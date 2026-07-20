#!/bin/bash
cd /home/a/zilch
export ANDROID_HOME=/home/a/Android/Sdk
export JAVA_HOME=/usr
./gradlew assembleDebug --no-daemon > /tmp/zilch_build2.log 2>&1
echo "EXIT_CODE=$?"
grep -E "BUILD|error:|Error|FAILED" /tmp/zilch_build2.log | head -30
