#!/usr/bin/env bash

set -e # Fail on error.

gcloud firebase test android run --no-auto-google-login --type instrumentation --app app/build/outputs/apk/debug/app-debug.apk --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk --device model=Pixel3,version=30,locale=en_US,orientation=portrait --timeout 10m --no-performance-metrics --use-orchestrator & PID_APP_E2E=$!
gcloud firebase test android run --no-auto-google-login --type instrumentation --app app/build/outputs/apk/debug/app-debug.apk --test auth-foundation/build/outputs/apk/androidTest/debug/auth-foundation-debug-androidTest.apk --device model=Pixel3,version=30,locale=en_US,orientation=portrait --timeout 5m --no-performance-metrics & PID_AUTH_FOUNDATION=$!

wait $PID_APP_E2E
wait $PID_AUTH_FOUNDATION
