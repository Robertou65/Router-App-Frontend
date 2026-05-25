# Bug Fix — App Crashes When Pressing Save Route

## File: `app/src/main/java/com/example/router_app/ui/screens/SaveExportScreen.kt`

---

## Crash Description

The app crashes with a SQLite UNIQUE constraint violation when the
user presses "Save Route" on the Save & Export screen.

```
android.database.sqlite.SQLiteConstraintException:
UNIQUE constraint failed: stops.id
```

The crash is guaranteed on the second route saved in the same app
session, and can also occur on the first save if the user removed
any stops from the session panel before finishing.

---

## Root Cause

Session stops are created in `CameraViewModel` with negative temporary
IDs (`-1L`, `-2L`, `-3L`, etc.) to avoid LazyColumn key collisions.

In `SaveExportScreen.kt`, the create mode save logic copies stops
without resetting the ID:

```kotlin
// WRONG — preserves negative temporary IDs
val stopsToInsert = stops.mapIndexed { index, stop ->
    stop.copy(routeId = routeId, order = index + 1)
}
```

Room's `autoGenerate = true` only generates a new ID when the value
is `0L`. For any non-zero value — including negative values like
`-1L` — SQLite uses the provided value as the literal primary key.

So the first route saves stops with `id = -1, -2, -3` in the database.
When the second route is saved, it tries to insert stops with those
same IDs again, causing a UNIQUE constraint violation and crashing.

---

## Fix

### Location
`SaveExportScreen.kt`, inside the `else` branch (create mode),
inside the `Save Route` button `onClick` lambda, inside
`withContext(Dispatchers.IO)`.

### Change

```kotlin
// BEFORE
val stopsToInsert = stops.mapIndexed { index, stop ->
    stop.copy(routeId = routeId, order = index + 1)
}

// AFTER — add id = 0L to force Room to auto-generate a real ID
val stopsToInsert = stops.mapIndexed { index, stop ->
    stop.copy(routeId = routeId, order = index + 1, id = 0L)
}
```

### Why `id = 0L` is safe
`Stop.id` is declared as `@PrimaryKey(autoGenerate = true) val id: Long = 0`.
Room treats `0L` as the sentinel value meaning "auto-generate on insert".
The resulting stop in the database will have a positive unique ID
assigned by SQLite's rowid mechanism. The temporary negative session
ID is discarded and never stored.

---

## Verification

After the fix, confirm:
1. Scan 2 packages → Finish → Save Route → navigates to Route History ✅
2. Open the saved route → both stops are visible with correct addresses ✅
3. Scan 2 more packages → Finish → Save Route → second route saves without crash ✅
4. Both routes visible in Route History with correct stop counts ✅
5. Remove a stop in the session panel, then save → no crash ✅

---

## Note on the Edit Mode (already correct)

The edit mode save logic in the same file already does this correctly:
```kotlin
stop.copy(
    routeId = existingRouteId!!,
    order = startOrder + index,
    id = 0L   // ← already present
)
```
Do not change the edit mode block. Only fix the create mode block.
