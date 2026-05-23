# Feature Prompt — Route Deletion & Session Address Management

You are an expert Android/Kotlin developer working on an existing
Jetpack Compose app. Implement the two features below completely.
No placeholders. No TODOs. Do not modify any code outside the files
listed in each feature section.

---

## Feature 1 — Delete Route from History

### Affected files
- `RouteHistoryScreen.kt`
- `HistoryViewModel.kt`
- `RouteDao.kt` (if cascade delete is not already configured)

### Behavior
Each route card in the Route History screen has a delete icon button
(trailing icon, use `Icons.Default.Delete`).

Tapping it opens a confirmation dialog:
- Title: `Delete route?`
- Body: the route name (e.g. `"Route - Nov 12 - 09:30"`)
- Buttons: `Cancel` (dismiss) and `Delete` (confirm)

On confirm:
- Delete the `Route` row from Room by id
- Delete all `Stop` rows with matching `routeId` from Room
- The list updates reactively — no manual refresh needed

On cancel: dismiss the dialog, no changes.

### ViewModel method to add
```kotlin
fun deleteRoute(routeId: Long)
```
Runs on `viewModelScope` with `Dispatchers.IO`.
Calls `routeDao.deleteById(routeId)` and `stopDao.deleteByRouteId(routeId)`.

### DAO requirements
Verify that `RouteDao` has:
```kotlin
@Query("DELETE FROM route WHERE id = :routeId")
suspend fun deleteById(routeId: Long)
```
Verify that `StopDao` has:
```kotlin
@Query("DELETE FROM stop WHERE routeId = :routeId")
suspend fun deleteByRouteId(routeId: Long)
```
Add them if missing.

### UI rules
- The delete button must be visible at all times on each card.
  Do not use swipe-to-delete — it is not discoverable enough.
- The confirmation dialog must use `AlertDialog` composable.
- While the delete operation runs, disable the delete button on
  that specific card to prevent double taps.
- The dialog must be dismissed if the user navigates away.

---

## Feature 2 — Tappable Session Address Panel on Camera Screen

### Affected files
- `CameraScreen.kt`
- `CameraViewModel.kt`

### Overview
The scrollable address list at the top of the Camera Screen is currently
read-only. It becomes a tappable surface that opens a full-panel overlay
showing all addresses scanned in the current session. The user can select
one address and remove it using a bottom button. This panel sits entirely
within the Camera Screen — no new navigation destination.

### New ViewModel state to add
```kotlin
data class SessionPanelState(
    val isOpen: Boolean = false,
    val selectedStopId: Long? = null
)
```
Expose it as `StateFlow<SessionPanelState>` from `CameraViewModel`.

Add these methods to `CameraViewModel`:
```kotlin
fun openSessionPanel()
fun closeSessionPanel()
fun selectStop(stopId: Long)       // toggles: selecting same id deselects
fun removeSelectedStop()           // removes from session list in memory only
                                   // not from Room — route is not saved yet
```

### Panel trigger
The entire address list container at the top of the Camera Screen
is wrapped in a `clickable` modifier.
Add an expand icon (`Icons.Default.KeyboardArrowUp`) at the trailing
end of the container as a visual affordance.
Tapping anywhere on the container calls `viewModel.openSessionPanel()`.

### Session Address Panel layout
Rendered as a full-screen `Box` overlay on top of the camera preview,
with a semi-opaque dark background (`Color.Black.copy(alpha = 0.85f)`).

```
┌──────────────────────────────────┐
│  Session Addresses    [ ✕ close ]│  ← row: title + close IconButton
│──────────────────────────────────│
│                                  │
│  LazyColumn of stop cards:       │
│  ┌────────────────────────────┐  │
│  │ ○ / ● (selected indicator) │  │
│  │ Package #N                 │  │
│  │ full address string        │  │
│  └────────────────────────────┘  │
│                                  │
│  (fills remaining vertical space)│
│                                  │
│  ┌────────────────────────────┐  │
│  │   [ 🗑 Remove Selected ]   │  │  ← bottom, full width
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

### Stop card behavior
- Each card is `clickable`. Tapping it calls `viewModel.selectStop(stop.id)`.
- Selected card has a distinct background (`MaterialTheme.colorScheme.errorContainer`)
  and a filled radio button or checkmark indicator.
- Unselected cards have a transparent or subtle surface background.
- Only one card can be selected at a time.

### Remove Selected button behavior
- Disabled (grayed out) when `selectedStopId == null`.
- Enabled when any card is selected.
- Tapping it opens a confirmation dialog:
  - Title: `Remove address?`
  - Body: the full address string of the selected stop
  - Buttons: `Cancel` and `Remove`
- On confirm:
  - Call `viewModel.removeSelectedStop()`
  - This removes the stop from the in-memory session list only.
    The route has not been saved to Room yet at this stage,
    so no database operation is needed here.
  - Clear `selectedStopId` → set to null
  - Dismiss the dialog
  - Panel stays open so the user can remove more stops if needed
- On cancel: dismiss dialog, no changes.

### Auto-close rule
If the session stop list becomes empty after a removal,
call `viewModel.closeSessionPanel()` automatically.

### Close button behavior
The `[ ✕ ]` icon button at the top right calls `viewModel.closeSessionPanel()`.
No confirmation needed — closing the panel does not remove anything.

### Panel open/close rules
- The panel must NOT open when `ScanState` is `Scanning` or `Requesting`.
  The address list container must be non-clickable during those states.
- The panel must NOT open when the session stop list is empty.
  If the list is empty, the expand icon must be hidden.
- Pressing the system back button while the panel is open must close
  the panel instead of navigating away from the Camera Screen.
  Implement this using `BackHandler`.

### Scan and Finish button behavior while panel is open
- Both `[ Scan ]` and `[ Finish ]` buttons must be hidden while the
  panel is open. They reappear when the panel is closed.

---

## Delivery Checklist

- [ ] `RouteDao.deleteById` exists and works
- [ ] `StopDao.deleteByRouteId` exists and works
- [ ] `HistoryViewModel.deleteRoute(routeId)` implemented
- [ ] Delete button visible on each route card in Route History
- [ ] Confirmation dialog shown before delete executes
- [ ] Route + all its stops deleted from Room on confirm
- [ ] List updates reactively after deletion
- [ ] `SessionPanelState` added to `CameraViewModel`
- [ ] Address list container is tappable (expand icon visible when list non-empty)
- [ ] Panel not openable during Scanning / Requesting states
- [ ] Panel not openable when session list is empty
- [ ] Stop cards are individually selectable (single selection)
- [ ] Remove Selected button disabled when nothing selected
- [ ] Confirmation dialog shown before removal
- [ ] Stop removed from in-memory session list on confirm (no Room call)
- [ ] Panel stays open after removal unless list becomes empty
- [ ] Panel auto-closes when session list becomes empty
- [ ] Back button closes panel instead of leaving the screen
- [ ] Scan and Finish buttons hidden while panel is open
