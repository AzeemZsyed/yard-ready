# Yard Ready 🌱❄️

An Android app that turns the weather forecast into yard-work advice: **the best time to mow the lawn**, **when to clear snow**, and **alerts before significant snowfall**.

Built with Kotlin, OpenWeatherMap, Room (SQLite), WorkManager and Firebase, with JUnit and Espresso tests.

## Features

- **Best time to mow**: scans the 5-day / 3-hour forecast for a daytime window that is dry, calm and mild, and skips slots where the grass is still wet from rain in the previous 12 hours. It favours midday, after the dew dries.
- **Knows when you last mowed**: log a mow with one tap and the app holds off until the lawn is due again.
- **Snow outlook**: groups snowy forecast slots into storms, totals the expected accumulation for the next 24 and 48 hours, and suggests the best daytime window to clear once the snow stops. Heavy storms (10 cm or more) also get a mid-storm clearing suggestion.
- **Snowfall alerts**: a background worker checks the forecast every 6 hours and sends one notification per storm when the expected snow passes your threshold (adjustable from 1 to 20 cm).
- **Maintenance history**: every mow and snow clearing is stored locally in SQLite, with 12-month stats and swipe to delete.
- **Optional cloud backup**: history syncs to Firebase Firestore using anonymous auth, if a Firebase project is configured.
- Material 3 design with dark mode, pull to refresh, and city search through OpenWeatherMap geocoding.

## Architecture

```
ui/            Activities + ViewModels (StateFlow), ViewBinding, RecyclerView adapters
domain/        RecommendationEngine: pure Kotlin, no Android code, fully unit-tested
data/
  remote/      Retrofit API for OpenWeatherMap + DTO -> domain mapping
  local/       Room database: entities, DAOs with SQL queries
  sync/        Firebase Firestore backup (no-op when Firebase isn't configured)
work/          WorkManager job: periodic forecast check -> snowfall notification
ServiceLocator Simple dependency injection; tests swap in fakes
```

All decision logic lives in `RecommendationEngine`, so the rules for mowing and snow clearing are tested on the JVM in milliseconds, without an emulator.

### Relational data model (Room / SQLite)

```
locations                     maintenance_logs                    snow_alerts
─────────                     ────────────────                    ───────────
id            PK  ◄───────┬── location_id  FK (cascade)           id           PK
name                      │   id           PK                     location_id  FK (cascade)
region, country           │   task_type    MOW | CLEAR_SNOW       event_start  UNIQUE(location_id, event_start)
latitude, longitude       │   completed_at                        expected_cm
is_selected               │   notes, synced                       notified_at
                          └──────────────────────────────────────────────┘
```

Example query (per-task statistics for the history screen):

```sql
SELECT task_type AS taskType, COUNT(*) AS count, MAX(completed_at) AS lastCompletedAt
FROM maintenance_logs
WHERE location_id = :locationId AND completed_at >= :since
GROUP BY task_type
```

The unique index on `snow_alerts (location_id, event_start)` together with `INSERT OR IGNORE` guarantees that each storm triggers only one notification, even though the background check runs every 6 hours.

## Setup

1. Get a free API key from [OpenWeatherMap](https://home.openweathermap.org/api_keys). New keys can take up to an hour to activate.
2. Add it to `local.properties` in the project root (see `local.properties.example`):
   ```
   OPENWEATHER_API_KEY=your_key_here
   ```
3. Open the project in Android Studio and run the `app` configuration (min SDK 26).

**Optional, Firebase backup:** create a Firebase project, add an Android app with package `com.azeemzsyed.yardready`, enable **Anonymous** sign-in and **Cloud Firestore**, and place `google-services.json` in `app/`. The Google Services plugin is applied automatically when that file exists. Without it, the app works fully offline-first.

## Tests

```bash
./gradlew testDebugUnitTest        # JVM unit tests: recommendation engine + API mapping
./gradlew connectedDebugAndroidTest # needs an emulator/device: Room DAO + Espresso UI tests
```

| Suite | What it covers |
|---|---|
| `RecommendationEngineTest` (21 tests) | mowing windows, wet-grass rule, rain/wind/temperature limits, off-season, mow interval; snow event grouping, 24/48 h totals, alert threshold, clearing time, mid-storm pass; daily summaries |
| `ForecastMapperTest` | OpenWeatherMap JSON DTOs to domain model (missing fields, sorting, time zones) |
| `MaintenanceDaoTest` | SQL queries, GROUP BY stats, newest-first ordering, cascade deletes, single selected location, one alert per storm |
| `MainActivityTest` (Espresso) | welcome screen, mowing recommendation, "Mark as mowed" updating advice and history, snowfall alert banner, network error and retry |
| `SettingsActivityTest` (Espresso) | searching for a city and saving it as the selected location |

UI tests use a `FakeWeatherRepository` and a fixed `Clock`, so they are deterministic and never call the network.

## Tech
Kotlin · Coroutines/Flow · Android Jetpack (ViewModel, Room, WorkManager, ViewBinding) · Material 3 · Retrofit/OkHttp/Gson · OpenWeatherMap API · Firebase Auth + Firestore · JUnit 4 · Espresso · GitHub Actions
