# Bug Fix — App Crashes When Opening Session Address Panel

## File: `app/src/main/java/com/example/router_app/ui/camera/CameraViewModel.kt`
## File: `app/src/main/java/com/example/router_app/ui/screens/CameraScreen.kt`

---

## Crash Description

The app crashes with `IllegalArgumentException` when the user taps the
session address box on the Camera Screen after scanning 2 or more packages.

```
IllegalArgumentException: Key 0 was already used.
If you are using LazyColumn/Row please make sure you
provide a unique key for each item.
```

---

## Root Cause

In `CameraViewModel.kt`, session stops are created in-memory before
being saved to Room. Room's `autoGenerate` only assigns a real ID on
database insert, so every in-memory stop is created with the default
`id = 0L`:

```kotlin
// Every in-memory stop ends up with id = 0
val stop = Stop(
    routeId = 0L,
    label = "Package #${_sessionStops.value.size + 1}",
    rawOcrText = text,
    address = parsed.address,
    lat = result.lat,
    lng = result.lng,
    order = _sessionStops.value.size + 1,
)
```

In `CameraScreen.kt`, `SessionAddressPanel` renders a `LazyColumn`
using `stop.id` as the item key:

```kotlin
itemsIndexed(sessionStops, key = { _, item -> item.id }) { _, stop ->
```

With 2 or more stops all having `id = 0`, Compose throws the
`IllegalArgumentException` the moment the panel opens and the
`LazyColumn` tries to assign keys.

---

## Fix 1 — `CameraViewModel.kt`

Locate the `Stop` creation block inside `onOcrResult()`.
Add `id = -(_sessionStops.value.size + 1L)` as the first field:

```kotlin
// BEFORE
val stop = Stop(
    routeId = 0L,
    label = "Package #${_sessionStops.value.size + 1}",
    rawOcrText = text,
    address = parsed.address,
    lat = result.lat,
    lng = result.lng,
    order = _sessionStops.value.size + 1,
)

// AFTER
val stop = Stop(
    id = -(_sessionStops.value.size + 1L),
    routeId = 0L,
    label = "Package #${_sessionStops.value.size + 1}",
    rawOcrText = text,
    address = parsed.address,
    lat = result.lat,
    lng = result.lng,
    order = _sessionStops.value.size + 1,
)
```

### Why negative IDs are safe here
Room's `autoGenerate` always produces positive Long values starting
from 1. Negative IDs are used only as temporary session identifiers
and are never written to the database at this stage.

`SaveExportScreen.kt` already handles the transition correctly — it
calls `stop.copy(routeId = routeId)` before inserting, which triggers
Room to assign a real auto-generated ID on insert, discarding the
temporary negative value.

No changes needed in `SaveExportScreen.kt`.

---

## Fix 2 — `CameraScreen.kt`

Locate the `LazyColumn` inside `SessionAddressPanel`.
Change the `key` from `item.id` to `item.order`:

```kotlin
// BEFORE
itemsIndexed(sessionStops, key = { _, item -> item.id }) { _, stop ->

// AFTER
itemsIndexed(sessionStops, key = { _, item -> item.order }) { _, stop ->
```

### Why use `order` as key
`order` is assigned as `_sessionStops.value.size + 1` at creation time,
making it unique and stable within the session (1, 2, 3...).
This acts as a safety net independent of the ID strategy, so the
`LazyColumn` remains correct even if ID logic changes in the future.

---

## Do Not Touch

Do not modify any other file. Do not change `SaveExportScreen.kt`,
`Stop.kt`, `StopDao.kt`, or any DAO. The fix is isolated to these
two lines across two files.

---

## Verification

After applying both fixes, confirm the following manually:

1. Scan 1 package → open panel → no crash, 1 card visible
2. Scan 3 packages → open panel → no crash, 3 cards visible
3. Select a card → only that card highlights
4. Remove a card → remaining cards reorder correctly, panel stays open
5. Scan again after removal → new stop gets a correct unique negative ID
6. Tap Finish → Save Route → route and all stops saved correctly to Room
