## Plan: Android File Explorer App with Jetpack Compose

Build a fully functional file explorer app using Jetpack Compose + Material3. The app will navigate folders, display the current path as a breadcrumb, toggle hidden files, show full filenames with extensions, support file operations (cut/copy/paste/rename/delete), and open files in external apps via an intent chooser. A `FEATURES.md` tracker file will track feature status.

### Steps

1. ✅ **Add permissions & manifest setup** — In [AndroidManifest.xml](C:\Users\Admin\AndroidStudioProjects\Files\app\src\main\AndroidManifest.xml), added `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, and `MANAGE_EXTERNAL_STORAGE` permissions. Added `android:requestLegacyExternalStorage="true"` for SDK 29 compatibility. The `MainActivity` requests `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` at runtime for Android 11+ (SDK 30+).

2. ✅ **Add dependencies** — In [libs.versions.toml](C:\Users\Admin\AndroidStudioProjects\Files\gradle\libs.versions.toml) and [app/build.gradle.kts](C:\Users\Admin\AndroidStudioProjects\Files\app\build.gradle.kts), added `androidx-lifecycle-viewmodel-compose`, `material-icons-extended`, and `androidx-documentfile`.

3. ✅ **Create `FileExplorerViewModel`** — Created `app/src/main/java/com/gmail/omkarjoshi1989/viewmodel/FileExplorerViewModel.kt` with all state management and file operations.

4. ✅ **Create UI screens** — Created `app/src/main/java/com/gmail/omkarjoshi1989/ui/screens/FileExplorerScreen.kt` with breadcrumb bar, file list, bottom sheet operations, rename/delete dialogs, and paste FAB.

5. ✅ **Set up `FileProvider`** — Created `file_paths.xml` in `app/src/main/res/xml/` and registered `<provider>` in AndroidManifest.xml.

6. ✅ **Wire everything in `MainActivity`** — Updated `MainActivity.kt` with permission handling, ViewModel integration, and back press navigation.

7. ✅ **Create `FEATURES.md`** — Created `FEATURES.md` at the project root tracking all features with status columns.

### Further Considerations

1. **Storage Access approach**: Android 11+ requires `MANAGE_EXTERNAL_STORAGE` for full filesystem access. This may trigger Play Store policy review if published — acceptable for a personal file manager tool.
2. **File copy/move for large files**: Should these operations run in a coroutine with a progress indicator, or is a simple blocking approach acceptable for v1? *Recommend*: use `viewModelScope.launch(Dispatchers.IO)` with a progress state.
3. **Navigation library**: Should we use Compose Navigation for a multi-screen setup (e.g., a Settings screen later), or keep it single-screen for now? *Recommend*: single-screen for now, easy to add Navigation later via the `FEATURES.md` tracker.

