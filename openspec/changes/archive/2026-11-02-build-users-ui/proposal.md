# Proposal

## Why

The data layer is done: an offline-first repository, a pending-sync outbox and the Ktor client. The UI, however, is
still a placeholder `ListItem` list that never refreshes. The challenge is judged mainly on UX polish, so this change
builds the full user-facing experience on top of that repository. It adds a premium feed, a reusable profile page
for viewing, editing and creating users, delete with a 5-second Undo, and an adaptive layout for phones and tablets.
The Room cache stays the single source of truth for everything shown on screen.

## What Changes

- **Premium visual language.** The colour palette is refined to deep ink neutrals with a single restrained accent and a
  refined destructive red, in both light and dark themes. Shared design-system components are added in
  `:core:designsystem`: a user card surface, a shimmer modifier and placeholder card, a segmented two-option toggle,
  the add-user FAB, an Undo snackbar with a countdown, and a destructive button. Every screen uses these components so
  the app looks the same throughout.
- **Smart user feed.** The users screen shows the cache in a lazy list of clean cards. Each card has an initials avatar,
  the user's name, email, and a relative "Added 5 minutes ago" label computed in shared code. The label updates every
  minute. Users created offline that are not yet synced show a subtle "Pending sync" chip. List changes animate.
- **Loading and errors.** The feed refreshes on launch and on pull-to-refresh. While the cache is empty and the first
  load is running, 20 shimmering placeholder cards are shown, built lazily. A refresh failure shows a full-screen error
  with Retry when there is nothing cached, or a slim inline banner such as "No internet" above the cached list.
- **Reusable profile page** with three modes: **View**, **Edit** and **Create**.
  - The app bar shows "Profile" at the top left.
  - In View mode the top right holds an Edit icon. In Edit and Create modes this becomes a **Save** text button, which
    is disabled while the form is invalid or a save is running.
  - The name and email fields validate in real time. Gender is a clean two-option toggle (Male / Female), and so is
    Status (Active / Inactive).
  - A red **Delete** button is pinned to the bottom of the page in View and Edit modes.
- **Add user.** A stylish FAB at the bottom right of the users screen opens the profile page in Create mode. After a
  successful save the pane closes and the list scrolls to the top, where the new user appears.
- **Delete with Undo.** Long-pressing a card, or pressing the profile's Delete button, opens a confirmation dialog: "You
  will have 5 seconds to undo." Confirming removes the user locally. A styled snackbar in the users screen's
  `SnackbarHost` then shows an **Undo** action and a 5-second countdown. Undo restores the user. When the countdown
  ends, the delete is finalised. The countdown is owned by an object that outlives the screen, so the delete is always
  either finalised or undone, even if the screen goes away.
- **Adaptive layout.** On compact widths, the list is a single column and the profile opens full screen with a back
  arrow. On medium widths, such as a landscape phone, the list is a two-column grid. On expanded widths, such as
  tablets, the list and the profile are shown side by side as master-detail.
- `UsersAction`, `UsersState` and `UsersViewModel` grow to cover refresh, errors, the delete and Undo flow, and the
  selection. A new `ProfileViewModel` drives the profile page.

## Capabilities

### New Capabilities

- `user-feed`: the users list, covering cards, the relative "added" time, the pending-sync chip, the shimmer
  placeholders, refresh, pull-to-refresh and the error states.
- `user-profile`: the reusable View, Edit and Create profile page, covering its app bar, form, real-time validation,
  gender and status toggles, save outcomes, the add-user FAB entry point, and what happens when the user disappears.
- `user-deletion`: long-press and profile delete confirmation, local removal, the 5-second Undo snackbar, and the
  "always exactly one of undo or finalise" guarantee.
- `adaptive-layout`: how compact, medium and expanded window sizes change the structure of the feed and profile.

### Modified Capabilities

- `design-system`: adds requirements for the premium palette, its contrast, and the shared components every screen
  reuses. This capability was introduced by the in-flight `setup-module-structure` change, so the delta only adds
  requirements.

## Impact

- **`:core:designsystem`**
  - Updates the colour tokens in `Color.kt`.
  - Adds new component and layout packages: card, shimmer, segmented toggle, FAB, undo snackbar, destructive button,
    and a `DeviceConfiguration` helper.
  - Adds the dependencies `material-icons-core` and `material3-adaptive`.
- **`:users:domain`**
  - Adds `UserDetailsValidator`, a pure shared validation function with typed errors.
  - Adds `User.isLocalOnly`, which is true for a temporary id.
  - The repository interface does not change.
- **`:users:presentation`**
  - Rewrites `UsersScreen`, `UsersState`, `UsersAction` and `UsersViewModel`.
  - Adds the profile package (`ProfileViewModel`, `ProfileState`, `ProfileAction`, screen), a relative-time formatter,
    `DeleteUndoController` (a Koin single with its own long-lived scope), the list-detail root, and new strings and
    plurals.
  - Adds the `adaptive-layout` and `adaptive-navigation` dependencies.
- **`:composeApp`**: `App()` hosts the new list-detail root. `KoinGraphTest` still has to resolve the graph, including
  the new ViewModels and the controller.
- **`:androidApp`**: `AppLaunchSmokeTest` is updated, because the empty-state copy and the title change.
- **Tests**: new and updated unit tests for both ViewModels, the validator, the relative-time formatter and the Undo
  controller. New instrumented Compose UI tests for the feed and profile live in `:users:presentation`.
- **Docs**: `README.md` is updated with the UI architecture and design choices.
- **No changes** to `:users:data`, `:core:database` or the network layer.
