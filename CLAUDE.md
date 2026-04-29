# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew :app:testDebugUnitTest --tests "com.example.todoapp.YourTestClass"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest

# Lint check
./gradlew lint

# Install on connected device
./gradlew installDebug
```

## Architecture

This is a Jetpack Compose Android app. The PRD lives in `prd/daily-todo-app-prd.md` and defines the full feature set — read it before implementing anything.

**Prescribed stack (from PRD §6):**
- Single Activity, single screen + history overlay (no multi-Activity navigation)
- ViewModel + StateFlow for state management
- Room database with two tables: `daily_tasks` (active, resets nightly) and `task_history` (past days, 30-day retention)
- Hilt for dependency injection
- `AlarmManager` with `setExactAndAllowWhileIdle` for nightly reminder and midnight reset
- `BroadcastReceiver` to re-register alarms after device reboot
- No network layer

**Key product constraints (non-obvious):**
- No task carry-over between days — this is a deliberate UX principle, not an oversight
- Midnight reset must work while the app is open (handle via coroutine/Flow observing date change)
- Nightly reminder fires only if the task list is empty at reminder time
- History viewer is a bottom sheet overlay, not a separate screen/destination
- Permissions (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`) are requested at first relevant use, not on launch

**minSdk is 24 in `build.gradle.kts` but the PRD targets API 26 minimum** — align to API 26 before shipping.

## Dependency Management

All versions are declared in `gradle/libs.versions.toml`. Hilt, Room, and other libraries needed per the PRD are not yet added — add them there before referencing in `app/build.gradle.kts`.
