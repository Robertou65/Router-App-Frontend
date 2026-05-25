# Android OCR App — Development Plan

## 1. Camera Access

Android requires two steps to access the camera:

**Declare the permission in `AndroidManifest.xml`**
You need to declare `uses-permission` for `CAMERA`, and optionally `uses-feature` to indicate the app requires a camera. This tells the Play Store and the OS what hardware your app depends on.

**Request the permission at runtime**
Since Camera is a *dangerous permission* (Android 6.0+), you can't just declare it — you must ask the user at runtime. With Jetpack Compose, the cleanest way to do this is using the **Accompanist Permissions** library (`com.google.accompanist:accompanist-permissions`), which provides a composable-friendly API to check and request permissions without dropping into legacy Activity callback code.

The flow is:
1. Check if permission is already granted.
2. If not, show a rationale to the user (a dialog or message explaining *why* you need the camera).
3. Launch the system permission dialog.
4. React to the result (granted → open camera / denied → show a graceful fallback).

---

## 2. OCR Library — Use ML Kit

The clear best choice for a native Android app is **Google ML Kit Text Recognition**.

| Option | Pros | Cons |
|---|---|---|
| **ML Kit** (Google) | On-device, fast, free, official Jetpack support | Less accurate on complex layouts |
| Tesseract (tess-four) | Open source, highly configurable | Heavier, slower, harder to integrate |
| Firebase ML | Cloud option, higher accuracy | Requires network, has cost |
| AWS Rekognition / Azure OCR | Very high accuracy | Cloud-only, adds backend complexity |

**ML Kit is the right pick** for a basic app because it runs fully **on-device** (no internet needed), it's maintained by Google, it integrates naturally with CameraX, and it's free with no API key required.

---

## 3. The Camera Layer — Use CameraX

You shouldn't use the raw `Camera2` API. Instead, use **CameraX** (`androidx.camera`), which is the modern Jetpack camera library. It has a specific use case called `ImageAnalysis` that streams camera frames and lets you pass each frame directly into ML Kit for real-time OCR.

---

## 4. Full Architecture Plan

```
┌─────────────────────────────────────┐
│           Compose UI Layer          │
│  - Permission request screen        │
│  - Camera preview (AndroidView)     │
│  - Overlay with detected text       │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│           ViewModel                 │
│  - Holds UI state (text result,     │
│    permission state, errors)        │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│         CameraX Controller          │
│  - PreviewUseCase → shows viewfinder│
│  - ImageAnalysisUseCase → feeds     │
│    frames to the OCR analyzer       │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│         ML Kit Analyzer             │
│  - Implements ImageAnalysis.        │
│    Analyzer interface               │
│  - Passes ImageProxy to ML Kit      │
│  - Returns List<Text.Block>         │
└─────────────────────────────────────┘
```

---

## 5. Key Dependencies (Gradle)

Here's the set of libraries you'll need to add to your `build.gradle`:

- `androidx.camera:camera-core` — CameraX core
- `androidx.camera:camera-camera2` — CameraX Camera2 backend
- `androidx.camera:camera-lifecycle` — Ties camera to lifecycle
- `androidx.camera:camera-view` — `PreviewView` composable wrapper
- `com.google.mlkit:text-recognition` — ML Kit OCR (Latin script)
- `com.google.accompanist:accompanist-permissions` — Compose permission helper

---

## 6. Screen Flow

```
App Launch
    │
    ▼
Check Camera Permission
    ├─ Denied ──► Show rationale ──► Request ──► [Denied again] ──► Guide to Settings
    │
    └─ Granted
         │
         ▼
    Camera Preview Screen
    (Live viewfinder + real-time text overlay)
         │
         ▼
    User taps "Capture"
         │
         ▼
    Display extracted text
    (Copy / Share / Save options)
```

---

## 7. Important Considerations

- **Rotation handling** — Camera frames have an `imageInfo.rotationDegrees` value you must pass to ML Kit, otherwise OCR on a rotated frame will fail or produce garbage.
- **Performance** — ML Kit analysis can be called on every frame. Use `STRATEGY_KEEP_ONLY_LATEST` in `ImageAnalysis` so frames are dropped when the analyzer is busy, preventing memory buildup.
- **Language support** — The default ML Kit module handles Latin scripts (English, Spanish, etc.). For other scripts (Chinese, Devanagari, Korean, Japanese), you need to add their specific ML Kit module dependency.
- **Min SDK** — ML Kit Text Recognition requires **minSdk 21** (Android 5.0) at minimum.

---

This plan gives you a solid, modern, fully on-device OCR app. When you're ready to start coding, the natural order is: ① set up dependencies → ② build the permission flow → ③ integrate the camera preview → ④ wire up the ML Kit analyzer.


# Google Maps API — Getting Coordinates from an Address

What you need is called **Geocoding** — converting a human-readable address into geographic coordinates (lat/lng). Google has exactly one API for that.

---

## The API You Need: Geocoding API

**`https://maps.googleapis.com/maps/api/geocode/json`**

You send a plain text address, it returns latitude & longitude. That's it. Simple POST/GET request.

```
"Calle 10 #43E-31, Medellín, Colombia"
        │
        ▼
   Geocoding API
        │
        ▼
  { lat: 6.2087, lng: -75.5740 }
```

---

## Why Not the Others?

The Google Maps ecosystem has many APIs and it's easy to get lost. Here's a quick orientation so you know you're picking the right one:

| API | Purpose | Do you need it? |
|---|---|---|
| **Geocoding API** | Address → Coordinates | ✅ **YES — this is your pick** |
| Maps SDK for Android | Renders an interactive map in-app | Only if you show a map UI |
| Places API | Search businesses by name/type | No — you already have the address |
| Directions API | Route between two points | No — you just need the location |
| Distance Matrix API | Travel time/distance between points | No |
| Roads API | Snap coordinates to roads | No |
| Geolocation API | Device location via Wi-Fi/cell towers | No — that's the phone's GPS job |

---

## How the Flow Fits Into Your App

```
Camera
  │
  ▼
ML Kit OCR  ──►  Raw text (messy)
  │
  ▼
Text Parser  ──►  Clean address string
  │                (you extract just the address part)
  ▼
Geocoding API  ──►  { lat, lng }
  │
  ▼
Use coordinates
(store, display, pass to delivery system, etc.)
```

---

## Two Ways to Call It in Android

### Option A — Direct REST call (simple)
Call the HTTP endpoint directly using **Retrofit** or **Ktor**. You get full control and no extra SDK weight.

```
GET https://maps.googleapis.com/maps/api/geocode/json
  ?address=Calle+10+%2343E-31+Medellin+Colombia
  &key=YOUR_API_KEY
```

The response gives you a `results` array where `geometry.location` holds `lat` and `lng`.

### Option B — Android Places SDK
Google also offers a native Android SDK. It's heavier and better suited for *search autocomplete* UIs. Since you already have a full address string from OCR, the REST call is cleaner and simpler for your use case.

**→ Go with Option A (direct REST).**

---

## Setup Checklist

Before making any call, you need to:

1. **Enable the Geocoding API** in your [Google Cloud Console](https://console.cloud.google.com/) under *APIs & Services → Library*.
2. **Create an API Key** and restrict it to the Geocoding API (best practice for security).
3. **Restrict the key to your Android app** using your app's SHA-1 fingerprint + package name — this prevents unauthorized use of your key.
4. **Add billing** to your Google Cloud project. Geocoding API is not free, but it has a **$200/month free credit**, which covers ~40,000 geocoding requests — more than enough for a basic app.

---

## One Important Warning — Address Quality

OCR output from a physical package label is often noisy (`"C/ 10 #43E - 31"`, line breaks, extra characters). The Geocoding API is fairly tolerant but **the cleaner the address string, the better the result**. You'll likely need a small parsing/cleaning step between ML Kit output and the API call. That's a critical piece of the plan to keep in mind for the next step.

# Full App Plan — OCR + Geocoding + Interactive Map

## Updated Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Compose UI Layer                   │
│                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │   Camera    │  │   Address    │  │    Map      │ │
│  │   Screen    │  │   Review     │  │   Screen    │ │
│  │             │  │   Screen     │  │             │ │
│  └──────┬──────┘  └──────┬───────┘  └──────┬──────┘ │
└─────────┼────────────────┼─────────────────┼────────┘
          │                │                 │
┌─────────▼────────────────▼─────────────────▼────────┐
│                     ViewModels                       │
│         (CameraVM)   (AddressVM)     (MapVM)         │
└─────────┬────────────────┬─────────────────┬────────┘
          │                │                 │
┌─────────▼──────┐ ┌───────▼──────┐ ┌────────▼───────┐
│   ML Kit OCR   │ │   Geocoding  │ │   Maps SDK     │
│   + CameraX    │ │     API      │ │  for Android   │
└─────────┬──────┘ └───────┬──────┘ └────────────────┘
          │                │
┌─────────▼──────┐ ┌───────▼──────┐
│ Address Parser │ │  Repository  │
│  (cleaning     │ │  (Retrofit + │
│   algorithm)   │ │   caching)   │
└────────────────┘ └──────────────┘
```

---

## The 3 Screens

### Screen 1 — Camera & OCR
The live camera viewfinder with ML Kit running in the background. The user points at the package label and taps a capture button. The raw OCR text is grabbed and immediately sent to the parser.

### Screen 2 — Address Review
This is a critical UX step. Before hitting the Geocoding API, show the user the **parsed address** in an editable text field so they can fix any OCR mistakes before confirming. Never skip this — OCR on physical labels is imperfect.

```
┌──────────────────────────────────┐
│  📦 Detected Address             │
│                                  │
│  ┌────────────────────────────┐  │
│  │ Calle 10 #43E-31,          │  │
│  │ Medellín, Antioquia        │  │
│  └────────────────────────────┘  │
│        (editable field)          │
│                                  │
│   [  Edit  ]   [ Confirm ✓ ]     │
└──────────────────────────────────┘
```

### Screen 3 — Map Verification
After the Geocoding API returns coordinates, the Maps SDK renders an interactive map with a pin dropped at the resolved location. The user can pan/zoom to visually verify it's correct.

```
┌──────────────────────────────────┐
│  ╔════════════════════════════╗  │
│  ║                            ║  │
│  ║        [Map View]          ║  │
│  ║           📍               ║  │
│  ║                            ║  │
│  ╚════════════════════════════╝  │
│                                  │
│  Calle 10 #43E-31, Medellín      │
│  6.2087° N, 75.5740° W           │
│                                  │
│  [ ← Try Again ]  [ Confirm ✓ ]  │
└──────────────────────────────────┘
```

---

## New Dependency — Maps SDK for Android

Add this to your existing dependency list:

- `com.google.maps.android:maps-compose` — This is the **Jetpack Compose wrapper** for Google Maps. It gives you a `GoogleMap()` composable that fits natively into your Compose UI. Without this you'd have to use an `AndroidView` to wrap the legacy `MapView`, which is messy.
- `com.google.android.gms:play-services-maps` — The underlying Maps SDK (pulled in automatically by maps-compose).

> In Google Cloud Console you now need **two APIs enabled**: Geocoding API + Maps SDK for Android.

---

## Address Parser — The Algorithm Plan

This is the logic layer between raw OCR text and the clean string sent to Geocoding. It doesn't need to be an ML model — a set of well-ordered rules handles most cases.

### What OCR output typically looks like from a package label:
```
DESTINATARIO / RECIPIENT
Cra. 45 # 10 - 20 Apto 301
Barrio El Poblado
Medell1n, Antioquia    C0L
TEL: 300-123-4567
```

### Parsing steps:

**Step 1 — Strip noise lines**
Remove lines that match known non-address patterns: phone numbers (`TEL:`, digits with dashes), labels (`DESTINATARIO`, `RECIPIENT`, `TO:`, `PARA:`), postal codes standing alone, and country codes.

**Step 2 — Identify the address line**
The address line almost always contains a street keyword. For Colombia specifically, that means tokens like `Cra`, `Carrera`, `Calle`, `Cl`, `Av`, `Avenida`, `Transversal`, `Tr`, `Diagonal`. For international addresses, look for numeric patterns like `123 Main St`.

**Step 3 — Fix common OCR character errors**
OCR frequently confuses characters on printed labels:
- `0` ↔ `O` (zero vs letter O) — e.g., `C0LOMBIA` → `COLOMBIA`
- `1` ↔ `l` or `I`
- `#` sometimes read as `H` or missing entirely

**Step 4 — Normalize formatting**
Standardize separators (`Cra. 45 # 10 - 20` and `Carrera 45 #10-20` should become the same string), remove double spaces, fix capitalization.

**Step 5 — Append city/country if missing**
If the city wasn't detected on the label, append a default from the app's context (e.g. `", Medellín, Colombia"`). The Geocoding API resolves much better with a full address.

**Step 6 — Confidence flag**
After parsing, assign a confidence level (`HIGH` / `MEDIUM` / `LOW`) based on how many rules fired cleanly. If confidence is LOW, force the Address Review screen to be non-skippable.

---

## Complete User Flow

```
App opens
    │
    ▼
Camera Permission check
    │
    ▼
Screen 1 — Camera
User points at package label → taps Capture
    │
    ▼
ML Kit OCR → raw text block
    │
    ▼
Address Parser → cleaned address string + confidence score
    │
    ▼
Screen 2 — Address Review    ◄──── User can edit manually
User confirms address
    │
    ▼
Geocoding API call (Retrofit)
    ├── Error (address not found) ──► Show error + go back to Screen 2
    │
    └── Success → { lat, lng }
         │
         ▼
Screen 3 — Map Verification
Interactive map with pin at coordinates
User verifies location visually
    │
    ├── [ ← Try Again ] ──► Back to Screen 2
    │
    └── [ Confirm ✓ ] ──► Address + coordinates saved ✅
```

---

## Full Dependency Summary

| Library | Purpose |
|---|---|
| `androidx.camera:camera-*` | Live camera feed |
| `com.google.mlkit:text-recognition` | OCR engine |
| `com.google.accompanist:accompanist-permissions` | Runtime camera permission |
| `com.squareup.retrofit2:retrofit` | HTTP client for Geocoding API |
| `com.google.maps.android:maps-compose` | Compose-native Google Maps |
| `com.google.android.gms:play-services-maps` | Maps SDK core |
| `androidx.navigation:navigation-compose` | Navigation between the 3 screens |

---

## Google Cloud Console Checklist

- [x] Geocoding API — enabled
- [x] Maps SDK for Android — enabled
- [x] One API key (restrict it to both APIs + your app's SHA-1)
- [x] Billing account linked ($200 free monthly credit covers both APIs)

# Inter-App Communication — How Apps Talk to Each Other Without Internet

This is a fundamental Android OS concept. No internet is needed because the communication happens through the **Android operating system itself** as a message broker between apps installed on the same device.

---

## The Core Mechanism: Android Intents

An **Intent** is Android's built-in inter-process communication system. Think of it as a message envelope you hand to the OS, and the OS decides which app should open it — or lets the user choose.

```
Your App                  Android OS               Routin
   │                          │                      │
   │── sends Intent ─────────►│                      │
   │   (address data)         │── delivers ─────────►│
   │                          │                      │
   │                          │          Routin opens│
   │                          │          with the    │
   │                          │          address ────│
```

Nothing goes through the internet. The OS delivers the message locally between the two apps in memory.

---

## The 3 Ways Apps Connect to Routin (or any app)

### Method 1 — Geo URI Intent (most likely what you've seen)
The `geo:` URI scheme is a standard Android protocol that any map/routing app can register to handle. Your app fires an Intent with a geographic URI and the OS asks the user which app should handle it — Routin, Google Maps, Waze, etc.

```
geo:6.2087,-75.5740?q=Calle+10+%2343E-31+Medellin
```

This is exactly how Google Maps, Waze, and other apps already receive locations from external apps. Routin registers itself as a handler for this same URI scheme. **This is the integration path most relevant to your app.**

### Method 2 — Deep Link (custom URI scheme)
An app can register its own custom URI scheme in its `AndroidManifest.xml`. For example, Routin could expose something like:

```
routin://add-stop?address=Calle+10+%2343E-31&lat=6.2087&lng=-75.5740
```

When your app fires an Intent with that URI, Android routes it directly to Routin because only Routin is registered to handle the `routin://` scheme. No ambiguity, no dialog — it goes straight there. This requires knowing the target app's documented scheme.

### Method 3 — File Import (indirect)
Routin supports importing multiple stops data using files in CSV, KML, GPX, and XLS formats. Your app could generate a CSV file with all the addresses and use Android's `ACTION_SEND` Intent to share it. Routin would receive the file and bulk-import all stops at once. This is less seamless UX-wise but very reliable for sending multiple addresses at once.

---

## Which Method to Use for Your App

Given your flow (OCR → parse → geocode → verify → send to Routin), the recommended approach is:

```
Your app confirms address
        │
        ▼
Build a geo: URI  ──►  startActivity(Intent)
        │
        ▼
Android OS shows chooser
(or goes directly to Routin if set as default)
        │
        ▼
Routin opens with the address/pin pre-loaded
```

The `geo:` URI is the **safest and most compatible** choice because it doesn't depend on Routin's internal implementation — it's an open standard that any routing app handles. Your app stays decoupled from Routin specifically, so it also works with Google Maps, Waze, or any future app the user prefers.

---

## Important Note About Routin's Own API

Routin's website mentions that their API and Webhook features empower seamless integration between external systems and their platform. This is their **web/cloud API** — a different path that goes through the internet and is aimed at business systems (like a warehouse management system pushing hundreds of stops to a driver's Routin account). That's overkill for your use case of sending one confirmed address at a time from a phone app.

---

## Updated App Flow with Routin Integration

```
Screen 3 — Map Verification
User taps [ Confirm ✓ ]
        │
        ▼
Build Intent:
  action = ACTION_VIEW
  data   = geo:{lat},{lng}?q={address}
        │
        ▼
Android OS  ──► User picks Routin (or it opens directly)
        │
        ▼
Routin opens with the stop pre-filled ✅
```

---

## Summary Table

| Method | How it works | Internet needed | Best for |
|---|---|---|---|
| `geo:` URI Intent | OS standard protocol | ❌ No | Single address, any map app |
| Deep Link URI | App-specific scheme | ❌ No | Direct Routin integration |
| File share (CSV) | `ACTION_SEND` Intent | ❌ No | Batch of many addresses |
| Routin Web API | HTTP to Routin servers | ✅ Yes | Enterprise/backend systems |

For your app, **`geo:` URI Intent** is the cleanest path — one line of code to trigger it, works with Routin and every other navigation app, and requires zero internet.

Exactly, and it fits your app's workflow really naturally:

```
Scan package 1 ──► OCR ──► Parse ──► Verify on map ──► Add to list
Scan package 2 ──► OCR ──► Parse ──► Verify on map ──► Add to list
Scan package 3 ──► OCR ──► Parse ──► Verify on map ──► Add to list
        │
        ▼
  [ Export to Routin ]
        │
        ▼
  Generate CSV  ──►  ACTION_SEND Intent  ──►  Routin imports all stops ✅
```

So your app naturally becomes a **scanning session tool** — the user scans all their packages first, reviews each address on the map, builds up a list, and when done sends the whole batch to Routin in one shot. That's actually better UX than sending one by one.

---

A couple of things worth keeping in mind for when you plan the CSV step:

- **Column format matters** — you'll need to check Routin's expected CSV structure (they document it). It typically expects columns like `name`, `address`, `latitude`, `longitude`, `phone`, `notes`. Latitude and longitude are important to include since you already have them from the Geocoding API — it makes Routin's import more accurate and skips any re-geocoding on their end.
- **The list screen** becomes a new screen in your app — sitting between the map verification and the export. It shows all scanned & confirmed addresses in a scrollable list, lets the user remove any mistakes, and has the "Send to Routin" button at the bottom.

That list screen would make your app flow complete end to end. Want to plan that out?

# Local Route History — Plan

No account system means everything lives **on the device**. The right tool for this is **Room**, Android's official local database library (a Kotlin-friendly wrapper over SQLite).

---

## Data Structure

```
┌─────────────────────────────────┐
│            Route                │
│─────────────────────────────────│
│ id          (auto-generated)    │
│ name        "Route Nov 12"      │
│ createdAt   2024-11-12 09:30    │
│ stopCount   (derived)           │
└────────────────┬────────────────┘
                 │ 1 route has many stops
                 │
┌────────────────▼────────────────┐
│              Stop               │
│─────────────────────────────────│
│ id          (auto-generated)    │
│ routeId     (foreign key)       │
│ label       "Paquete #1"        │
│ rawOcrText  (original scan)     │
│ address     "Calle 10 #43E-31"  │
│ lat         6.2087              │
│ lng         -75.5740            │
│ order       1, 2, 3...          │
└─────────────────────────────────┘
```

Storing `rawOcrText` alongside the clean address is a good safety net — if the parser made a mistake, you can always see what the camera originally read.

---

## New Screen: Route History

This becomes the **home screen** of the app.

```
┌──────────────────────────────────┐
│  📦 My Routes                    │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 🗓 Route - Nov 12          │  │
│  │ 8 stops · 09:30 AM         │  │
│  │              [ Open  ▶ ]   │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 🗓 Route - Nov 11          │  │
│  │ 5 stops · 08:15 AM         │  │
│  │              [ Open  ▶ ]   │  │
│  └────────────────────────────┘  │
│                                  │
│         [ + New Route ]          │
└──────────────────────────────────┘
```

---

## Route Detail Screen

When the user opens a saved route they see the full stop list and can export at any time.

```
┌──────────────────────────────────┐
│  ← Route - Nov 12                │
│                                  │
│  1. Calle 10 #43E-31             │
│  2. Carrera 45 #52-29            │
│  3. Av. El Poblado #1-20         │
│     ...                          │
│                                  │
│  [ 📤 Export CSV to Routin ]     │
└──────────────────────────────────┘
```

---

## Complete App Flow (Updated)

```
App opens
     │
     ▼
Route History Screen (home)
     │
     ├── Tap existing route ──► Route Detail ──► Export CSV ──► Routin
     │
     └── Tap [ + New Route ]
               │
               ▼
          Camera Screen
          Scan package ──► OCR ──► Parse ──► Map Verify
               │                                  │
               │                          [ Add to Route ]
               │                                  │
               ▼                                  ▼
          Scan next package              Stop saved to Room DB
               │
               └── Tap [ Done ]
                        │
                        ▼
                  Route auto-saved
                  with timestamp
                        │
                        ▼
                  Route Detail Screen
                  [ Export CSV to Routin ]
```

---

## Dependency to Add

Only one new library needed:

- `androidx.room:room-runtime` — Room database core
- `androidx.room:room-ktx` — Kotlin coroutines support for Room
- `androidx.room:room-compiler` — Annotation processor (KSP)

Room integrates with your existing ViewModel + Kotlin architecture with no friction.

---

## Updated Full Dependency Summary

| Library | Purpose |
|---|---|
| `androidx.camera:camera-*` | Live camera feed |
| `com.google.mlkit:text-recognition` | OCR engine |
| `com.google.accompanist:accompanist-permissions` | Camera permission |
| `com.squareup.retrofit2:retrofit` | Geocoding API calls |
| `com.google.maps.android:maps-compose` | Interactive map |
| `androidx.navigation:navigation-compose` | Screen navigation |
| `androidx.room:room-*` | ✅ Local route history database |

---

No backend, no account, no internet dependency for storage — everything is self-contained on the device. The user's history is private, instant, and works fully offline.

# Clarified OCR → Geocoding Flow

Good clarification. The camera view itself becomes the feedback interface — no extra screens interrupting the scanning flow.

---

## The Flow

```
Camera view is live (neutral state)
        │
        ▼
User points at package label
        │
User taps [ Scan ] button
        │
        ▼
ML Kit OCR runs on the frozen frame
        │
        ▼
Parser & cleaner algorithm
        │
        ▼
Geocoding API request
        │
        ├── ✅ Valid response (lat/lng found)
        │         │
        │         ▼
        │   Camera overlay turns GREEN
        │   Address + coordinates stored
        │   [ Add to Route ] button appears
        │
        └── ❌ Invalid response (address not found)
                  │
                  ▼
            Camera overlay turns RED
            Fades back to neutral after ~2 seconds
            User repositions and tries again
```

---

## Camera Screen States

```
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│                  │  │ ░░░░░░░░░░░░░░░░ │  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│   [live feed]    │  │   [live feed]    │  │   [live feed]    │
│                  │  │ ░░░░░░░░░░░░░░░░ │  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│                  │  │  Processing...   │  │                  │
│  ┌──────────┐    │  │                  │  │                  │
│  │  [ 📷 ] │    │  │                  │  │                  │
│  └──────────┘    │  │                  │  │                  │
└──────────────────┘  └──────────────────┘  └──────────────────┘
   NEUTRAL STATE          LOADING STATE        RESULT STATE
   user scans label       OCR + API running     green or red
```

### Result states in detail:

```
┌──────────────────┐        ┌──────────────────┐
│ ╔══════════════╗ │        │ ╔══════════════╗ │
│ ║ GREEN overlay║ │        │ ║  RED overlay ║ │
│ ║              ║ │        │ ║              ║ │
│ ║ Calle 10     ║ │        │ ║  Address not ║ │
│ ║ #43E-31      ║ │        │ ║  recognized  ║ │
│ ║ Medellín ✓   ║ │        │ ║  Try again   ║ │
│ ╚══════════════╝ │        │ ╚══════════════╝ │
│ [ Add to Route ] │        │  (auto-resets    │
│                  │        │   after 2s)      │
└──────────────────┘        └──────────────────┘
     SUCCESS ✅                  FAILURE ❌
```

---

## States to Handle in the ViewModel

```
sealed class ScanState {
    object Idle         // camera live, waiting for user
    object Scanning     // OCR running, show loading
    object Requesting   // Geocoding API call in progress
    data class Success  // green overlay + address data
    data class Failure  // red overlay + error reason
}
```

This maps directly to what the Compose UI renders as an overlay on top of the camera preview.

---

## What Triggers Each Transition

| From | Event | To |
|---|---|---|
| `Idle` | User taps Scan button | `Scanning` |
| `Scanning` | ML Kit returns text | `Requesting` |
| `Scanning` | ML Kit finds no text | `Failure` ("No text detected") |
| `Requesting` | Geocoding returns valid lat/lng | `Success` |
| `Requesting` | Geocoding returns zero results | `Failure` ("Address not found") |
| `Requesting` | Network/API error | `Failure` ("Connection error") |
| `Failure` | 2 second timer expires | `Idle` |
| `Success` | User taps Add to Route | address saved → `Idle` |
| `Success` | User taps scan again | `Idle` |

---

## Key Design Decision — Freeze the Frame

When the user taps Scan, **freeze the camera frame** immediately instead of keeping the live feed running. This gives the user a clear visual signal that the scan was captured, and prevents ML Kit from running on a moving image during the API call. The frozen frame sits underneath the green/red overlay while the process completes.

---

This keeps the entire scanning experience on **one single screen** — no navigation interruptions, no modals. Fast, fluid, and the color feedback is instantly readable even in a warehouse or delivery environment.\

# Refined UX Flow — Full Plan

## Screen Flow

```
App opens
    │
    ▼
Route History Screen
    │
    └── [ + New Route ]
              │
              ▼
        Camera Screen ──────────────────────────────────────────►──┐
        (scanning session)                                          │
              │                                               continue scanning
              │
        User taps [ Scan ]
              │
         ┌────┴─────┐
         │          │
        ❌ RED     ✅ GREEN
        2s fade     Address appended
        back to     to top list
        neutral          │
                         ▼
                   Continue scanning?
                    │           │
                   YES          NO → taps [ Finish ]
                    │                      │
                    └──────────────────────┘
                                │
                                ▼
                        Save & Export Screen
                        (filename, location, export)
```

---

## Camera Screen Layout

This is the most important screen. Everything lives here without blocking the camera.

```
┌─────────────────────────────────────┐
│  ┌───────────────────────────────┐  │  ← Scrollable address list
│  │ ✓ Calle 10 #43E-31, Medellín  │  │    at top, semi-transparent
│  │ ✓ Carrera 45 #52-29, Medellín │  │    background so camera
│  │ ✓ Av. El Poblado #1-20        │  │    stays visible behind
│  └───────────────────────────────┘  │
│                                     │
│                                     │
│          [  live camera  ]          │  ← Full screen camera
│                                     │    behind everything
│                                     │
│                                     │
│  ┌──────────────────────────────┐   │
│  │       [ 📷 Scan ]            │   │  ← Scan button center bottom
│  └──────────────────────────────┘   │
│  ┌──────────────────────────────┐   │
│  │         [ Finish ]           │   │  ← Finish button below scan
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```

### During scan (loading state):
```
┌─────────────────────────────────────┐
│  ✓ Calle 10 #43E-31, Medellín       │
│  ✓ Carrera 45 #52-29                │
│                                     │
│        [ frozen camera frame ]      │
│                                     │
│         ⏳ Reading address...        │
│                                     │
│     [ 📷 Scan ] (disabled)          │
│     [ Finish  ] (disabled)          │
└─────────────────────────────────────┘
```

### Success state (green, ~1.5s):
```
┌─────────────────────────────────────┐
│  ✓ Calle 10 #43E-31, Medellín       │
│  ✓ Carrera 45 #52-29                │
│  ✓ Av. El Poblado #1-20  ← NEW      │  ← New address slides in
│                                     │
│  ╔═════════════════════════════╗    │
│  ║   [camera — green overlay]  ║    │  ← Full frame green tint
│  ╚═════════════════════════════╝    │
│                                     │
│     [ 📷 Scan ] (disabled)          │
│     [ Finish  ] (disabled)          │
└─────────────────────────────────────┘
```

### Failure state (red, ~1.5s then resets):
```
┌─────────────────────────────────────┐
│  ✓ Calle 10 #43E-31, Medellín       │
│  ✓ Carrera 45 #52-29                │
│                                     │
│  ╔═════════════════════════════╗    │
│  ║   [camera — red overlay]    ║    │  ← Full frame red tint
│  ║     Address not found       ║    │
│  ╚═════════════════════════════╝    │
│                                     │
│     [ 📷 Scan ] (disabled)          │
│     [ Finish  ] (disabled)          │
└─────────────────────────────────────┘
```

---

## Save & Export Screen

Triggered when the user taps **Finish**. Clean and simple form.

```
┌─────────────────────────────────────┐
│  ←  Save Route                      │
│                                     │
│  Route name                         │
│  ┌───────────────────────────────┐  │
│  │ Route - Nov 12 - 09:30        │  │  ← Auto-filled with date/time
│  └───────────────────────────────┘  │    user can edit freely
│                                     │
│  Stops summary                      │
│  ┌───────────────────────────────┐  │
│  │ ✓ Calle 10 #43E-31            │  │
│  │ ✓ Carrera 45 #52-29           │  │  ← Read-only list review
│  │ ✓ Av. El Poblado #1-20        │  │
│  └───────────────────────────────┘  │
│                                     │
│  CSV filename                       │
│  ┌───────────────────────────────┐  │
│  │ route_nov12_0930.csv          │  │  ← Auto-filled, editable
│  └───────────────────────────────┘  │
│                                     │
│  Save location                      │
│  ┌───────────────────────────────┐  │
│  │ 📁 Downloads          [ Change]│  │  ← Android folder picker
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │        [ 💾 Save Route ]      │  │  ← Saves to Room DB
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │      [ 📤 Export to Routin ]  │  │  ← Generates CSV + Intent
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

Two separate actions at the bottom — **Save Route** stores it in Room (history), **Export to Routin** generates the CSV and fires the Intent. The user can do both, or just one.

---

## Updated Complete Screen Map

```
┌─────────────────┐
│  Route History  │  ← Home screen
│  (list of past  │
│   routes)       │
└────────┬────────┘
         │ [ + New Route ]
         ▼
┌─────────────────┐
│  Camera Screen  │  ← Scanning session
│  (OCR + list    │
│   + Finish btn) │
└────────┬────────┘
         │ [ Finish ]
         ▼
┌─────────────────┐
│  Save & Export  │  ← Name, folder, save, export
│  Screen         │
└────────┬────────┘
         │ [ Save Route ]
         ▼
┌─────────────────┐
│  Route History  │  ← Back home, new route appears at top
└─────────────────┘
```

---

## Small Details Worth Planning

- **Address list at top auto-scrolls** to the latest entry after each successful scan, so the user always sees the most recent one without manual scrolling.
- **Minimum 1 stop** — the Finish button should stay disabled until at least one address has been successfully scanned, preventing empty routes from being saved.
- **Auto-generated names** use the device date and time (`Route - Nov 12 - 09:30`) so the user doesn't have to type anything if they don't want to.
- **Folder picker** uses Android's native `StorageAccessFramework` — no extra library needed, it's built into the OS.
- **Save and Export are independent** — the user can save a route to history without exporting to Routin yet, and export it later from the Route History screen at any time.


# Complete App Plan — Full Resume

## What the App Does

A Kotlin + Jetpack Compose Android app for delivery drivers. The user scans package labels with the phone camera, the app extracts the delivery address via OCR, validates it through Google's Geocoding API, builds a route with multiple stops, and exports it to Routin Smart Route Planner as a CSV file. All data is stored locally — no account system, no backend.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Camera | CameraX |
| OCR | Google ML Kit Text Recognition |
| Geocoding | Google Geocoding API (REST) |
| Interactive Map | Maps SDK for Android (maps-compose) |
| HTTP Client | Retrofit |
| Local Storage | Room Database |
| Camera Permission | Accompanist Permissions |
| Navigation | Navigation Compose |
| File/Folder Picker | Android StorageAccessFramework (built-in) |

---

## Dependencies

```
androidx.camera:camera-core
androidx.camera:camera-camera2
androidx.camera:camera-lifecycle
androidx.camera:camera-view
com.google.mlkit:text-recognition
com.google.accompanist:accompanist-permissions
com.squareup.retrofit2:retrofit
com.google.maps.android:maps-compose
com.google.android.gms:play-services-maps
androidx.navigation:navigation-compose
androidx.room:room-runtime
androidx.room:room-ktx
androidx.room:room-compiler (KSP)
```

---

## Google Cloud Console Setup

- Geocoding API → enabled
- Maps SDK for Android → enabled
- One API key restricted to both APIs + app SHA-1 fingerprint
- Billing account linked ($200/month free credit covers both)

---

## Data Structure (Room)

```
Route
─────────────────────
id          (auto)
name        "Route - Nov 12 - 09:30"
createdAt   timestamp

Stop
─────────────────────
id          (auto)
routeId     (foreign key → Route)
label       "Package #1"
rawOcrText  original ML Kit output
address     cleaned address string
lat         6.2087
lng         -75.5740
order       1, 2, 3...
```

---

## Screens (4 total)

### 1 — Route History (Home)
- List of all saved routes (name, date, stop count)
- Each route has an Open button
- `[ + New Route ]` button starts a new scanning session

### 2 — Camera Screen (Scanning Session)
- Full screen live camera feed
- Scrollable semi-transparent address list at the top (non-blocking)
- `[ Scan ]` button at center bottom
- `[ Finish ]` button below it (disabled until ≥1 stop scanned)
- Camera overlay states:
  - **Neutral** → live feed, waiting
  - **Loading** → frame frozen, "Reading address..." label, both buttons disabled
  - **Green (~1.5s)** → success, new address slides into top list, then resets to neutral
  - **Red (~1.5s)** → failure with reason, then auto-resets to neutral

### 3 — Save & Export Screen
- Auto-filled route name (editable)
- Read-only stop list for review
- Auto-filled CSV filename (editable)
- Folder picker (Android native StorageAccessFramework)
- `[ Save Route ]` → saves to Room DB, goes back to Route History
- `[ Export to Routin ]` → generates CSV + fires ACTION_SEND Intent to Routin

### 4 — Route Detail Screen (from History)
- Full stop list of a past route
- `[ Export CSV to Routin ]` button available at any time

---

## Camera Screen State Machine

```
sealed class ScanState {
    Idle        // camera live, waiting
    Scanning    // ML Kit running
    Requesting  // Geocoding API call in progress
    Success     // green overlay + address data
    Failure     // red overlay + error reason
}

Transitions:
Idle        + tap Scan          → Scanning
Scanning    + text found        → Requesting
Scanning    + no text found     → Failure ("No text detected")
Requesting  + valid lat/lng     → Success
Requesting  + zero results      → Failure ("Address not found")
Requesting  + network error     → Failure ("Connection error")
Failure     + 1.5s timer        → Idle
Success     + address saved     → Idle
```

---

## OCR → Geocoding Pipeline

```
CameraX (frozen frame)
        │
        ▼
ML Kit Text Recognition
→ raw text block
        │
        ▼
Address Parser (local algorithm)
  Step 1 — Strip noise lines
           (phone numbers, labels like DESTINATARIO,
            country codes, postal codes standalone)
  Step 2 — Identify address line
           (street keywords: Calle, Cra, Carrera,
            Av, Avenida, Diagonal, Transversal)
  Step 3 — Fix OCR character errors
           (0↔O, 1↔l, missing # symbol)
  Step 4 — Normalize formatting
           (standardize separators, fix spacing)
  Step 5 — Append city/country if missing
  Step 6 — Assign confidence: HIGH / MEDIUM / LOW
        │
        ▼
Geocoding API call (Retrofit)
GET maps.googleapis.com/maps/api/geocode/json
  ?address={cleaned_address}
  &key={API_KEY}
        │
        ├── response.results not empty → Success → { lat, lng }
        └── response.results empty    → Failure
```

---

## Routin Integration

| Scenario | Method | Internet |
|---|---|---|
| Export current session | CSV file + ACTION_SEND Intent | ❌ No |
| Export from history | CSV file + ACTION_SEND Intent | ❌ No |
| Enterprise backend sync | Routin Web API | ✅ Yes (not used here) |

CSV column structure matches Routin's import format:
`name, address, latitude, longitude, phone, notes`

---

## Complete Navigation Flow

```
App opens
    │
    ▼
Route History (home)
    │
    ├── Open existing route ──► Route Detail ──► Export CSV ──► Routin
    │
    └── [ + New Route ]
              │
              ▼
        Camera Screen
        Scan packages one by one
        (green/red feedback per scan)
        Address list builds at top
              │
              └── [ Finish ] (min 1 stop)
                       │
                       ▼
                 Save & Export Screen
                 Edit name / filename / folder
                       │
                       ├── [ Save Route ] ──► Room DB ──► Route History
                       └── [ Export to Routin ] ──► CSV ──► Routin
```

---

## Architecture Pattern

```
Compose UI
    │
    ▼
ViewModels (CameraVM, RouteVM, HistoryVM)
    │
    ▼
Repository layer
    │
    ├── ML Kit Analyzer (OCR)
    ├── Address Parser (local algorithm)
    ├── Geocoding API (Retrofit)
    └── Room Database (local storage)
```

---

That's everything we designed. Clean starting point to open Android Studio and begin. The natural coding order would be:

1. Project setup + all dependencies
2. Room DB (data foundation everything else relies on)
3. Camera permission flow
4. Camera screen + ML Kit
5. Address parser algorithm
6. Geocoding API (Retrofit)
7. Camera screen state machine + color overlays
8. Maps SDK verification view
9. Save & Export screen
10. Route History screen + Route Detail screen


There's your prompt. A few things worth noting about how it's structured:

**The opening "Your Role" section** sets the AI's behavior rules before anything else — telling it to wait for your confirmation between steps, state assumptions clearly, and never deviate from the defined stack. This prevents the AI from going off-script and suggesting Flutter or some other framework mid-build.

**The technical constraints section near the end** is particularly important — it pre-empts the most common mistakes an AI makes on this type of project, like forgetting `rotationDegrees` for ML Kit, using `Uri.fromFile()` instead of FileProvider, or calling Room on the main thread.

**The "How to Work With Me" section** controls the AI's output style — complete compilable code per step, no ghost TODOs, always a summary of what was built and what's next. This keeps the session organized across 15 steps.

You can use this prompt with Claude Code, Claude chat, or any other AI coding tool. When you start a session, paste the full prompt as your first message, then just say **"Let's start with Step 1"**.

Yes, **it's the same key** — but enabling the APIs and restricting the key are two separate things you need to configure in Google Cloud Console.

---

## How It Works

Google gives you one API key per project. That single key can call **any API you have enabled** on that project. Think of the key as the door key, and each API as a room — you use the same key but you decide which rooms it can open.

```
Google Cloud Project
        │
        ├── API Key  (one key)
        │
        └── Enabled APIs
              ├── ✅ Geocoding API        → key works here
              ├── ✅ Maps SDK for Android → key works here
              └── ❌ Directions API       → key blocked here
                                           (not enabled, not needed)
```

---

## What You Need to Do in Cloud Console

### 1 — Enable both APIs
Go to **APIs & Services → Library** and make sure these two are enabled:
- `Geocoding API`
- `Maps SDK for Android`

Just having the key is not enough — if the API isn't enabled, the key returns a `REQUEST_DENIED` error even if the key is valid.

### 2 — Restrict the key (important)
Go to **APIs & Services → Credentials → Edit your key** and set two restrictions:

**API restrictions** — limit the key to only the APIs your app uses:
- Geocoding API ✅
- Maps SDK for Android ✅

This way if the key is ever leaked, it can't be used to call Directions, Places, or any other paid API on your account.

**Application restrictions** — lock the key to your Android app specifically using your app's package name and SHA-1 fingerprint. This prevents anyone else from using your key even if they find it.

---

## So for Your Prompt

The line in the prompt that says:

> *Add the Google API key to `local.properties` and wire it through `BuildConfig`*

That's still just **one key** — used in two places in the app:
- Retrofit call to the Geocoding API endpoint
- `AndroidManifest.xml` meta-data tag for the Maps SDK

Same key, two usages, zero problems.

Yes, you already have it set up. Looking at your screenshot you already have a **Maps Platform API Key** created today (May 22, 2026) with 33 APIs enabled — that's your key, ready to use.

---

## What Your Screenshot Shows

- ✅ **API Key exists** — "Maps Platform API Key" is active (green dot)
- ✅ **Google account connected** — you're already logged into Google Cloud Console, which means your Google account IS the project owner. No extra connection needed.
- ⚠️ **33 APIs enabled** — this is worth trimming down. Having 33 APIs on one key is risky. If the key leaks, someone can use all 33 APIs on your billing account.

---

## Two Things to Do Right Now

### 1 — Restrict the key to only your 2 APIs
Click **Maps Platform API Key → Edit (the 3 dots menu)**. Under API restrictions, switch from "Don't restrict" to "Restrict key" and select only:
- `Geocoding API`
- `Maps SDK for Android`

Remove the other 31. Your app only needs these two.

### 2 — Restrict to your Android app
On the same edit screen, under **Application restrictions**, select **Android apps** and add your app's package name + SHA-1 fingerprint. This locks the key to your app specifically.

---

## About the OAuth Warning

The yellow banner says *"Remember to configure the OAuth consent screen"* — you can **ignore this for now**. OAuth is for apps that access user Google account data (Gmail, Drive, Calendar, etc.). Your app only calls Maps APIs with an API key, not OAuth. It's not relevant to your project.

Simple steps. Both APIs are enabled the exact same way through the Library section of Google Cloud Console.

---

## Enable Geocoding API

1. Go to **[console.cloud.google.com](https://console.cloud.google.com)**
2. Make sure your project is selected at the top
3. Left sidebar → **APIs & Services → Library**
4. Search for `Geocoding API`
5. Click it → click **Enable**

---

## Enable Maps SDK for Android

Same flow, different search:

1. Stay in **APIs & Services → Library**
2. Search for `Maps SDK for Android`
3. Click it → click **Enable**

---

## Verify Both Are Active

After enabling, go to **APIs & Services → Dashboard**. You should see both listed there with traffic graphs. If they appear in the list they are active and your key can use them.

```
Dashboard
─────────────────────────────────
✅ Geocoding API
✅ Maps SDK for Android
```

---

One thing to check — before using the Geocoding API you need a project with a billing account enabled. If you haven't added a billing account to your Cloud project yet, Google will block the API calls even with a valid key. The Enable button itself might be grayed out until billing is set up. You won't be charged unless you exceed the free $200/month credit, but the billing account must be linked regardless.

Let me read both CSV files first.Now I have everything I need. Let me analyze all three files carefully.

The photo shows the real package label, the app CSV shows what ML Kit actually captured (very messy), and the Routin CSV shows the expected format. There are also **two problems at once** — the parser and the CSV format itself.

---

## Problem 1 — CSV Format is Wrong

Your app is generating the wrong format. Routin's example file shows:

```
// YOUR APP (wrong)
name, address, latitude, longitude, notes
Package #1, "International Courier Sas ...", 4.577832, -74.1140209,

// ROUTIN EXPECTS (correct)
Name;Address;Latitude;Longitude;Phone;Group;Notes
Central Park;NY 10024 New York;40.783372;-73.965366;;;Note-1
```

Two differences to fix immediately:
- **Separator is `;` (semicolon)**, not `,` (comma)
- **Routin has 3 extra columns**: `Phone`, `Group`, `Notes` — they can be empty but must exist

---

## Problem 2 — The Parser Has No Structure to Work With

Looking at the real label photo vs what ML Kit captured, here is exactly what happened:

```
REAL LABEL                          ML KIT OUTPUT (raw)
──────────────────────────────────────────────────────────────
Admitido por: BEYOND BORDER    →    International Courier Sas
INTERNATIONAL COURIER S.A.S    →    Admiido Por Deyond Border   ← OCR errors
Operado por: XCARGO SAS        →    Operado Por: Xcarg0 Sas

ROBERT HERNAN HERNANDEZ ALVIS  →    Robert Hernan Hernandez Alvis
Destino: KR 18b # 32 - 06 SUR →    Destino: Kr 18b # 32 - 06   ← ADDRESS IS HERE
Quiroga Central, Rafael         →    Sur Quiroga Certra, Rafael
Uribe Uribe, Bogota DC          →    (merged into one block)
C.P. 111811, Colombia           →
Tel: 3005286287                 →

Referencia: Casa esquinera...   →    Reterencia Casa Esquinera...
Origen: 16192 Coastal Highway   →    Orngen 16192 Constal...

EAD: 2026-04-14                 →    Ead: 2026-04-14
FACTURA / PESO / VALOR / ENVÍO  →    all dumped together
CONTENIDO: Keyboard             →
PO: F755542604141MT7b37         →
TN: 2645702544970294            →    26a5702544970294            ← OCR error
Bogota DC-Rafael Uribe Uribe    →
ST: 19-1                        →
RAFAEL URIBE ZONA 3             →
```

**The key insight:** this label has a clear, consistent structure. The delivery address always starts with the keyword `Destino:` and ends before `Referencia:`. Your parser needs to be built around this label structure specifically.

---

## Updated Parser — Built Around This Label Format

```
RAW ML KIT TEXT
      │
      ▼
STEP 1 — Split into lines and find the "Destino:" keyword
         The address ALWAYS starts after "Destino:"
         Everything before it is courier/company header → discard
      │
      ▼
STEP 2 — Collect address lines until a stop keyword is found
         Stop keywords: "Referencia:", "Origen:", "Notas:", "EAD:",
         "FACTURA", "PESO:", "PO:", "TN:", "ST:"
         Everything after stop keywords → discard
      │
      ▼
STEP 3 — Fix OCR character errors on the extracted address only
         KR / Cra / Kr  → normalize to "Cra"
         0 ↔ O, 1 ↔ l
         C.P. + number  → this is a postal code, remove it
         "Colombia"     → keep it, useful for Geocoding
         "Tel:" + number → remove phone number
      │
      ▼
STEP 4 — The result should be:
         "KR 18b # 32 - 06 SUR Quiroga Central,
          Rafael Uribe Uribe, Bogota DC, Colombia"
      │
      ▼
STEP 5 — Send this clean string to Geocoding API
```

---

## What the Final Output Should Look Like

```csv
Name;Address;Latitude;Longitude;Phone;Group;Notes
Package #1;KR 18b # 32 - 06 SUR Quiroga Central Rafael Uribe Uribe Bogota DC Colombia;4.577832;-74.1140209;;; 
```

---

## Summary of Changes for the Prompt

Two concrete updates needed:

**Parser update** — the algorithm must look for `Destino:` as the address start anchor and stop collecting at `Referencia:` / `Origen:` / `EAD:` / `PESO:` / `PO:` / `TN:`. This is far more reliable than trying to detect street-type keywords on a noisy full-label OCR dump.

**CSV format update** — switch separator from `,` to `;` and add the three extra columns `Phone`, `Group`, `Notes` (empty by default) to match Routin's expected format exactly.

Do you want me to update the AI coding prompt document with these two corrections?

Great catch. This is actually one of the most critical parts of the parser — Colombian addresses have no single standard format, and OCR makes it even more unpredictable. Let me map out every variation the parser needs to handle.

---

## The Problem — Same Address, Many Formats

```
All of these are the same place:

KR 18b # 32 - 06 SUR          ← official label format
Carrera 18b # 32 - 06 sur     ← full word
Cr 18b # 32 - 06 sur          ← short abbreviation
Kr 18B 32 06 SUR               ← OCR dropped the # and -
carrera 18 b #32-06 sur        ← spaced letter suffix
CALLE 10 # 43E - 31            ← different street type
Cl 10 #43E-31                  ← abbreviated
CL10 #43E31                    ← no spaces
Cll 10 43 E 31                 ← OCR broke the format
Diagonal 45 # 10 - 20          ← less common type
Dg 45 # 10 - 20
Transversal 8 # 15 - 30
Tv 8 # 15 - 30
Avenida Carrera 30 # 45 - 10   ← compound type
Av Cr 30 # 45 - 10
AC 30 # 45 - 10
```

---

## Complete Abbreviation Dictionary

The parser needs a lookup table that maps every known variant to one canonical form:

### Street types
| Variants | Canonical |
|---|---|
| `Carrera`, `Cra`, `Cra.`, `Cr`, `Cr.`, `KR`, `Kr`, `K` | `Carrera` |
| `Calle`, `Cl`, `Cl.`, `Cll`, `CL`, `Ca` | `Calle` |
| `Avenida`, `Av`, `Av.`, `Ave` | `Avenida` |
| `Avenida Carrera`, `Av Cr`, `Av. Cr.`, `AC`, `AK` | `Avenida Carrera` |
| `Avenida Calle`, `Av Cl`, `Av. Cl.`, `AC` | `Avenida Calle` |
| `Diagonal`, `Dg`, `Dg.`, `Diag` | `Diagonal` |
| `Transversal`, `Tv`, `Tv.`, `Tr`, `Tr.`, `Trans` | `Transversal` |
| `Circular`, `Cir` | `Circular` |
| `Variante`, `Var` | `Variante` |

### Quadrant suffixes
| Variants | Canonical |
|---|---|
| `SUR`, `Sur`, `S`, `sur` | `Sur` |
| `NORTE`, `Norte`, `N`, `norte` | `Norte` |
| `ESTE`, `Este`, `E`, `este` | `Este` |
| `OESTE`, `Oeste`, `O`, `oeste` | `Oeste` |
| `OCCIDENTE`, `Occidente`, `OCC`, `Occ` | `Occidente` |
| `ORIENTE`, `Oriente`, `OR`, `Or` | `Oriente` |

---

## The Normalization Steps for Address Format

After extracting the address from the `Destino:` block, run this specific normalization pipeline:

```
STEP A — Normalize street type
  "KR" → "Carrera"
  "Cr" → "Carrera"
  "Cl" → "Calle"
  etc. (lookup table above)

STEP B — Normalize the # separator
  "32 06"     → "# 32 - 06"   (OCR dropped # and -)
  "32-06"     → "# 32 - 06"   (missing # and spaces)
  "# 32-06"   → "# 32 - 06"   (missing spaces around -)
  "# 3206"    → "# 32 - 06"   (merged numbers, harder case)

STEP C — Normalize letter suffixes on numbers
  "18 b"  → "18b"   (OCR split the letter from number)
  "18 B"  → "18b"
  "43 E"  → "43E"
  "43e"   → "43E"   (uppercase the letter suffix)

STEP D — Normalize quadrant suffix
  "SUR" → "Sur"
  "sur" → "Sur"
  etc. (lookup table above)

STEP E — Rebuild as canonical format
  "{StreetType} {Number}{Letter} # {Cross} - {Door} {Quadrant}"
  → "Carrera 18b # 32 - 06 Sur"
```

---

## Why Canonical Format Matters for Geocoding

The Geocoding API is tolerant but not perfect. Sending a normalized, consistent format improves accuracy significantly:

```
❌ "Kr 18B 32 06 SUR Quiroga Central Bogota"
   → Geocoding might misinterpret or return low confidence

✅ "Carrera 18b # 32 - 06 Sur, Quiroga Central, Bogotá, Colombia"
   → Clean, structured, Geocoding returns precise result
```

---

## Hardest OCR Cases to Anticipate

These are the trickiest patterns the parser must survive:

```
"Cr18b#32-06Sur"        → no spaces at all
"C R 1 8 b # 3 2 - 0 6" → OCR spaced every character
"Cra. 18B No. 32-06"    → "No." instead of "#" (also valid Colombian format)
"Kr 180 3206 SUR"       → OCR read 18b as 180 (b→0 confusion)
"KF 18b # 32 - 06"      → OCR read R as F (R→F confusion)
```

The `No.` → `#` substitution is particularly important — some labels use `No.` as the separator and it's completely valid Colombian address notation.

---

## Updated Parser — Full Flow

```
ML Kit raw text
      │
      ▼
Extract block between "Destino:" and next stop keyword
      │
      ▼
Remove: C.P. + postal code, "Tel:" + phone, "Colombia" (re-added later)
      │
      ▼
Fix OCR character errors (0↔O, 1↔l, F↔R on street type only)
      │
      ▼
Normalize street type → canonical (lookup table)
      │
      ▼
Normalize "No." → "#"
      │
      ▼
Normalize # separator spacing → "# XX - XX"
      │
      ▼
Normalize letter suffixes → attached + uppercase (18b, 43E)
      │
      ▼
Normalize quadrant suffix → Title case (Sur, Norte)
      │
      ▼
Append neighborhood + city + country from remaining Destino block
      │
      ▼
Final clean string → Geocoding API
"Carrera 18b # 32 - 06 Sur, Quiroga Central, Bogotá, Colombia"
```

---

Do you want me to update the full AI coding prompt document now with all of this — the parser corrections, the address normalization dictionary, the CSV format fix, and the `Destino:` anchor strategy?

Done. A few notes on what makes this prompt precise enough to work:

**The real label photo was key.** Without it the prompt would still say "look for street keywords" — the wrong strategy. Now the AI knows `Destino:` is the anchor and exactly which keywords signal the end of the address block.

**The unit tests use real data.** Input 1 is literally the address from your package photo. The AI can't fake a passing test on that — it forces the correct extraction logic.

**The CSV fix includes the BOM detail (`\uFEFF`).** This is the kind of thing that gets missed — Routin needs it to correctly render `Bogotá`, `Medellín` and other accented city names. Without it those characters would show as garbage in the import.

**The two fixes are completely isolated** — the prompt explicitly says don't touch anything else. This prevents the AI from refactoring unrelated code and introducing new bugs while fixing these two.

The full plan is already documented — you have two files from this conversation:Here's how to use them together:

**`ai_coding_prompt.md`** — the main document. Use this as your first message when starting a coding session with any AI. It contains the full app description, tech stack, all 4 screens, the architecture, the Room schema, the 15-step build plan, and the technical constraints.

**`parser_csv_fix_prompt.md`** — a targeted fix prompt. Use this specifically when you reach Step 7 (Address Parser) and Step 13 (CSV Export) in the build plan, or whenever those two components need to be corrected. It supersedes the parser and CSV sections in the main prompt.

**Recommended workflow:**
```
1. Open AI coding session
2. Paste ai_coding_prompt.md → "Let's start with Step 1"
3. Build steps 1 through 6 normally
4. On Step 7, paste parser_csv_fix_prompt.md for the parser rewrite
5. Continue steps 8 through 12 normally
6. On Step 13, paste parser_csv_fix_prompt.md again for the CSV fix
7. Continue through Step 15
```

Good additions. Let me plan both features precisely before writing the prompt.

---

## Feature 1 — Remove Route from History

Simple swipe-to-delete or a delete button on each route card. A confirmation dialog prevents accidental deletions.

```
Route History Screen
┌─────────────────────────────────┐
│ 🗓 Route - Nov 12  │  8 stops  │
│                    │  [ 🗑 ]   │  ← delete button
├─────────────────────────────────┤
│ 🗓 Route - Nov 11  │  5 stops  │
│                    │  [ 🗑 ]   │
└─────────────────────────────────┘

Tap 🗑 → confirmation dialog:
┌─────────────────────────────┐
│  Delete "Route - Nov 12"?   │
│  This cannot be undone.     │
│                             │
│  [ Cancel ]   [ Delete ]    │
└─────────────────────────────┘

Confirm → delete Route + all its Stops from Room
```

---

## Feature 2 — Tappable Address Box → Full Address History Panel

The address list at the top of the camera screen becomes a tappable trigger. When pressed, it expands into a full-screen panel showing all scanned addresses for the current session. The user selects one to remove and confirms via a bottom button.

```
CAMERA SCREEN (normal state)
┌──────────────────────────────────┐
│ ┌────────────────────────────┐   │
│ │ ✓ Calle 10 #43E-31    [↕] │   │ ← tappable, [↕] = expand icon
│ │ ✓ Carrera 45 #52-29       │   │
│ └────────────────────────────┘   │
│         [live camera]            │
│   [ 📷 Scan ]  [ Finish ]        │
└──────────────────────────────────┘

User taps the address box →

ADDRESS HISTORY PANEL (expanded)
┌──────────────────────────────────┐
│  Session Addresses          [ ✕ ]│  ← close button
│──────────────────────────────────│
│  ○ Package #1                    │
│    Calle 10 #43E-31, Medellín    │
│                                  │
│  ● Package #2  ← SELECTED        │
│    Carrera 45 #52-29, Medellín   │
│                                  │
│  ○ Package #3                    │
│    Av. El Poblado #1-20          │
│                                  │
│                                  │
│  ┌────────────────────────────┐  │
│  │     [ 🗑 Remove Selected ] │  │  ← bottom button, disabled
│  └────────────────────────────┘  │    until one is selected
└──────────────────────────────────┘

User taps [ Remove Selected ] →

Confirmation dialog:
┌──────────────────────────────────┐
│  Remove this address?            │
│  Carrera 45 #52-29, Medellín     │
│                                  │
│  [ Cancel ]      [ Remove ]      │
└──────────────────────────────────┘

Confirm → address removed from session list
         panel stays open so user can remove more
         if list becomes empty → panel auto-closes
```

---

Now the prompt:A few decisions worth noting:

**Feature 1 — visible delete button instead of swipe-to-delete.** Swipe gestures are not obvious to all users, especially in a fast-paced delivery environment. A visible trash icon is always discoverable.

**Feature 2 — in-memory removal only.** When the user removes an address from the panel, the stop is only removed from the session list in the ViewModel — not from Room. The route hasn't been saved yet at that point. The Room deletion only happens if the user had already saved the route, which belongs to Feature 1 instead.

**The `BackHandler` detail** is critical — without it, pressing back while the panel is open would navigate away from the camera screen and lose the entire session. The prompt explicitly handles this.

This is a classic Jetpack Compose bug. The cause is almost certainly one of these two things:

**Cause A — Wrong selection condition in the card composable.**
The card is checking `selectedStopId != null` instead of `selectedStopId == stop.id`. So as soon as any card is selected, all cards see a non-null ID and highlight themselves.

**Cause B — Missing `key` in `LazyColumn`.**
Without a `key { stop.id }` on each item, Compose reuses and recomposes cards incorrectly, making the selection state bleed across all items.

Both are one-line fixes. Here's the precise prompt:Short prompt intentionally — this is a surgical fix, not a feature. Two lines of code across two files.

The verification block at the bottom is important — paste it to the AI after the fix is applied so it writes a quick manual test checklist confirming the toggle behavior works correctly in all four scenarios.

I can see the repo structure but GitHub is blocking direct file access. I need you to share the raw file URLs so I can read the code. Here's how to get them:

---

## How to Get the Raw File URLs

For any Kotlin file in your repo, go to the file on GitHub and click the **Raw** button at the top right of the code viewer. That gives you a URL like:

```
https://raw.githubusercontent.com/Robertou65/Router-App/main/app/src/...
```

Paste those raw URLs here and I can read the code directly.

---

## Files I Need to See

To diagnose the crash I specifically need these files:

1. **`CameraScreen.kt`** — the address list container and its `clickable` modifier, this is almost certainly where the crash originates
2. **`CameraViewModel.kt`** — the `openSessionPanel()` function and `SessionPanelState`
3. **`CameraViewModel.kt`** or wherever `sessionStops` is defined as a `StateFlow`

---

## Most Likely Crash Causes (while you fetch those)

Based on the symptom (crash on tap), the three most common causes are:

**Cause A — `sessionStops` is null when the panel tries to render**
The panel composable collects `sessionStops` but it hasn't been initialized yet, causing a `NullPointerException` on first tap.

**Cause B — `openSessionPanel()` triggers a state update that causes an illegal composable call**
A state change inside a `clickable` lambda that directly calls a suspend function instead of going through the ViewModel.

**Cause C — `BackHandler` not wrapped in a condition**
If `BackHandler` is registered unconditionally (not inside `if (panelState.isOpen)`), it can conflict with the navigation stack and crash on certain Android versions.

Share the raw file links and I'll pinpoint the exact line.

I found the crash. I can see it clearly in the code. Here's exactly what's happening:

---

## Root Cause — Duplicate Keys in LazyColumn

In `CameraViewModel.kt`, every in-memory session stop is created with `id = 0` because Room's `autoGenerate` only assigns a real ID **on database insert**, which hasn't happened yet at this point:

```kotlin
// CameraViewModel.kt — all session stops get id = 0
val stop = Stop(
    routeId = 0L,   // ← id not set, defaults to 0
    label = "Package #...",
    ...
)
```

Then in `SessionAddressPanel` inside `CameraScreen.kt`, the `LazyColumn` uses `stop.id` as the unique key:

```kotlin
// CameraScreen.kt
itemsIndexed(sessionStops, key = { _, item -> item.id }) { _, stop ->
```

The moment the panel opens with 2 or more scanned stops, **every stop has `id = 0`**. Android throws:

```
IllegalArgumentException: Key 0 was already used.
If you are using LazyColumn/Row please make sure you provide
a unique key for each item.
```

The app crashes.

---

## The Fix — Two lines across two files

**`CameraViewModel.kt`** — assign a unique temporary ID to each in-memory stop using its position:

```kotlin
// BEFORE
val stop = Stop(
    routeId = 0L,
    label = "Package #${_sessionStops.value.size + 1}",
    ...
    order = _sessionStops.value.size + 1,
)

// AFTER — add id as a negative unique value so it never collides with Room IDs
val stop = Stop(
    id = -(_sessionStops.value.size + 1L),  // ← add this line
    routeId = 0L,
    label = "Package #${_sessionStops.value.size + 1}",
    ...
    order = _sessionStops.value.size + 1,
)
```

Negative IDs are used as temporary session-only identifiers. Room never generates negative IDs, so there's zero collision risk. When the route is saved, `SaveExportScreen` already calls `stop.copy(routeId = routeId)` which resets the ID correctly on insert.

**`CameraScreen.kt`** — as a safety net, also change the key to use `order` which is already unique per session:

```kotlin
// BEFORE
itemsIndexed(sessionStops, key = { _, item -> item.id }) { _, stop ->

// AFTER
itemsIndexed(sessionStops, key = { _, item -> item.order }) { _, stop ->
```

---

## Why `order` as key instead of fixing `id` only

Using both fixes together is safer. `order` is always unique and stable within a session, so even if the ID logic changes in the future, the `LazyColumn` key remains correct.

---

## Verification

After the fix:
- Scan 1 stop → open panel → no crash
- Scan 3 stops → open panel → all 3 cards render correctly
- Remove a stop → panel stays open, remaining stops reorder correctly
- Scan again after removal → new stop gets correct negative ID (-2, -3, etc.)


Two lines, two files, crash gone. Quick summary of why it's safe:

**The negative ID trick** works because Room's autoGenerate only ever produces positive Longs. The `-1L`, `-2L`, `-3L` temporary IDs exist only in memory during the scanning session and are never written to the database — `SaveExportScreen` already discards them on insert via `stop.copy(routeId = routeId)`.

**The `order` key as backup** means even if someone refactors the ID logic later, the `LazyColumn` still has a stable unique key to work with.

Good, I can see the full current code. Let me plan the changes precisely before writing the prompt.

**What needs to change across 6 files:**

- `Routes.kt` — add optional `routeId` param to Camera route
- `RouterNavHost.kt` — Camera accepts optional `routeId`, SaveExport reads it, RouteDetail gets an "Add Stops" nav action
- `CameraViewModel.kt` — store `existingRouteId` so SaveExport knows whether to create or append
- `CameraScreen.kt` — receive and forward `routeId` to ViewModel
- `SaveExportScreen.kt` — two modes: create new route vs append to existing
- `RouteDetailScreen.kt` — add "Add More Stops" buttonA few important details in this prompt worth noting:

**The `id = 0L` reset on insert** is the critical line in the edit mode save logic. Your session stops currently have negative temporary IDs (-1L, -2L, etc.) from the crash fix. Room's `autoGenerate` treats `0L` as "please generate a new ID" — if you try to insert a stop with `-1L`, Room will try to use that as the literal ID and it could conflict or behave unexpectedly. Resetting to `0L` before insert guarantees a clean auto-generated positive ID every time.

**The `startOrder` offset** ensures new stops continue from where the existing ones left off. If a route already has 3 stops, the new scanned stops get `order = 4, 5, 6...` instead of starting back at 1.

**The back stack try/catch** in `RouterNavHost` handles the ambiguity between new-route and edit-route flows sharing the same `SaveExportScreen` destination.

Looking at the current code, the change is clean and contained. The manual address flow reuses the existing `ScanState` machine — it just skips OCR and the parser and goes straight to `Requesting`. Only two files need changes.Two files, minimal changes. A few things worth noting:

**The parser is intentionally skipped.** When a user types an address manually, they're already writing it in a clean, readable format. Running it through the `AddressParser` would risk mangling a correctly typed address. The string goes directly to the Geocoding API.

**The dialog closes before calling `onManualAddress`.** This is the correct order — if you call the ViewModel function first and then close the dialog, Compose might show a brief flash of the dialog while the loading overlay is already showing. Closing first keeps the transition clean.

**`manualAddressText` is reset on open, not on close.** This prevents the previous failed address from being invisible in the background while the dialog is closed — if the user reopens it, they start fresh every time.

Good questions. Let me read the code carefully before writing the prompt.Good. I have the full codebase from the documents. Let me analyze both things before writing the prompt.

**Performance issues I can see in the code:**

1. `GeocodingRepository` — no cache. If the user scans the same address twice, it makes two identical API calls.
2. `SaveExportScreen` and `RouteDetailScreen` — both write CSV files to `context.cacheDir` but **never clean them up**. Every export adds a file that lives forever.
3. `RouteDetailViewModel` — uses a one-time `suspend` load, not a `Flow`. After deleting a stop, it needs to manually reload.

**Remove stop from RouteDetail** requires: `deleteById` in `StopDao`, a new ViewModel method, and a delete button with confirmation dialog on each stop card.Here's what each improvement targets and why it matters for this app specifically:

**Geocoding cache** — the delivery scenario is the key reason this matters. A driver scanning 20 packages for the same apartment building will scan the same "Carrera 45 # 10-20, Chapinero, Bogotá" label repeatedly. Without the cache, that's 20 API calls. With it, it's 1. The LRU approach (using `LinkedHashMap` in access-order mode) means the 50 most-recently-used addresses stay hot, which fits the delivery pattern perfectly.

**CSV cache cleanup** — this is the thing mentioned early in the conversation about `cacheDir`. Every time the user exports, a new `.csv` file is written to cache but the old ones are never deleted. After a few weeks of daily use, the cache directory fills up with hundreds of stale route files. The 1-hour cutoff is conservative — the file is only needed for the duration of the share sheet interaction, which takes seconds.

**Remove stop from Route Detail** — the `deletingStopIds` Set pattern is the same one already used in `RouteHistoryViewModel` for route deletion, so it's consistent with the existing codebase.

Great idea, and honestly the right call for this specific problem. Here's my honest assessment:

---

## Why AI Parsing Makes Sense Here

The core problem with the rule-based `AddressParser.kt` is that **you're fighting a combinatorial explosion**. Colombian address formats, OCR character errors, label designs from dozens of different courier companies, smudges, rotated text — the number of edge cases is effectively infinite. Every new label format that breaks the parser requires a code update and a new release.

An AI model solves this at the root. You describe what you want in natural language once, and the model generalizes across every format variation automatically.

---

## The Tradeoff Is Minimal for This App

| Concern | Reality |
|---|---|
| Requires internet | Already required — Geocoding API won't work offline either |
| Adds latency | ~1s extra per scan, acceptable in a scanning workflow |
| API cost | Tiny — a label OCR dump is ~200 tokens. At Claude's pricing, thousands of scans cost pennies |
| API goes down | Same risk as Google Maps API — if internet is gone, the whole flow stops anyway |

---

## The Right Tool: Anthropic API (Claude)

Since you're already in the Anthropic ecosystem and your `ai_coding_prompt.md` already references the Anthropic API, using Claude directly is the cleanest path. The implementation is simple — one API call with a tight system prompt, replacing `AddressParser.kt` entirely.

The new flow:

```
ML Kit OCR → raw text
      │
      ▼
Claude API  ←── system prompt: "extract delivery address from this Colombian package label OCR text"
      │
      ▼
Clean address string
      │
      ▼
Google Geocoding API → coordinates
```

---

## What the System Prompt Looks Like

The prompt is the entire "algorithm." Something like:

```
You are an address extraction tool for Colombian package delivery labels.
The input is raw OCR text scanned from a physical package label.
It contains noise: barcodes, weights, tracking numbers, sender info, reference notes.

Your task: extract ONLY the recipient delivery address.
The delivery address always follows the keyword "Destino:" if present.
Output ONLY the clean address string. No explanation, no punctuation around it.
If no address is found, output: ADDRESS_NOT_FOUND

Examples of valid Colombian address formats:
- Carrera 18b # 32 - 06 Sur, Quiroga Central, Bogotá
- Calle 10 # 43E - 31, El Poblado, Medellín
- Av Carrera 30 # 45 - 10, Teusaquillo, Bogotá
```

That's it. No regex, no lookup tables, no normalization steps. Claude handles every OCR error and format variation on its own.

---

## Implementation Plan

Three files change, everything else stays identical:

**Replace** `AddressParser.kt` with `AiAddressExtractor.kt` — an async function that calls the Anthropic API and returns a clean address string or an error.

**Update** `CameraViewModel.kt` — replace the `addressParser.parse(text)` call with `aiAddressExtractor.extract(text)`. The state machine (`Scanning → Requesting → Success/Failure`) stays exactly the same.

**Add** the Anthropic API key to `local.properties` and `BuildConfig`, same pattern as the Google Maps key already there.

`AddressParser.kt` and `AddressParserTest.kt` are deleted entirely.

---

## One Practical Suggestion

Keep the Geocoding API call as-is. Don't ask Claude to also return coordinates — let each service do what it's best at. Claude extracts and cleans text, Google Maps resolves geography. Clean separation.

---

Want me to write the full implementation prompt for this? It would replace the parser section of your main prompt and give the AI coder the exact Anthropic API call, system prompt, error handling, and the `CameraViewModel` wiring needed.

Great technical question. The honest answer is: **yes it's possible, but the hardest part isn't the model — it's the data.**

---

## How It Would Work on Android

The standard stack for on-device ML on Android is **TensorFlow Lite (TFLite)**. You'd train a model on a server, export it to TFLite format, bundle it in the APK, and run inference locally with no internet needed.

```
Training (server, once)          Inference (on-device, every scan)
──────────────────────           ──────────────────────────────────
Labeled dataset                  Raw OCR text
      │                                │
      ▼                                ▼
Train model (GPU)               TFLite model (bundled in APK)
      │                                │
      ▼                                ▼
Export to .tflite              Clean address string
Bundle in APK                  ~50-200ms, no internet
```

---

## The Real Barrier: Training Data

This is where most on-device ML projects fail. To train a model that reliably extracts Colombian delivery addresses from noisy OCR text, you need:

```
Minimum viable dataset:
  ~1,000 labeled examples        ← bare minimum, results will be fragile
  ~5,000 labeled examples        ← decent generalization
  ~20,000+ labeled examples      ← production-quality

Each example = {
  input:  raw OCR dump from a real package label
  output: the correct clean address string
}
```

Where does that data come from? You'd have to **manually photograph and label** real package labels. That's months of work before you write a single line of model code. This is the genuine bottleneck — not the ML itself.

---

## Model Options (Realistic for Mobile)

### Option A — Fine-tune a small BERT (best quality)
Use `bert-tiny` or `distilmBERT`, fine-tune it as a sequence labeling model (marking which tokens belong to the address), convert to TFLite with int8 quantization.

| Property | Value |
|---|---|
| Model size in APK | ~20–40MB |
| Inference time | ~100–200ms |
| Training expertise needed | High |
| Data needed | ~5,000+ examples |

### Option B — BiLSTM + CRF (lighter, simpler)
Classic NER architecture, faster to train, smaller model, but less accurate on messy OCR input.

| Property | Value |
|---|---|
| Model size in APK | ~5–10MB |
| Inference time | ~30–80ms |
| Training expertise needed | Medium |
| Data needed | ~2,000+ examples |

### Option C — Gemini Nano via MediaPipe (no training needed)
Google's on-device LLM, available on Pixel 8+ and some other flagship devices. No training required — you prompt it like an API. Zero data collection needed.

| Property | Value |
|---|---|
| Model size | Already on device (not in APK) |
| Inference time | ~500ms–2s |
| Training expertise needed | None |
| Data needed | None |
| Downside | Only works on compatible devices (Pixel 8+, some Samsung) |

---

## Honest Comparison for Your App

```
                    Custom TFLite    Gemini Nano    Claude API
─────────────────────────────────────────────────────────────
Works offline            ✅              ✅              ❌
No API cost              ✅              ✅              ❌ (tiny)
Works all devices        ✅              ❌              ✅
Training data needed     ✅ (a lot)      ❌              ❌
ML expertise needed      ✅ (high)       ❌              ❌
Time to implement        Months         Weeks           Days
Handles new formats      Retrain needed  ✅              ✅
Quality on messy OCR     Depends on data High            Very high
```

---

## My Honest Recommendation

For where your app is right now, training a custom model is premature. The data collection alone would take longer than the rest of the app development combined.

The **practical path forward** depends on one question:

**Is offline support a hard requirement?**

```
Offline required?
      │
      ├── NO  ──► Use Claude API
      │           Simple, high quality, handles any format,
      │           costs pennies at delivery-driver scale
      │
      └── YES ──► Use Gemini Nano (MediaPipe LLM Inference)
                  No training, no data collection, runs on-device,
                  but only on compatible flagship phones
                  (check if your target devices support it)
```

If in the future you reach **thousands of real labeled examples** from actual usage of the app (users correcting bad parses = automatic labeling), then training a custom TFLite model becomes genuinely worthwhile. That's actually a common pattern — ship the API version first, collect corrections as implicit training data, train the local model later.

Want me to plan the Gemini Nano integration via MediaPipe, or go ahead with the Claude API approach?

I can see the bug immediately in `SaveExportScreen.kt`. It's the same `id` issue from before but in a different place.

---

## Root Cause

In the create mode save logic, the `stop.copy()` call preserves the negative temporary session IDs (`-1L`, `-2L`, `-3L`) when inserting into Room:

```kotlin
// CURRENT — keeps negative id, Room tries to insert with id = -1, -2, -3
val stopsToInsert = stops.mapIndexed { index, stop ->
    stop.copy(routeId = routeId, order = index + 1)
}
```

The first save works fine because `-1`, `-2`, `-3` don't exist yet. But the **second route** tries to insert stops with the same `-1`, `-2`, `-3` IDs → **SQLite UNIQUE constraint violation → crash.** It also crashes on the first save if the user removed any stops from the session panel first (leaving gaps like `-1`, `-3`).

The fix is one word: add `id = 0L`. Room's `autoGenerate` treats `0L` as "generate a new ID" — exactly what the edit mode already does correctly.

---One line fix: add `id = 0L` to the `stop.copy()` call in the create mode save block.

This is the same root cause as the LazyColumn crash from earlier — the negative temporary IDs that were introduced to fix that bug need to be reset to `0L` before any Room insert. The edit mode already handled it correctly with `id = 0L`, but the create mode was missed. The first route saved would appear to work because SQLite happily stores `-1`, `-2`, `-3` as literal primary keys — the crash only surfaces on the second save attempt when those IDs already exist in the database.


Great plan. DigitalOcean is a solid choice for this, and the student credits make it essentially free for several months. Let me break down exactly what makes sense here.

---

## The Setup That Works on Your Budget

**Droplet recommendation: Basic 2 vCPU / 4GB RAM / $24/month**

Your $200 credit = **~8 months of runtime** before you pay anything.

GPU droplets would be much faster but would burn through $200 in 3-5 days. CPU inference on a small model is the right call here — the task is simple enough that it doesn't need GPU speed.

---

## Model Recommendation: Not Gemma 4

Gemma 4 is too large for a 4GB CPU droplet. For this specific task on that hardware, the right choices are:

| Model | RAM needed | CPU inference speed | Quality for address extraction |
|---|---|---|---|
| `qwen2.5:0.5b` | ~600MB | ~1-2s | Good |
| `gemma3:1b` | ~800MB | ~2-3s | Very good |
| `llama3.2:1b` | ~800MB | ~2-3s | Very good |
| `gemma3:4b` | ~3.5GB | ~15-30s | Overkill, too slow |

**Go with `gemma3:1b` or `qwen2.5:0.5b`.** For extracting an address from 200-300 tokens of OCR text, a 1B model with a good prompt is more than sufficient. You don't need 4B parameters for this.

---

## Architecture

```
Android App
     │
     │ POST /extract-address
     │ { "ocr_text": "raw label text..." }
     ▼
DigitalOcean Droplet
  ┌─────────────────────────┐
  │  FastAPI (Python)       │  ← thin wrapper, handles auth
  │       │                 │
  │  Ollama (local)         │  ← runs gemma3:1b on CPU
  │       │                 │
  │  Returns clean address  │
  └─────────────────────────┘
     │
     │ { "address": "Carrera 18b # 32-06 Sur, Bogotá" }
     ▼
Android App → sends to Geocoding API as before
```

The app's existing `Retrofit` client already handles HTTP — adding one more endpoint is minimal work. The rest of the scan flow (`ScanState` machine, Geocoding API, green/red overlay) stays identical.

---

## What Changes in the App

**`AddressParser.kt`** — deleted entirely.

**New file: `AiAddressExtractor.kt`** — replaces the parser. Makes a `POST` to your DigitalOcean endpoint instead of running local regex logic.

**`CameraViewModel.kt`** — one line change: `addressParser.parse(text)` becomes `aiExtractor.extract(text)`. Everything else stays the same.

**`local.properties`** — add your DigitalOcean server URL and a secret API key (same pattern as the Google Maps key).

---

## Server Stack (Simple)

On the droplet you run two things:

**Ollama** — handles model download, management, and local inference. Has its own REST API built in.

**FastAPI (Python)** — a thin wrapper that:
- Validates your secret API key header (prevents strangers from using your server)
- Sends the OCR text to Ollama with your system prompt
- Returns just the clean address string

The system prompt on the server is exactly what was discussed earlier — describe the label structure, the `Destino:` keyword, the Colombian address formats, and instruct the model to return only the clean address string.

---

## Cost Projection

```
$200 student credit
÷ $24/month (2vCPU / 4GB RAM droplet)
= ~8.3 months free

After credits run out:
$24/month ongoing — cheap for a production service
```

If usage grows you can upgrade the droplet size later. A 4vCPU/8GB droplet ($48/month) would roughly halve inference time.

---

## One Thing to Plan For

The droplet needs a **domain or static IP + HTTPS**. Android blocks plain HTTP calls by default (cleartext traffic policy). DigitalOcean makes this straightforward — you can attach a free Let's Encrypt SSL certificate through their managed load balancer, or use Caddy on the droplet which handles SSL automatically.

---

## Summary

```
What you need to set up:
  1. DigitalOcean droplet (2vCPU / 4GB RAM)
  2. Install Ollama → pull gemma3:1b
  3. Write a ~40-line FastAPI server with auth + system prompt
  4. Set up HTTPS (Caddy is the easiest)
  5. Add the server URL + API key to local.properties
  6. Replace AddressParser with AiAddressExtractor in the app
  7. Delete AddressParser.kt and AddressParserTest.kt
```

Want me to write the implementation prompt for both the server (FastAPI + Ollama setup) and the Android side (AiAddressExtractor + CameraViewModel update)?

Good. Click **Droplet** — that's the one you need. Follow these steps:

---

## Step 1 — Create the Droplet

**Choose Region**
Pick the region closest to Colombia — **New York** or **Toronto** will give the best latency for your users.

**Choose an image**
Select **Ubuntu 24.04 LTS** — most stable, best Ollama support.

**Choose Size**
- Click **Basic** plan
- CPU options → select **Regular** (not Premium)
- Pick **2 vCPU / 4GB RAM / 80GB SSD → $24/month**

This is the minimum for running `gemma3:1b` comfortably. Don't go lower.

**Authentication**
Choose **SSH Key** over password — more secure and easier to work with. If you don't have one set up yet, DigitalOcean has a button to generate one during this step.

**Hostname**
Name it something like `router-app-ai` so you can identify it easily.

Click **Create Droplet** and wait ~60 seconds for it to provision.

---

## Step 2 — Note Your Droplet's IP

Once created, the dashboard shows your droplet's **public IP address**. Copy it — you'll need it for everything that follows.

---

## Step 3 — Connect via SSH

Open your terminal and run:

```bash
ssh root@YOUR_DROPLET_IP
```

Once you're connected and see the Ubuntu prompt, come back here and we'll install Ollama and set up the server.

---

The next steps after you're connected will be:
1. Install Ollama + pull `gemma3:1b`
2. Install Python + FastAPI
3. Write the address extraction server (~40 lines)
4. Set up Caddy for HTTPS
5. Configure it to auto-start on reboot

Let me know when you're in.

Simple answer: **cost vs. benefit for this specific task.**

---

## The Math

```
CPU Droplet (2vCPU / 4GB RAM)
$24/month
$200 credit → ~8 months free

GPU Droplet (cheapest H100 option)
~$2.50–$10/hour depending on configuration
$200 credit → 20 hours to 3 days of runtime
```

Your $200 disappears in less than a week on a GPU droplet.

---

## The Speed Difference Doesn't Matter Here

Yes, GPU inference is ~10-20x faster. But look at the actual numbers for your use case:

```
Input:  ~300 tokens (OCR label text)
Output: ~20 tokens (just the address)

CPU inference (gemma3:1b):   ~2-3 seconds
GPU inference (gemma3:1b):   ~0.1-0.2 seconds
```

That 2-3 second difference is **invisible to the user** because the full scan flow already takes 4-6 seconds anyway:

```
User taps Scan
    │
    ├── ML Kit OCR:          ~0.5s
    ├── Network to server:   ~0.3s (round trip)
    ├── AI extraction:       ~2-3s  ← the difference
    ├── Network back:        ~0.3s
    └── Geocoding API:       ~0.5s
                             ──────
Total:                       ~4-5s
```

Whether the AI part takes 0.1s or 3s, the user experience feels identical. The camera overlay says "Reading address..." either way.

---

## When a GPU Droplet Makes Sense

- High-traffic production app with hundreds of simultaneous users
- Real-time streaming applications
- Large models (7B+ parameters) where CPU would take 30+ seconds
- You have a budget that supports $500-2000+/month

None of those apply to your app right now. Go with the CPU droplet, get 8 months free, and revisit when you have real user traffic data to justify the cost.

First, let's lock down root SSH access, then install Ollama.

---

## Step 1 — Disable Root SSH Login

```bash
sudo nano /etc/ssh/sshd_config
```

Use `Ctrl+W` to search for `PermitRootLogin`. Change the line to:

```
PermitRootLogin no
```

Save with `Ctrl+X` → `Y` → `Enter`. Then restart SSH:

```bash
sudo systemctl restart sshd
```

---

## Step 2 — Install Ollama

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

This takes about 30 seconds. When it finishes, verify it's running:

```bash
ollama --version
```

---

## Step 3 — Pull the Model

```bash
ollama pull gemma3:1b
```

This downloads ~800MB. You'll see a progress bar. It takes 1-3 minutes depending on DigitalOcean's network speed.

When it finishes, do a quick test to confirm it works:

```bash
ollama run gemma3:1b "Say only the word: working"
```

It should respond with something like `working`. Type `/bye` to exit the chat.

---

Let me know when the model responds and we'll move to setting up the FastAPI server.

**Create an independent project.** Here's why:

---

## The Problem With Putting Backend in the Android Project

```
Router-App/                    ← Android project
├── app/
│   ├── src/
│   └── build.gradle.kts
├── backend/                   ← ❌ bad idea
│   ├── main.py
│   └── requirements.txt
├── build.gradle.kts
└── settings.gradle.kts
```

- Android Studio doesn't understand Python files — it will show errors and warnings for everything in `backend/`
- Gradle might try to process files it shouldn't
- Deploying to the server means pushing your entire Android codebase including compiled APKs, Gradle cache, and build artifacts — messy and insecure
- Your `.gitignore` is configured for Android, not Python — secrets could leak

---

## The Right Structure — Two Separate Repos

```
GitHub
├── Router-App/               ← Android project (existing)
│   ├── app/
│   └── build.gradle.kts
│
└── router-app-backend/       ← New independent Python project
    ├── main.py
    ├── requirements.txt
    ├── .env                  ← API key (git ignored)
    ├── .gitignore
    └── README.md
```

**Benefits:**
- Deploy only the backend to DigitalOcean — Android source code never touches the server
- Independent version control — a broken backend commit doesn't affect the Android app
- Clean separation — a future developer (or you in 6 months) immediately understands what each repo does
- Easier to add a second server later without touching Android code

---

## On the Server the Structure Is Even Simpler

```
/home/appuser/
└── router-app-backend/
    ├── main.py
    ├── requirements.txt
    └── .env
```

You deploy by pulling from GitHub directly onto the droplet. No Android files, no Gradle, no APKs — just the Python server.

---

Create the new repo on GitHub, clone it on the droplet, and tell me when it's ready. Then we'll write the FastAPI server.
