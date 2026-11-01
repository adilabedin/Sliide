package com.androidlearning.sliide.users.presentation.profile

import androidx.compose.runtime.Immutable
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.UiText

enum class ProfileMode {
    VIEW,
    EDIT,
    CREATE,
}

/** What the detail pane shows, encoded as a primitive so the pane navigator can save it (design D1). */
object ProfileKey {
    /** Temp ids count down from -1, so this can never be a user id. */
    const val CREATE: Long = Long.MIN_VALUE
}

/** The editable fields as typed (untrimmed). */
@Immutable
data class ProfileForm(
    val name: String = "",
    val email: String = "",
    val gender: Gender = Gender.MALE,
    val status: UserStatus = UserStatus.ACTIVE,
)

/** Read-only header details of the stored user. */
@Immutable
data class ProfileHeader(
    val userId: Long,
    val initials: String,
    val addedAgo: RelativeTime,
    val isPendingSync: Boolean,
)

@Immutable
data class ProfileState(
    val mode: ProfileMode = ProfileMode.VIEW,
    val header: ProfileHeader? = null,
    val form: ProfileForm = ProfileForm(),
    val nameError: UiText? = null,
    val emailError: UiText? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val showDeleteDialog: Boolean = false,
) {
    val isEditable: Boolean
        get() = mode != ProfileMode.VIEW && !isSaving
}
