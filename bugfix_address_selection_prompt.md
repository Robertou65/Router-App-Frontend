# Bug Fix — Session Address Panel: All Addresses Selected Instead of One

## Problem
In the Session Address Panel on the Camera Screen, tapping any address
card causes ALL cards to appear selected instead of only the tapped one.

## Root cause
Two likely causes — fix both regardless of which one is active:

### Cause A — Wrong selection condition
In the stop card composable, the selected state is being evaluated as:
```kotlin
// WRONG — true for every card as long as anything is selected
val isSelected = viewModel.sessionPanelState.selectedStopId != null

// CORRECT — true only for the specific card matching the selected id
val isSelected = sessionPanelState.selectedStopId == stop.id
```

### Cause B — Missing key in LazyColumn
Without a stable key, Compose reuses card slots during recomposition
and the selection highlight bleeds across all items.

```kotlin
// WRONG
LazyColumn {
    items(stops) { stop ->
        StopCard(stop, isSelected = ...)
    }
}

// CORRECT
LazyColumn {
    items(stops, key = { it.id }) { stop ->
        StopCard(stop, isSelected = sessionPanelState.selectedStopId == stop.id)
    }
}
```

## Fix

### File: CameraScreen.kt

1. Locate the `LazyColumn` inside the Session Address Panel.

2. Add `key = { it.id }` to the `items(...)` call:
```kotlin
items(sessionStops, key = { it.id }) { stop ->
```

3. Locate the `isSelected` evaluation inside the stop card composable
   or wherever the card background/indicator is determined.
   Replace any of these incorrect patterns:
```kotlin
// Any of these are wrong:
val isSelected = sessionPanelState.selectedStopId != null
val isSelected = selectedStopId != null
val isSelected = viewModel.selectedStopId.value != null
```
With this exact check:
```kotlin
val isSelected = sessionPanelState.selectedStopId == stop.id
```

4. Verify the selected visual state (background color, radio button,
   checkmark) is driven exclusively by `isSelected` for each individual
   card instance.

### File: CameraViewModel.kt

Verify `selectStop` toggles correctly:
```kotlin
fun selectStop(stopId: Long) {
    _sessionPanelState.update { current ->
        current.copy(
            selectedStopId = if (current.selectedStopId == stopId) null else stopId
        )
    }
}
```
The toggle behavior (tapping selected card deselects it) must use
`== stopId` comparison, not a boolean flag.

## Verification
After the fix:
- Tapping card A → only card A highlighted, all others normal
- Tapping card A again → card A deselected, none highlighted
- Tapping card A then card B → only card B highlighted
- Remove Selected button disabled when none selected
- Remove Selected button enabled only when exactly one card is selected
