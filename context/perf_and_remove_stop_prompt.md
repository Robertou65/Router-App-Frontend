# Combined Prompt — Performance Improvements & Remove Stop from Route Detail

You are an expert Android/Kotlin developer working on an existing
Jetpack Compose app. Implement all changes below completely.
No placeholders. No TODOs. Do not modify any file not listed here.

---

## Part 1 — Performance Improvements

### 1A — Geocoding Result Cache (`GeocodingRepository.kt`)

**Problem:** Every scan calls the Geocoding API even if the same
address was already geocoded in a previous scan or session.
In a delivery scenario, multiple packages often go to the same
neighborhood and labels sometimes repeat.

**Fix:** Add an in-memory LRU cache inside `GeocodingRepository`.
Use `LinkedHashMap` with access-order to implement LRU behavior.
Cache size: 50 entries. Key: the normalized address string (trimmed,
lowercased). Value: `GeocodingResult.Success`.

Only cache successful results. Errors are never cached — a failed
geocode should always retry on the next scan.

```kotlin
private val cache = object : LinkedHashMap<String, GeocodingResult.Success>(
    16, 0.75f, true  // access-order = true → LRU
) {
    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, GeocodingResult.Success>
    ): Boolean = size > 50
}

suspend fun geocodeAddress(address: String): GeocodingResult {
    val key = address.trim().lowercase()

    // Check cache first
    cache[key]?.let { return it }

    // Cache miss — call the API
    return try {
        val response = api.geocode(address, BuildConfig.GOOGLE_MAPS_API_KEY)
        when (response.status) {
            "OK" -> {
                val location = response.results.firstOrNull()?.geometry?.location
                if (location == null) {
                    GeocodingResult.Error(GeocodingResult.ErrorType.AddressNotFound)
                } else {
                    val result = GeocodingResult.Success(location.lat, location.lng)
                    cache[key] = result   // store in cache
                    result
                }
            }
            "ZERO_RESULTS" -> GeocodingResult.Error(GeocodingResult.ErrorType.AddressNotFound)
            "REQUEST_DENIED" -> GeocodingResult.Error(GeocodingResult.ErrorType.ApiKeyError)
            else -> GeocodingResult.Error(GeocodingResult.ErrorType.ConnectionError)
        }
    } catch (error: IOException) {
        GeocodingResult.Error(GeocodingResult.ErrorType.ConnectionError)
    } catch (error: HttpException) {
        GeocodingResult.Error(GeocodingResult.ErrorType.ConnectionError)
    }
}
```

The `cache` object is not thread-safe on its own. Since all Geocoding
calls already run inside `viewModelScope.launch` on the IO dispatcher,
wrap the cache read and write with `synchronized(cache)`:

```kotlin
// Read
val cached = synchronized(cache) { cache[key] }
cached?.let { return it }

// Write after successful API call
synchronized(cache) { cache[key] = result }
```

---

### 1B — CSV Cache Cleanup (`SaveExportScreen.kt` and `RouteDetailScreen.kt`)

**Problem:** Every export writes a `.csv` file to `context.cacheDir`
for sharing via FileProvider, but those files are never deleted.
Over time the cache directory fills with stale CSV files.

**Fix in `SaveExportScreen.kt`:** After the `Intent.createChooser`
is started, add a cleanup call that deletes all `.csv` files in
`cacheDir` that are older than 1 hour:

```kotlin
// After context.startActivity(Intent.createChooser(...))
withContext(Dispatchers.IO) {
    context.cacheDir.listFiles()
        ?.filter { it.extension == "csv" && it.lastModified() < System.currentTimeMillis() - 3_600_000L }
        ?.forEach { it.delete() }
}
```

**Fix in `RouteDetailScreen.kt`:** Apply the same cleanup after the
`Intent.createChooser` call inside the Export button's `onClick`:

```kotlin
// After context.startActivity(Intent.createChooser(...))
val cutoff = System.currentTimeMillis() - 3_600_000L
context.cacheDir.listFiles()
    ?.filter { it.extension == "csv" && it.lastModified() < cutoff }
    ?.forEach { it.delete() }
```

Note: `RouteDetailScreen` runs its export entirely inside an `onClick`
lambda on the main thread, so the cleanup there must also run on the
main thread. The `listFiles` + `delete` operations are lightweight
enough (a handful of small files) that blocking is acceptable here.
If in future this becomes a concern it can be moved to a coroutine,
but do not add that complexity now.

---

## Part 2 — Remove Stop from Route Detail

### 2A — `StopDao.kt`

Add a new query to delete a single stop by its id:

```kotlin
@Query("DELETE FROM stops WHERE id = :stopId")
suspend fun deleteById(stopId: Long)
```

---

### 2B — `RouteDetailViewModel.kt`

Add a `removeStop` function and a `deletingStopIds` StateFlow to
track which stops are currently being deleted (used to disable the
delete button while the operation runs):

```kotlin
private val _deletingStopIds = MutableStateFlow<Set<Long>>(emptySet())
val deletingStopIds: StateFlow<Set<Long>> = _deletingStopIds

fun removeStop(stopId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        _deletingStopIds.value = _deletingStopIds.value + stopId
        db.stopDao().deleteById(stopId)
        // Reload stops after deletion
        val updatedStops = db.stopDao().getByRouteId(
            _uiState.value.route?.id ?: return@launch
        )
        _uiState.value = _uiState.value.copy(stops = updatedStops)
        _deletingStopIds.value = _deletingStopIds.value - stopId
    }
}
```

Also update the `RouteDetailUiState` import — `MutableStateFlow`
must be imported if not already present.

---

### 2C — `RouteDetailScreen.kt`

#### Add import
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```

#### Collect new state
Inside `RouteDetailScreen`, alongside the existing `uiState` collection:
```kotlin
val deletingStopIds by viewModel.deletingStopIds.collectAsState()
```

#### Add confirmation dialog state
After the existing state declarations at the top of `RouteDetailScreen`:
```kotlin
var stopPendingDelete by remember { mutableStateOf<Stop?>(null) }
```

Add the import:
```kotlin
import com.example.router_app.data.local.Stop
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
```

#### Update each stop card
Replace the current stop card content inside `itemsIndexed`:

```kotlin
// BEFORE
itemsIndexed(uiState.stops) { index, stop ->
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${index + 1}. ${stop.address}")
                Text(
                    text = "Lat: ${stop.lat}, Lng: ${stop.lng}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// AFTER
itemsIndexed(uiState.stops) { index, stop ->
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${index + 1}. ${stop.address}")
                Text(
                    text = "Lat: ${stop.lat}, Lng: ${stop.lng}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = { stopPendingDelete = stop },
                enabled = !deletingStopIds.contains(stop.id),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove stop",
                )
            }
        }
    }
}
```

#### Add confirmation dialog
Place this block at the bottom of `RouteDetailScreen`, after the
existing buttons but still inside the Column:

```kotlin
stopPendingDelete?.let { stop ->
    AlertDialog(
        onDismissRequest = { stopPendingDelete = null },
        title = { Text(text = "Remove stop?") },
        text = { Text(text = stop.address) },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.removeStop(stop.id)
                    stopPendingDelete = null
                },
                enabled = !deletingStopIds.contains(stop.id),
            ) {
                Text(text = "Remove")
            }
        },
        dismissButton = {
            Button(onClick = { stopPendingDelete = null }) {
                Text(text = "Cancel")
            }
        },
    )
}
```

Add the missing import:
```kotlin
import androidx.compose.material3.AlertDialog
```

---

## Delivery Checklist

### Part 1 — Performance
- [ ] `GeocodingRepository` has a `LinkedHashMap` LRU cache (max 50 entries)
- [ ] Cache reads and writes are wrapped in `synchronized(cache)`
- [ ] Only `GeocodingResult.Success` is cached; errors are not
- [ ] Cache hit skips the API call entirely
- [ ] `SaveExportScreen` deletes stale CSV cache files (older than 1h) after export
- [ ] `RouteDetailScreen` deletes stale CSV cache files (older than 1h) after export

### Part 2 — Remove Stop
- [ ] `StopDao.deleteById(stopId: Long)` added
- [ ] `RouteDetailViewModel.deletingStopIds` StateFlow added
- [ ] `RouteDetailViewModel.removeStop(stopId)` deletes from DB and reloads stops
- [ ] `deletingStopIds` collected in `RouteDetailScreen`
- [ ] `stopPendingDelete` state variable added
- [ ] Delete icon button visible on each stop card
- [ ] Delete button disabled while deletion is in progress
- [ ] Confirmation dialog shown before deletion
- [ ] Stop removed from list after deletion (reactive reload)
- [ ] All required imports added
- [ ] No other files modified
