#!/usr/bin/env bash

set -e # Fail on error.

gcloud firebase test android run --no-auto-google-login --type instrumentation --app app/build/outputs/apk/debug/app-debug.apk --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk --device model=Nexus6,version=21,locale=en_US,orientation=portrait --timeout 15m --no-performance-metrics --environment-variables cucumberUseAndroidJUnitRunner=false & PID_APP_CUCUMBER=$!
gcloud firebase test android run --no-auto-google-login --type instrumentation --app app/build/outputs/apk/debug/app-debug.apk --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk --device model=Nexus6,version=21,locale=en_US,orientation=portrait --timeout 15m --no-performance-metrics --environment-variables cucumberUseAndroidJUnitRunner=true & PID_APP_INSTRUMENTED=$!
gcloud firebase test android run --no-auto-google-login --type instrumentation --app dynamic-app/build/outputs/apk/debug/dynamic-app-debug.apk --test dynamic-app/build/outputs/apk/androidTest/debug/dynamic-app-debug-androidTest.apk --device model=Nexus6,version=21,locale=en_US,orientation=portrait --timeout 15m --no-performance-metrics --environment-variables cucumberUseAndroidJUnitRunner=false & PID_DYNAMIC_CUCUMBER=$!

wait $PID_APP_CUCUMBER
wait $PID_APP_INSTRUMENTED
wait $PID_DYNAMIC_CUCUMBER
