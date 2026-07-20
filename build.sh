#!/bin/bash
cd /home/a/zilch
export ANDROID_HOME=/home/a/Android/Sdk
export JAVA_HOME=/usr
./gradlew assembleDebug --no-daemon > /tmp/build10.log 2>&1
echo "EXIT=$?"
