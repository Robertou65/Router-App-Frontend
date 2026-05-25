# Feature Prompt — Edit Existing Route (Add More Stops)

You are an expert Android/Kotlin developer working on an existing
Jetpack Compose app. Implement the feature described below completely.
No placeholders. No TODOs. Do not modify any file not listed here.

---

## Feature Description

A user must be able to open any saved route from Route History,
scan additional package labels, and append the new stops to that
existing route. The route name and CSV filename are already set —
the user just scans and saves. Exporting is done separately from
Route Detail as it already works today.

---

## File 1 — `Routes.kt`

Add a new constant for the Camera route that accepts an optional
existing route id:

```kotlin
object Routes {
    const val RouteHistory = "route_history"
    const val Camera = "camera"
    const val CameraEdit = "camera_edit"   // ← ADD THIS
    const val SaveExport = "save_export"
    const val RouteDetail = "route_detail"
}
```

---

## File 2 — `RouterNavHost.kt`

### 2a — Add CameraEdit destination
Add a new `composable` entry for `Routes.CameraEdit` with a
mandatory `routeId` Long argument:

```
route = "${Routes.CameraEdit}/{routeId}"
arguments = listOf(navArgument("routeId") { type = NavType.LongType })
```

Inside this composable:
- Read `routeId` from `backStackEntry.arguments?.getLong("routeId") ?: 0L`
- Instantiate `CameraViewModel` with `viewModel()`
- Call `LaunchedEffect(routeId) { cameraViewModel.setExistingRoute(routeId) }`
- Render `CameraScreen` with:
  - `onFinish = { navController.navigate(Routes.SaveExport) }`
  - `onBack = { navController.popBackStack() }`

### 2b — Fix SaveExport to also work for CameraEdit
`SaveExportScreen` already gets `cameraViewModel` from the Camera
back stack entry. It must also handle the case where the user came
from `CameraEdit`. Update the SaveExport composable block to try
`CameraEdit` back stack entry first, then fall back to `Camera`:

```kotlin
composable(Routes.SaveExport) {
    val cameraEntry = remember(navController) {
        try {
            navController.getBackStackEntry("${Routes.CameraEdit}/{routeId}")
        } catch (e: IllegalArgumentException) {
            navController.getBackStackEntry(Routes.Camera)
        }
    }
    val cameraViewModel: CameraViewModel = viewModel(cameraEntry)
    SaveExportScreen(
        cameraViewModel = cameraViewModel,
        onSaveComplete = {
            navController.navigate(Routes.RouteHistory) {
                popUpTo(Routes.RouteHistory) { inclusive = true }
            }
        },
        onBack = { navController.popBackStack() },
    )
}
```

### 2c — Add navigation from RouteDetail
Add a lambda parameter to `RouteDetailScreen` call:
```kotlin
RouteDetailScreen(
    routeId = routeId,
    onBack = { navController.popBackStack() },
    onAddStops = { id ->
        navController.navigate("${Routes.CameraEdit}/$id")
    },
)
```

---

## File 3 — `CameraViewModel.kt`

### 3a — Add existingRouteId state
Add a private backing field and a public read-only StateFlow:

```kotlin
private val _existingRouteId = MutableStateFlow<Long?>(null)
val existingRouteId: StateFlow<Long?> = _existingRouteId
```

### 3b — Add setExistingRoute function
```kotlin
fun setExistingRoute(routeId: Long) {
    _existingRouteId.value = routeId
}
```

No other changes needed in this file. The session stop list,
scan state, and all existing logic remain identical.

---

## File 4 — `CameraScreen.kt`

No changes needed. `CameraScreen` and `CameraScreenContent` are
identical for both new-route and edit-route flows. The ViewModel
is set up externally in `RouterNavHost` before the composable runs.

---

## File 5 — `SaveExportScreen.kt`

`SaveExportScreen` must detect whether it is in **create mode**
(`existingRouteId == null`) or **edit mode** (`existingRouteId != null`)
and render accordingly.

### 5a — Read existingRouteId from ViewModel
At the top of `SaveExportScreen`:
```kotlin
val existingRouteId by cameraViewModel.existingRouteId.collectAsState()
```

### 5b — Edit mode UI
When `existingRouteId != null`, replace the full save form with a
simplified layout:

```
┌─────────────────────────────────────┐
│  Add Stops to Route                 │  ← title
│                                     │
│  New stops to add:                  │
│  1. Carrera 18B # 32 - 06 Sur...    │
│  2. Calle 10 # 43E - 31...          │
│     (scrollable list, read-only)    │
│                                     │
│  [ Save to Route ]                  │  ← single button
│  [ Back ]                           │
└─────────────────────────────────────┘
```

No route name field, no CSV filename field, no folder picker,
no Export button. Those are all available from Route Detail.

### 5c — Edit mode save logic
When `existingRouteId != null` and user taps **Save to Route**:

```kotlin
val db = AppDatabase.getInstance(context)
scope.launch {
    isSaving = true
    withContext(Dispatchers.IO) {
        // Get the current highest order for this route
        val existingStops = db.stopDao().getByRouteId(existingRouteId!!)
        val startOrder = existingStops.size + 1
        val stopsToInsert = stops.mapIndexed { index, stop ->
            stop.copy(
                routeId = existingRouteId!!,
                order = startOrder + index,
                id = 0L  // reset id so Room generates a real one
            )
        }
        stopsToInsert.forEach { db.stopDao().insert(it) }
    }
    isSaving = false
    onSaveComplete()
}
```

Note: `id = 0L` is required because session stops have negative
temporary IDs (e.g. -1L, -2L). Room must assign a real ID on insert,
so reset to 0L before inserting. `autoGenerate = true` on `Stop.id`
means Room treats 0L as "generate a new ID".

### 5d — Create mode (existing behavior)
When `existingRouteId == null`, render exactly the same UI and logic
as the current `SaveExportScreen`. Do not change create mode behavior.

---

## File 6 — `RouteDetailScreen.kt`

### 6a — Add onAddStops parameter
```kotlin
@Composable
fun RouteDetailScreen(
    routeId: Long,
    onBack: () -> Unit,
    onAddStops: (Long) -> Unit,   // ← ADD THIS
)
```

### 6b — Add "Add More Stops" button
Place it directly above the existing "Export CSV to Routin" button:

```kotlin
Button(
    onClick = { onAddStops(routeId) },
    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
) {
    Text(text = "Add More Stops")
}
```

The button is always enabled regardless of whether there are stops
already — the route exists and can always receive more.

---

## Delivery Checklist

- [ ] `Routes.CameraEdit` constant added
- [ ] `RouterNavHost` has `CameraEdit` composable with `routeId` argument
- [ ] `RouterNavHost` SaveExport tries `CameraEdit` back stack first
- [ ] `RouterNavHost` passes `onAddStops` to `RouteDetailScreen`
- [ ] `CameraViewModel._existingRouteId` StateFlow added
- [ ] `CameraViewModel.setExistingRoute(routeId)` function added
- [ ] `SaveExportScreen` reads `existingRouteId` from ViewModel
- [ ] Edit mode shows simplified UI (no name/CSV/folder fields)
- [ ] Edit mode appends stops with correct order offset
- [ ] Edit mode resets stop id to 0L before insert
- [ ] Create mode behavior unchanged
- [ ] `RouteDetailScreen` accepts `onAddStops` parameter
- [ ] "Add More Stops" button added above "Export CSV to Routin"
- [ ] Tapping "Add More Stops" navigates to `CameraEdit` with `routeId`
- [ ] After saving in edit mode, navigates back to Route History
