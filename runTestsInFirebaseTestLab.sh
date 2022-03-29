#!/usr/bin/env bash

set -e # Fail on error.

gcloud firebase test android run --no-auto-google-login --type instrumentation --app dynamic-app/build/outputs/apk/debug/dynamic-app-debug.apk --test dynamic-app/build/outputs/apk/androidTest/debug/dynamic-app-debug-androidTest.apk --device model=Pixel3,version=30,locale=en_US,orientation=portrait --timeout 25m --no-performance-metrics --environment-variables cucumberUseAndroidJUnitRunner=false
