# MynAssistant Android

Native Android application for the MynAssistant project.

## Development

- Kotlin
- Jetpack Compose
- Gradle
- GitHub Actions for cloud APK builds

This repository is intentionally separate from the existing Windows and Python projects.

## Build

Pushes to `main` and pull requests run the Android debug build through GitHub Actions. The generated `app-debug.apk` is uploaded as a workflow artifact.

## Current milestone

- Firebase Authentication supports email/password and Google sign-in.
- The Android app listens to the signed-in user's Firestore `devices` documents filtered by `ownerId`.
- PC remote actions are queued in Firestore `commands` with the signed-in user's `ownerId` and selected `deviceId`.
- The Windows Agent still needs to register its device in `devices` and consume/update queued `commands` before remote actions execute on a PC.
