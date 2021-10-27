#!/usr/bin/env bash

./gradlew installDist
./gradlew :studio-plugin:prepareSandbox
./build/install/gradle-profiler/bin/gradle-profiler --benchmark --scenario-file performance.scenarios androidStudioSync --studio-install-dir /Applications/Android\ Studio\ Preview.app --project-dir /Users/asodja/workspace/santa-tracker-android
