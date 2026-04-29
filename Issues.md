# Code Quality Issues & Critique

This document outlines the findings from the code analysis of the Todo App repository using Gemini. 
This shows we do need to give guidance on how we want our code to look like. 

## 1. Architecture & Design Patterns

### Repository Transactions
- **Issue:** `TodoRepository.archiveAndClearOldTasks` performs several sequential database operations (reading dates, reading tasks, inserting into history, and deleting old records).
- **Impact:** If the app crashes midway, the data could be in an inconsistent state (e.g., tasks archived but not deleted from the main table).
- **Recommendation:** Wrap these operations in a `@Transaction` block in the DAO or use `RoomDatabase.withTransaction`.

### Background Logic in ViewModel
- **Issue:** `scheduleMidnightReset` uses a `viewModelScope` with a `while(isActive)` loop and `delay` to handle daily resets.
- **Impact:** This logic only runs while the `ViewModel` is alive. If the app is in the background and the process is killed, the reset won't happen until the app is opened again.
- **Recommendation:** Use `WorkManager` for reliable background tasks like daily database maintenance or resets.

### Domain vs. Data Models
- **Issue:** Room entity classes (`TodoTask`, `TaskHistory`) are used directly in the UI and ViewModel.
- **Impact:** Tight coupling between the database schema and the UI. Changes to the database might require changes across the entire app.
- **Recommendation:** Introduce domain models and mapper functions to decouple the layers.

### Explicit Coroutine Dispatchers
- **Issue:** `TodoRepository` doesn't explicitly specify `Dispatchers.IO` for its operations.
- **Impact:** While Room handles single suspend functions on its own thread, the complex logic in `archiveAndClearOldTasks` might benefit from explicit dispatcher management to ensure it doesn't block the caller if called from a non-main thread incorrectly (though ViewModel uses `viewModelScope`).
- **Recommendation:** Use `withContext(Dispatchers.IO)` in the Repository for all database-heavy operations.

## 2. Jetpack Compose & UI

### Large Composable Functions
- **Issue:** `TodoScreen` is over 250 lines long and contains complex drag-and-drop logic.
- **Impact:** Harder to maintain, test, and reuse.
- **Recommendation:** Extract the drag-and-drop logic into a dedicated state holder (e.g., `rememberReorderState`) and break down the UI into smaller, focused composables.

### Hardcoded Strings
- **Issue:** Many UI strings (e.g., "Today", "What do you want to focus on today?", "Delete", "Add a task") are hardcoded in `TodoScreen.kt`.
- **Impact:** Makes localization and internationalization difficult.
- **Recommendation:** Move all user-facing strings to `strings.xml`.

### Hardcoded Constants
- **Issue:** `MAX_TASK_LENGTH` and `CHAR_COUNT_WARNING_THRESHOLD` are defined in `TodoViewModel.kt`.
- **Impact:** `TodoScreen.kt` depends on `TodoViewModel.kt` for these constants, making them tightly coupled.
- **Recommendation:** Move constants used by both UI and Logic to a `Constants.kt` file or a companion object in a relevant class.

## 3. Best Practices & Naming

### Date Consistency
- **Issue:** `LocalDate.now().toString()` is used throughout the app to represent dates.
- **Impact:** While ISO-8601 is a good standard, relying on `toString()` can be brittle.
- **Recommendation:** Use a consistent date formatting utility or store dates as Long (timestamps) if possible, though ISO strings are fine for simple Room queries.

### Error Handling
- **Issue:** Most repository and database operations lack `try-catch` blocks or error state propagation to the UI.
- **Impact:** The app might crash or fail silently if a database issue occurs.
- **Recommendation:** Implement error handling in the ViewModel and expose an `Error` state to the UI to inform the user.

## 4. Performance

### Redundant State Updates
- **Issue:** `displayedTasks` is updated via `LaunchedEffect(tasks)` in `TodoScreen`.
- **Impact:** This is generally fine for handling reorders, but ensure that it doesn't cause unnecessary recompositions for very large lists.
- **Recommendation:** Consider using `derivedStateOf` if the transformation logic becomes more complex.
