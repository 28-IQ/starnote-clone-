# clone-app

Native Android (Kotlin, Compose, androidx.ink). Vertical slice: create a note, write, erase, undo, reopen, share page 1 as PDF.

Open this folder in Android Studio. Not compiled yet; see ../build-log.md before the first build.

- `app/src/main/assets/migrations/`: generated from ../schema.sql by `python3 ../tools/gen_migrations.py`; add a new file entry there for every schema change
- `local.properties.example`: names of the values you set yourself (Play product id); see ../backend.md for accounts and keys
- `app/src/main/java/clone/design/`: generated from ../design/ (run `python3 ../design/gen_tokens.py`, then copy `../design/android/*.kt` here)
- `clone/nav/ScreenCatalog.kt`: all 38 screens from ../recon.md, with build status

## Build an APK to install on a phone

There is no APK in this folder yet: it has to be built with the Android SDK, which was not available where this code was written, and the code has never been compiled, so expect to fix a few compile errors on the first build.

Three ways, easiest first:

1. **GitHub Actions (no installs).** Put this folder in a new GitHub repository, push, open the Actions tab, run "Build debug APK". Download `app-debug-apk` from the finished run. If it fails, download `build-output` and send the errors back.
2. **Android Studio.** Open this folder, accept the Gradle sync, choose Build > Build Bundle(s) / APK(s) > Build APK(s). The file is `app/build/outputs/apk/debug/app-debug.apk`.
3. **Command line.** `./build-apk.sh` (needs JDK 17, the Android SDK and Gradle 8.11+).

Install on the phone: copy `app-debug.apk` to it, open it, and allow "install unknown apps" for the app you opened it from. Android 8.0 or newer. It is a debug build signed with a throwaway key; a Play release needs your own key.
