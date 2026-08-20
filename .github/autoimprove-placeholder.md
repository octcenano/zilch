name: CI

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build:
    name: Build, test & lint
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Make gradlew executable
        run: chmod +x gradlew

      - name: Cache Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run unit tests
        run: ./gradlew testDebugUnitTest --no-daemon

      - name: Assemble debug APK
        run: ./gradlew assembleDebug --no-daemon

      - name: Lint
        run: ./gradlew lintDebug --no-daemon
