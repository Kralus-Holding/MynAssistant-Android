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

The first milestone is a launchable Android shell with a local command input. Commands are not executed on the phone or a PC yet; device pairing and remote control will be added in later, tested stages.
