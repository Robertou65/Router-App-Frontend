# Feature Prompt — Manual Address Input on Camera Screen

You are an expert Android/Kotlin developer working on an existing
Jetpack Compose app. Implement the feature described below completely.
No placeholders. No TODOs. Do not modify any file not listed here.

---

## Feature Description

When a package label is smudged or unreadable, the user needs to type
the delivery address manually instead of scanning it. The manually
typed address goes through the same Geocoding API validation and is
added to the session stop list exactly like a scanned address.
The OCR parser is skipped entirely for manual input.

---

## File 1 — `CameraViewModel.kt`

### Add `onManualAddress` function

```kotlin
fun onManualAddress(address: String) {
    if (_scanState.value !is ScanState.Idle) return
    if (address.isBlank()) return

    _scanState.value = ScanState.Requesting

    viewModelScope.launch {
        when (val result = geocodingRepository.geocodeAddress(address)) {
            is GeocodingResult.Success -> {
                val stop = Stop(
                    id = -(_sessionStops.value.size + 1L),
                    routeId = 0L,
                    label = "Package #${_sessionStops.value.size + 1}",
                    rawOcrText = address,
                    address = address,
                    lat = result.lat,
                    lng = result.lng,
                    order = _sessionStops.value.size + 1,
                )
                _sessionStops.value = _sessionStops.value + stop
                succeedWith(stop)
            }
            is GeocodingResult.Error -> {
                val reason = when (result.type) {
                    GeocodingResult.ErrorType.AddressNotFound -> "Address not found"
                    GeocodingResult.ErrorType.ApiKeyError -> "API key error"
                    GeocodingResult.ErrorType.ConnectionError -> "Connection error"
                }
                failWith(reason)
            }
        }
    }
}
```

### Key differences from `onOcrResult`
- No `scanInProgress` AtomicBoolean involved — manual entry is not
  triggered by the camera analyzer.
- No call to `addressParser.parse()` — the user typed the address
  directly, so it goes straight to `geocodingRepository.geocodeAddress()`.
- `rawOcrText` is set to the typed address string (it documents
  what the user entered for traceability).
- State transitions are identical: `Idle → Requesting → Success/Failure`
  using the existing `succeedWith()` and `failWith()` private functions.

---

## File 2 — `CameraScreen.kt`

### 2a — Add dialog state variable in `CameraScreenContent`

Inside `CameraScreenContent`, alongside the existing
`var stopPendingRemoval by remember { mutableStateOf<Stop?>(null) }`,
add:

```kotlin
var showManualInput by remember { mutableStateOf(false) }
var manualAddressText by remember { mutableStateOf("") }
```

### 2b — Add "Type Address" button in the button column

In the bottom button column, between the Scan button and the Finish
button, add:

```kotlin
Spacer(modifier = Modifier.height(12.dp))
Button(
    onClick = {
        manualAddressText = ""
        showManualInput = true
    },
    enabled = scanState is CameraViewModel.ScanState.Idle,
) {
    Text(text = "Type Address")
}
```

The full button column must look like this after the change:

```kotlin
Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Bottom,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Button(
        onClick = { viewModel.requestScan() },
        enabled = scanState is CameraViewModel.ScanState.Idle,
    ) {
        Text(text = "Scan")
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = {
            manualAddressText = ""
            showManualInput = true
        },
        enabled = scanState is CameraViewModel.ScanState.Idle,
    ) {
        Text(text = "Type Address")
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onFinish,
        enabled = sessionStops.isNotEmpty() && !isBusy,
    ) {
        Text(text = "Finish")
    }
}
```

### 2c — Add manual address input dialog

Place this dialog block directly after the `stopPendingRemoval` dialog,
still inside `CameraScreenContent` but outside the main `Box`:

```kotlin
if (showManualInput) {
    AlertDialog(
        onDismissRequest = { showManualInput = false },
        title = { Text(text = "Enter address manually") },
        text = {
            OutlinedTextField(
                value = manualAddressText,
                onValueChange = { manualAddressText = it },
                label = { Text(text = "Address") },
                placeholder = { Text(text = "e.g. Carrera 18b # 32-06 Sur, Bogotá") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    showManualInput = false
                    viewModel.onManualAddress(manualAddressText.trim())
                    manualAddressText = ""
                },
                enabled = manualAddressText.isNotBlank(),
            ) {
                Text(text = "Search")
            }
        },
        dismissButton = {
            Button(onClick = { showManualInput = false }) {
                Text(text = "Cancel")
            }
        },
    )
}
```

### 2d — Add missing import

Add this import at the top of `CameraScreen.kt` if not already present:

```kotlin
import androidx.compose.material3.OutlinedTextField
```

---

## Behavior spec

### Happy path
1. User taps "Type Address"
2. Dialog opens with empty text field
3. User types: `Carrera 18b # 32-06 Sur, Quiroga, Bogotá`
4. "Search" button becomes enabled
5. User taps "Search"
6. Dialog closes immediately
7. Camera overlay shows loading state ("Reading address...")
8. Geocoding API is called with the typed string
9. API returns valid coordinates
10. Camera overlay turns green ("Address added")
11. Address appears in the session list at the top
12. Overlay resets to neutral after 1.5s

### Failure path
1. Same as above through step 8
2. API returns zero results or error
3. Camera overlay turns red with error reason
4. Overlay resets to neutral after 1.5s
5. User can try again — either type a corrected address or scan

### Disabled states
- "Type Address" button is disabled during `Scanning`, `Requesting`,
  `Success`, and `Failure` states — same rule as the Scan button.
- "Type Address" button is hidden when the session panel is open —
  the panel already hides both Scan and Finish via the `if/else` branch
  in `CameraScreenContent`, so this is already handled correctly.
- "Search" button inside the dialog is disabled when the text field
  is blank or contains only whitespace.

### Dialog dismissal rules
- Tapping Cancel closes the dialog with no state changes.
- Tapping outside the dialog (onDismissRequest) also closes it
  with no state changes.
- `manualAddressText` is reset to empty string every time the dialog
  opens, so previous input never persists between sessions.

---

## Delivery Checklist

- [ ] `CameraViewModel.onManualAddress(address: String)` added
- [ ] Manual flow skips `scanInProgress` and `addressParser`
- [ ] Manual flow uses same `succeedWith` / `failWith` as OCR flow
- [ ] `rawOcrText` set to typed address string on manual stops
- [ ] `showManualInput` and `manualAddressText` state vars added
- [ ] "Type Address" button added between Scan and Finish
- [ ] "Type Address" button disabled when not in Idle state
- [ ] `AlertDialog` with `OutlinedTextField` renders correctly
- [ ] "Search" button disabled when text field is blank
- [ ] Dialog closes before `onManualAddress` is called
- [ ] `manualAddressText` cleared on dialog open and after search
- [ ] `OutlinedTextField` import added if missing
- [ ] No changes to any other file
