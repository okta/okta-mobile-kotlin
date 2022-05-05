#!/usr/bin/env bash

export CLOUDSDK_CORE_DISABLE_PROMPTS=1
curl https://sdk.cloud.google.com | bash > /dev/null
echo $GOOGLE_SERVICE_JSON_BASE64 | base64 --decode > client-secret.json
gcloud auth activate-service-account --key-file client-secret.json
source $HOME/google-cloud-sdk/path.bash.inc
gcloud config set project $GOOGLE_PROJECT_ID
gcloud auth list # This is necessary to trigger gcloud auth so that it doesn't error when accessing it in the test scripts.
