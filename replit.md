# FeatherX Browser

## Overview
Native Android browser app (APK). Lightweight WebView-based browser with multiple tabs, dev tools, JS injection, bookmarks, history, find-in-page, downloads, and pull-to-refresh.

## Tech Stack
- Java, Gradle 8.6, Android Gradle Plugin 8.2.0
- compileSdk 34, minSdk 21, targetSdk 34
- namespace / applicationId: `me.featherx.browser`
- Material Components, AndroidX SwipeRefreshLayout

## Project Structure
- `app/src/main/java/me/featherx/browser/` — Java sources
  - `MainActivity.java` — UI, navigation, dev tools sheet
  - `TabManager.java` — tab lifecycle
  - `BookmarkManager.java`, `HistoryManager.java` — persistence via SharedPreferences
  - `SnippetManager.java`, `UAProfiles.java`, `UpdateChecker.java`, `WebAppInterface.java`
- `app/src/main/res/` — layouts, drawables, menu, colors, themes, strings
- `app/src/main/assets/eruda.min.js` — bundled DevTools

## Replit Environment
- Java: GraalVM 19 (module `java-graalvm22.3`)
- Android SDK installed at `~/android-sdk` (platforms;android-34, build-tools;34.0.0, platform-tools)
- System dep: `android-tools`, `unzip`
- `local.properties` is generated locally (gitignored) and points Gradle at `~/android-sdk`
- Workflow: **Build APK** runs `./gradlew assembleDebug` — APK output: `app/build/outputs/apk/debug/app-debug.apk`

## CI
`.github/workflows/build.yml` builds the release APK on push to `main` using JDK 17 (Temurin) and uploads it as a workflow artifact.

## Notes
- Not a web app: nothing to preview in the Replit browser.
- Release builds need a keystore at `app/release-key.keystore` (the CI workflow generates a throwaway one).
