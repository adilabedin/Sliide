package com.androidlearning.sliide.users.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.domain.validation.EmailError
import com.androidlearning.sliide.users.domain.validation.NameError
import com.androidlearning.sliide.users.domain.validation.UserDetailsValidator
import com.androidlearning.sliide.users.presentation.delete.DeleteUndoController
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_no_longer_available
import com.androidlearning.sliide.users.presentation.generated.resources.profile_saved_offline
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_in_use
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_invalid
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_required
import com.androidlearning.sliide.users.presentation.generated.resources.validation_name_required
import com.androidlearning.sliide.users.presentation.generated.resources.validation_name_too_short
import com.androidlearning.sliide.users.presentation.users.initialsOf
import com.androidlearning.sliide.users.presentation.util.UiText
import com.androidlearning.sliide.users.presentation.util.relativeAddedTime
import com.androidlearning.sliide.users.presentation.util.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The reusable profile (design D2): one ViewModel for the detail pane, retargeted with [ProfileAction.OnOpen].
 * View shows the cached user, Edit and Create share the same validated form. The target, mode and draft live in
 * [SavedStateHandle] so process death restores an open profile, including an unsaved edit.
 */
class ProfileViewModel(
    private val userRepository: UserRepository,
    private val deleteUndoController: DeleteUndoController,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    private var openKey: Long? = null
    private var original: User? = null
    private var observeJob: Job? = null
    private var deletingOwnUser = false

    /** Set after a server VALIDATION rejection; cleared as soon as the email changes. */
    private var serverEmailError: UiText? = null

    private var nameTouched: Boolean
        get() = savedStateHandle[KEY_NAME_TOUCHED] ?: false
        set(value) {
            savedStateHandle[KEY_NAME_TOUCHED] = value
        }

    private var emailTouched: Boolean
        get() = savedStateHandle[KEY_EMAIL_TOUCHED] ?: false
        set(value) {
            savedStateHandle[KEY_EMAIL_TOUCHED] = value
        }

    init {
        savedStateHandle.get<Long>(KEY_TARGET)?.let { key ->
            val mode = savedStateHandle.get<String>(KEY_MODE)?.let(ProfileMode::valueOf) ?: ProfileMode.VIEW
            open(key, restoredMode = mode, restoredForm = restoredForm())
        }
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.OnOpen -> if (action.key != openKey) open(action.key, restoredMode = null, restoredForm = null)
            ProfileAction.OnDismiss -> dismiss()
            ProfileAction.OnEditClick -> if (_state.value.mode == ProfileMode.VIEW && original != null) setMode(ProfileMode.EDIT)
            ProfileAction.OnCancelEdit -> cancelEdit()
            is ProfileAction.OnNameChange -> editForm(nameChanged = true) { it.copy(name = action.name) }
            is ProfileAction.OnEmailChange -> {
                serverEmailError = null
                editForm(emailChanged = true) { it.copy(email = action.email) }
            }
            is ProfileAction.OnGenderChange -> editForm { it.copy(gender = action.gender) }
            is ProfileAction.OnStatusChange -> editForm { it.copy(status = action.status) }
            ProfileAction.OnSaveClick -> save()
            ProfileAction.OnDeleteClick -> if (original != null) _state.value = _state.value.copy(showDeleteDialog = true)
            ProfileAction.OnDeleteCancel -> _state.value = _state.value.copy(showDeleteDialog = false)
            ProfileAction.OnDeleteConfirm -> confirmDelete()
        }
    }

    private fun open(
        key: Long,
        restoredMode: ProfileMode?,
        restoredForm: ProfileForm?,
    ) {
        observeJob?.cancel()
        openKey = key
        original = null
        deletingOwnUser = false
        serverEmailError = null
        savedStateHandle[KEY_TARGET] = key

        if (key == ProfileKey.CREATE) {
            if (restoredMode == null) {
                nameTouched = false
                emailTouched = false
            }
            setState(mode = ProfileMode.CREATE, header = null, form = restoredForm ?: ProfileForm())
            return
        }

        if (restoredMode == null) {
            nameTouched = false
            emailTouched = false
        }
        val draft = restoredForm.takeIf { restoredMode == ProfileMode.EDIT }
        setState(mode = restoredMode ?: ProfileMode.VIEW, header = null, form = draft ?: ProfileForm())
        observeJob =
            userRepository
                .observeUsers()
                .map { users -> users.firstOrNull { it.id == key } }
                .distinctUntilChanged()
                .onEach { user -> onStoredUser(user) }
                .launchIn(viewModelScope)
    }

    private fun onStoredUser(user: User?) {
        val previous = original
        if (user == null) {
            observeJob?.cancel()
            val message =
                when {
                    deletingOwnUser -> null
                    previous?.isLocalOnly == true -> null // Re-keyed after sync, or its offline create was rejected.
                    else -> UiText.Resource(Res.string.profile_no_longer_available)
                }
            clearTarget()
            send(ProfileEvent.Close(message))
            return
        }
        original = user
        val current = _state.value
        // While editing (including an edit restored after process death) the draft wins over cache updates.
        val form = if (current.mode == ProfileMode.EDIT) current.form else user.toForm()
        setState(mode = current.mode, header = user.toHeader(), form = form)
    }

    private fun dismiss() {
        observeJob?.cancel()
        original = null
        deletingOwnUser = false
        serverEmailError = null
        nameTouched = false
        emailTouched = false
        clearTarget()
        _state.value = ProfileState()
    }

    private fun cancelEdit() {
        val user = original ?: return
        if (_state.value.mode != ProfileMode.EDIT) return
        nameTouched = false
        emailTouched = false
        serverEmailError = null
        setState(mode = ProfileMode.VIEW, header = _state.value.header, form = user.toForm())
    }

    private fun setMode(mode: ProfileMode) {
        setState(mode = mode, header = _state.value.header, form = _state.value.form)
    }

    private fun editForm(
        nameChanged: Boolean = false,
        emailChanged: Boolean = false,
        transform: (ProfileForm) -> ProfileForm,
    ) {
        val current = _state.value
        if (!current.isEditable) return
        if (nameChanged) nameTouched = true
        if (emailChanged) emailTouched = true
        setState(mode = current.mode, header = current.header, form = transform(current.form))
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        val form = current.form
        val details =
            UserDetails(name = form.name.trim(), email = form.email.trim(), gender = form.gender, status = form.status)
        _state.value = current.copy(isSaving = true, canSave = false)
        viewModelScope.launch {
            when (current.mode) {
                ProfileMode.CREATE -> onCreateResult(userRepository.createUser(details))
                ProfileMode.EDIT -> {
                    val user = original ?: return@launch
                    val updated =
                        user.copy(name = details.name, email = details.email, gender = details.gender, status = details.status)
                    onEditResult(userRepository.updateUser(updated), updated)
                }
                ProfileMode.VIEW -> Unit
            }
        }
    }

    private fun onCreateResult(result: Result<WriteStatus, DataError>) {
        when (result) {
            is Result.Success -> {
                clearTarget()
                val message = if (result.data == WriteStatus.QUEUED) UiText.Resource(Res.string.profile_saved_offline) else null
                send(ProfileEvent.Created(message))
            }
            is Result.Failure -> onSaveFailure(result.error)
        }
    }

    private fun onEditResult(
        result: Result<WriteStatus, DataError>,
        updated: User,
    ) {
        when (result) {
            is Result.Success -> {
                original = updated
                nameTouched = false
                emailTouched = false
                setState(mode = ProfileMode.VIEW, header = _state.value.header, form = updated.toForm(), isSaving = false)
                if (result.data == WriteStatus.QUEUED) {
                    send(ProfileEvent.ShowMessage(UiText.Resource(Res.string.profile_saved_offline)))
                }
            }
            is Result.Failure -> onSaveFailure(result.error)
        }
    }

    /** A rejected save keeps the input; a server validation rejection is shown under the email field. */
    private fun onSaveFailure(error: DataError) {
        val current = _state.value
        if (error == DataError.Remote.VALIDATION) {
            serverEmailError = UiText.Resource(Res.string.validation_email_in_use)
            emailTouched = true
        } else {
            send(ProfileEvent.ShowMessage(error.toUiText()))
        }
        setState(mode = current.mode, header = current.header, form = current.form, isSaving = false)
    }

    private fun confirmDelete() {
        val user = original ?: return
        deletingOwnUser = true
        _state.value = _state.value.copy(showDeleteDialog = false)
        observeJob?.cancel()
        clearTarget()
        deleteUndoController.request(user.id)
        send(ProfileEvent.Close())
    }

    /** Rebuilds the state from [mode], [form] and the stored user, re-deriving validation and [ProfileState.canSave]. */
    private fun setState(
        mode: ProfileMode,
        header: ProfileHeader?,
        form: ProfileForm,
        isSaving: Boolean = _state.value.isSaving,
    ) {
        val nameError = UserDetailsValidator.validateName(form.name)
        val emailError = UserDetailsValidator.validateEmail(form.email)
        val showErrors = mode == ProfileMode.EDIT
        val changed = original?.toForm()?.trimmed() != form.trimmed()
        _state.value =
            ProfileState(
                mode = mode,
                header = header,
                form = form,
                nameError = nameError?.takeIf { showErrors || nameTouched }?.toUiText(),
                emailError =
                    serverEmailError ?: emailError?.takeIf { showErrors || emailTouched }?.toUiText(),
                isSaving = isSaving,
                canSave =
                    !isSaving &&
                        nameError == null &&
                        emailError == null &&
                        serverEmailError == null &&
                        when (mode) {
                            ProfileMode.CREATE -> true
                            ProfileMode.EDIT -> changed
                            ProfileMode.VIEW -> false
                        },
                showDeleteDialog = _state.value.showDeleteDialog,
            )
        savedStateHandle[KEY_MODE] = mode.name
        savedStateHandle[KEY_NAME] = form.name
        savedStateHandle[KEY_EMAIL] = form.email
        savedStateHandle[KEY_GENDER] = form.gender.name
        savedStateHandle[KEY_STATUS] = form.status.name
    }

    private fun clearTarget() {
        openKey = null
        savedStateHandle.remove<Long>(KEY_TARGET)
    }

    private fun restoredForm(): ProfileForm? {
        val name = savedStateHandle.get<String>(KEY_NAME) ?: return null
        return ProfileForm(
            name = name,
            email = savedStateHandle.get<String>(KEY_EMAIL).orEmpty(),
            gender = savedStateHandle.get<String>(KEY_GENDER)?.let(Gender::valueOf) ?: Gender.MALE,
            status = savedStateHandle.get<String>(KEY_STATUS)?.let(UserStatus::valueOf) ?: UserStatus.ACTIVE,
        )
    }

    private fun send(event: ProfileEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun User.toForm() = ProfileForm(name = name, email = email, gender = gender, status = status)

    private fun User.toHeader() =
        ProfileHeader(
            userId = id,
            initials = initialsOf(name),
            addedAgo = relativeAddedTime(createdAt = createdAt, now = clock.now()),
            isPendingSync = isLocalOnly,
        )

    private fun ProfileForm.trimmed() = copy(name = name.trim(), email = email.trim())

    private fun NameError.toUiText(): UiText =
        UiText.Resource(
            when (this) {
                NameError.REQUIRED -> Res.string.validation_name_required
                NameError.TOO_SHORT -> Res.string.validation_name_too_short
            },
        )

    private fun EmailError.toUiText(): UiText =
        UiText.Resource(
            when (this) {
                EmailError.REQUIRED -> Res.string.validation_email_required
                EmailError.INVALID -> Res.string.validation_email_invalid
            },
        )

    private companion object {
        const val KEY_TARGET = "profile_target"
        const val KEY_MODE = "profile_mode"
        const val KEY_NAME = "profile_name"
        const val KEY_EMAIL = "profile_email"
        const val KEY_GENDER = "profile_gender"
        const val KEY_STATUS = "profile_status"
        const val KEY_NAME_TOUCHED = "profile_name_touched"
        const val KEY_EMAIL_TOUCHED = "profile_email_touched"
    }
}
