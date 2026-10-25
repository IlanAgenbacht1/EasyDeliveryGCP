# EasyDelivery driver app (API edition)

Android proof-of-delivery app for drivers, rebuilt against the EasyDelivery Cloud API on Google Cloud. A driver signs in on an enrolled device, claims a trip, and at each drop scans the parcels, captures a signature and photo, and completes the delivery. Everything works without signal and syncs when the connection comes back.

This is the second generation of the driver app. The first ([EasyDelivery](https://github.com/IlanAgenbacht1/EasyDelivery)) synced through Dropbox folders. This version replaces that with a REST API and a proper offline queue, and moves the codebase from static globals and a hand-written SQLite layer to MVVM.

## Architecture

```
Activities ─► ViewModels (LiveData, SavedStateHandle) ─► Repositories
                                                          │
                                   ┌──────────────────────┴───────────┐
                                   ▼                                  ▼
                       Room (local source of truth)        Retrofit ─► Cloud API
                       trips, deliveries, sessions,                (Cloud Run)
                       devices, offline actions
                                   │
                                   ▼
                       SyncWorker (WorkManager) drains the
                       offline action queue with retries
```

- **MVVM with Hilt.** ViewModels own the state that used to live in static constants; `SavedStateHandle` restores it after process death. Hilt modules provide the database, network client and WorkManager.
- **Offline first.** Delivery changes are saved to Room first and queued as `OfflineActionEntity` rows. A Hilt-injected `SyncWorker` replays the queue against the API, retrying each action up to a limit before giving up and logging it.
- **Device enrolment and sessions.** The app validates a company key, registers the device, and runs PIN-based driver sessions through `auth/*` endpoints, so trips are tied to a known device and driver.
- **Trip lifecycle over REST:** `trips/my` → `claim` → `start` → `deliveries/{id}/complete` → `complete`.
- **Capture:** ZXing parcel scanning, signature pad and photos. Sensitive values live in `EncryptedSharedPreferences`.

## Stack

Java, Android SDK 34, MVVM, Hilt, Room, WorkManager, Retrofit and OkHttp, Firebase Storage, ZXing, AndroidX Security.

## Building

The API base URL is set as a `BuildConfig` field in `app/build.gradle`. Put `google-services.json` for your Firebase project in `app/`, then open in Android Studio and run.

## Looking back

This version loaded a Google service-account key from the app's assets to reach Cloud Storage. Any credential inside an APK can be extracted, so cloud access belongs behind the API, which already authenticates the device and the driver. The app shouldn't hold cloud credentials at all.
