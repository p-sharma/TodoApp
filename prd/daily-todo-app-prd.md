# Product Requirements Document
## Daily Intentional TODO — Android App

**Version:** 1.1  
**Status:** Draft  
**Last Updated:** April 27, 2026  

---

## 1. Overview

### 1.1 Product Summary

Daily Intentional TODO is a minimalist Android app that helps users plan their day with purpose. The core philosophy is **intentionality** — users must manually re-enter their tasks every single day, preventing mindless copy-paste habits and encouraging active reflection on what truly matters today.

### 1.2 Problem Statement

Most TODO apps accumulate clutter over time. Tasks carry over indefinitely, lists grow long and stale, and users stop engaging meaningfully with what they need to do. This app solves that by enforcing a clean slate every day.

### 1.3 Target Audience

Individuals who want a simple, focused daily planning habit — without the bloat of project management tools.

### 1.4 Design Principles

- **One screen. One day.** Everything happens in a single view.
- **No carry-over.** Yesterday's tasks are gone from the main view. Always.
- **Manual re-entry is the feature.** Friction here is intentional.
- **No onboarding friction.** First launch drops the user straight into the app.

---

## 2. Scope

### 2.1 In Scope (v1)

- Daily task list with add, edit, complete, reorder, and delete
- Configurable task limit per day
- Midnight daily reset with silent history save
- Nightly reminder notification (conditional on empty list)
- Configurable reminder time
- History viewer (read-only, past days only)

### 2.2 Out of Scope (v1)

| Feature | Reason |
|---|---|
| Copy/import tasks from previous day | Core product principle |
| Home screen widget | Post-launch |
| Cloud sync or backup | Local only for v1 |
| Multiple lists or categories | Single-focus philosophy |
| Recurring tasks | Conflicts with daily reset |
| Collaboration or sharing | Not in target use case |
| Onboarding flow | Intentionally skipped |

---

## 3. Functional Requirements

### 3.1 Main Screen

The app has a single main screen. It shows today's date at the top, the task list in the middle, and a fixed input bar at the bottom.

**FR-01 — Screen layout**
- Display today's date prominently at the top (e.g. "Monday, April 27")
- Show the task list below the date header
- A fixed input bar with a text field and an "Add" button sits at the bottom of the screen
- If the list is empty, show a centered prompt: *"What do you want to focus on today?"*

**FR-02 — Add a task**
- User types in the input field and taps "Add" or presses the keyboard return key
- Empty or whitespace-only entries are rejected silently (no error toast)
- Maximum task text length: 120 characters
- Character count indicator appears when the user is within 20 characters of the limit
- Newly added tasks appear at the bottom of the list
- Input field clears after a task is successfully added

**FR-03 — Task limit enforcement**
- Users can configure a maximum number of tasks per day (see Settings, FR-09)
- Default limit: 10 tasks
- When the limit is reached, the "Add" button is disabled and a message appears: *"You've reached your task limit for today"*
- Tasks already in the list are unaffected when the limit is changed mid-day

**FR-04 — Mark a task complete**
- Each task row has a checkbox on the left
- Tapping the checkbox toggles the task between complete and incomplete
- Completed tasks display with strikethrough text and reduced opacity
- Completed tasks move to the bottom of the list, below all incomplete tasks
- Completed tasks can still be deleted or unchecked
- Completion state does not persist across day resets

**FR-05 — Inline edit a task**
- Tapping the task text (not the checkbox) enters inline edit mode
- The task text becomes an editable field in place
- User confirms by tapping outside the field or pressing return
- User cancels with the back button, restoring original text
- Editing constraints are the same as adding: 120 char max, no empty saves

**FR-06 — Delete a task**
- Swipe left on a task row to reveal a red "Delete" action button
- Tapping the delete action removes the task immediately
- No confirmation dialog (swipe is reversible up until the action is tapped)
- No undo after deletion

**FR-07 — Reorder tasks**
- User can drag and drop tasks to reorder them
- A long-press on the drag handle (right side of row) initiates drag mode
- Completed tasks can be reordered among other completed tasks but cannot be moved above incomplete tasks
- Reorder state is preserved until the daily reset

---

### 3.2 Daily Reset

**FR-08 — Midnight reset**
- At midnight (local device time), the current day's task list is cleared from the main view
- The list data is silently saved to local history before clearing (see FR-11)
- On first open after reset, the main screen shows the empty state prompt
- If the app is open exactly at midnight, the reset triggers without requiring the user to restart the app
- Completion states, order, and content from the previous day are not pre-populated in any way

---

### 3.3 Settings

Settings are accessible via a settings icon in the top-right corner of the main screen.

**FR-09 — Task limit setting**
- User can set a daily task limit using a number picker
- Range: 1 to 25 tasks
- Default: 10
- Change takes effect immediately (same day)
- If the current list already exceeds the new limit, existing tasks are not removed — only new additions are blocked

**FR-10 — Reminder settings**
- Toggle to enable/disable the nightly reminder (default: enabled)
- Time picker to set the reminder time (default: 9:00 PM)
- Both settings persist across app restarts and OS reboots
- If the reminder is disabled, no notification is ever sent

---

### 3.4 History

**FR-11 — Silent history save**
- When the daily reset triggers, today's list is saved locally with the date as the key
- Saved data includes: task text, completion state, and the order at time of reset
- History is stored in a local Room database
- History is retained for 30 days, then automatically purged

**FR-12 — History viewer**
- Accessible via a history icon in the top-left corner of the main screen
- Displays a list of past days in reverse chronological order (most recent first)
- Each day entry in the collapsed list shows the date and a completion rate (e.g. "4 of 6 done")
- Days where 0 tasks were added do not appear in the history list
- Tapping a day expands it to show the tasks from that day (read-only)
- Completed tasks are shown with strikethrough, incomplete tasks are shown normally
- No "copy" or "add to today" action is available anywhere in the history view
- History viewer is a separate overlay/bottom sheet — it does not replace the main screen

---

### 3.5 Nightly Reminder Notification

**FR-13 — Conditional notification**
- Notification fires at the user's configured reminder time each night
- The notification is **skipped** if today's task list is not empty at the time of firing
- If the list is empty at reminder time, notification is sent with:
  - Title: *"Plan your day"*
  - Body: *"What do you want to focus on tomorrow?"*
- Tapping the notification opens the app directly to the main screen
- Notification is non-expandable (no inline reply or action buttons needed)
- The notification channel is labelled "Daily reminder" in Android system settings
- Notification scheduling survives device reboots (re-registered via a boot receiver)
- Scheduling uses `AlarmManager` with `setExactAndAllowWhileIdle` for reliability
- App respects the user's Android Do Not Disturb settings

---

## 4. Non-Functional Requirements

### 4.1 Platform

| Attribute | Requirement |
|---|---|
| Minimum Android version | API 26 (Android 8.0 Oreo) |
| Target Android version | API 35 (Android 15) |
| Screen sizes supported | 360dp to 430dp width (phones only, no tablet layout) |
| Orientation | Portrait only |

### 4.2 Permissions

| Permission | Reason |
|---|---|
| `POST_NOTIFICATIONS` | Required for nightly reminder (Android 13+) |
| `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` | Required for precise nightly notification timing |
| `RECEIVE_BOOT_COMPLETED` | Re-register alarms after device reboot |

Permissions are requested at first relevant use (e.g. when user first enables the reminder in Settings), not on app launch.

### 4.3 Data & Storage

- All data is stored locally on device using Room (SQLite)
- No network connectivity required at any point
- No user accounts, no sign-in
- App data is included in Android's standard backup (if the user has device backup enabled)
- History is auto-purged after 30 days

### 4.4 Performance

| Metric | Target |
|---|---|
| Cold start time | < 1 second |
| Task add interaction response | < 100ms |
| Drag-and-drop frame rate | 60fps |
| App size | < 15MB installed |

### 4.5 Accessibility

- All interactive elements meet a minimum touch target size of 48×48dp
- Task text supports system font size scaling (sp units)
- Checkbox and drag handle have content descriptions for screen readers (TalkBack)
- Sufficient color contrast ratio: minimum 4.5:1 for all text

### 4.6 Compatibility

- Supports both light and dark mode (follows system setting)
- Handles time zone changes gracefully (reset is based on local time, recalculated on change)
- Daylight saving time transitions handled without duplicate or missed resets

---

## 5. Edge Cases

| Scenario | Expected Behavior |
|---|---|
| App opened for first time ever | Empty task list with centered prompt shown; no onboarding |
| App opened exactly at midnight during reset | Reset triggers in-app, list clears, prompt appears |
| User changes time zone mid-day | Midnight is recalculated for the new time zone; no data loss |
| Reminder time passes while app is force-stopped | AlarmManager wakes the app for notification delivery |
| User changes task limit below current task count | Existing tasks remain; only new additions are blocked |
| History entry has 0 tasks (user never added any) | Day does not appear in history list |
| Device reboots before nightly reminder fires | Boot receiver re-registers alarm; reminder fires at correct time |
| Notification permission denied by user | App functions normally; reminder silently skipped; no in-app nagging |

---

## 6. Architecture Notes (for Engineering)

- **UI:** Jetpack Compose (single Activity, single screen + overlay for history)
- **State management:** ViewModel + StateFlow
- **Local DB:** Room with two tables: `daily_tasks` (today) and `task_history` (past days)
- **Scheduling:** `AlarmManager` with `setExactAndAllowWhileIdle`; reboot handled via `BroadcastReceiver`
- **DI:** Hilt
- **No network layer needed**

---

## 7. Success Metrics (post-launch)

| Metric | Target (30 days post-launch) |
|---|---|
| Day 7 retention | > 40% |
| Day 30 retention | > 20% |
| Average tasks added per active day | 3–7 |
| Crash-free session rate | > 99.5% |
| Notification opt-in rate | > 70% |

---

## 8. Open Questions

| # | Question | Owner | Due |
|---|---|---|---|
| OQ-03 | What is the app name and icon style? | Product/Design | Pre-dev |
| OQ-04 | Should the task limit default of 10 be validated with user research? | Product | Pre-launch |
| OQ-05 | Should purge of history (30 days) be user-configurable in v1? | Product | Pre-dev |

### Resolved

| # | Question | Decision |
|---|---|---|
| OQ-01 | Should the history viewer show a completion rate? | **Yes.** Each collapsed day entry shows "X of Y done". See FR-12. |
| OQ-02 | Should there be haptic feedback when a task is checked off? | **No.** Not in v1. Can be revisited post-launch based on user feedback. |

---

*End of document*
