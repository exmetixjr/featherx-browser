# FeatherXBrowser

## Overview
Native Android application (APK) — a lightweight web browser. This is **not** a web app; it has no frontend or backend that can be previewed in a browser.

## Tech Stack
- Language: Java
- Build system: Gradle 8.6 (via `./gradlew`)
- Android Gradle Plugin: 8.2.0
- compileSdk: 34, minSdk: 21, targetSdk: 34
- Namespace / applicationId: `me.featherx.browser`

## Project Structure
- `app/` — Android app module (Java sources in `app/src/main/java/me/featherx/browser/`, resources in `app/src/main/res/`)
- `build.gradle`, `settings.gradle`, `gradle.properties` — root Gradle config
- `gradlew` / `gradle/` — Gradle wrapper

## Replit Environment
- Java: GraalVM JDK 19 (preinstalled module `java-graalvm22.3`)
- Nix system dep: `android-tools` installed
- Workflow: **Gradle Tasks** — runs `./gradlew tasks --no-daemon` (console output) to validate the Gradle setup. It exits when done.

## Building the APK
A full `./gradlew assembleDebug` requires the Android SDK (platforms, build-tools) which is not installed by default. To build the APK, install the Android SDK and set `ANDROID_HOME`. The Gradle wrapper itself works.

## Notes
- No deployment is configured: Replit Deployments are for web apps; Android APKs are distributed differently (Play Store, sideload, etc.).
- Signing keystore (`app/release-key.keystore`) and `KEYSTORE_PASSWORD` / `KEY_PASSWORD` env vars are referenced for release builds.
