#! /bin/bash


# This script publishes open source Android gradle project to Sonatype OSSRH (and on to Maven Central).
# It requires:
# - Sonatype credentials (username/password)
# - A GPG key for signing the artifacts

function java18_0() {
	echo "INFO: Setting up Java 18..."
	local jdk_archive="amazon-corretto-18-x64-linux-jdk.tar.gz"
	pushd /usr/java;

	curl -fL -o "${jdk_archive}" "https://corretto.aws/downloads/latest/${jdk_archive}"
	tar xzf "${jdk_archive}"
	rm -f "${jdk_archive}"
	ln -snf $(ls -d /usr/java/amazon-corretto-18.*-linux-x64)/ /usr/java/default;
	ln -snf /usr/java/default/bin/java /usr/bin/java;

	export JAVA_HOME=/usr/java/default
	popd
}

function yummed() {
	echo "INFO: Installing dependencies with yum..."
	echo "timeout=120" >> /etc/yum.conf
	yum install -y gnupg unzip
}

function get_secret_from_terminus() {
	local SECRET_NAME="$1"
	local SECRET_ENV_VAR="$2"
	local SECRET_LOCATION="sonatype_auth"
	# get_terminus_secret "/${SECRET_LOCATION}" "${SECRET_NAME}" "${SECRET_ENV_VAR}"
	get_terminus_secret "/" "${SECRET_NAME}" "${SECRET_ENV_VAR}"

	if [ -z "${!SECRET_ENV_VAR}" ]; then
		echo "ERROR: Variable ${SECRET_ENV_VAR} in location ${SECRET_LOCATION} in project sonatype is empty." >&2
		echo "HINT: Does your repository have access to the 'sonatype' project on Terminus?" >&2
		exit 1;
	fi
}

function fetch_gpg() {
	get_secret_from_terminus gpg_tar_archive GPG_TAR_ARCHIVE
	[ -d "${HOME}/.gnupg" ] && { mv "${HOME}/.gnupg" "${HOME}/.gnupg.original"; } || { echo "Clean GPG"; }
	mkdir -p "${HOME}/.gnupg" && chmod 700 "${HOME}/.gnupg"

	echo "INFO: Decoding and extracting GPG key..."
	echo "${GPG_TAR_ARCHIVE}" | base64 --decode -i - | tar x -C "${HOME}/.gnupg"
	# Set secure permissions for the current user, not root, to allow Gradle to read the keys.
	chmod 600 "${HOME}/.gnupg"/*
}

function install_android_sdk() {
    local CMD_LINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
    local LATEST_PLATFORM="platforms;android-36"
    local LATEST_BUILD_TOOLS="build-tools;35.0.0"
    local LATEST_NDK="ndk;29.0.14206865"

    local TEMP_DIR
    TEMP_DIR=$(mktemp -d)
    trap 'rm -rf -- "$TEMP_DIR"' RETURN

    echo "INFO: Downloading Android command line tools..."
    curl -fL -o "${TEMP_DIR}/${CMD_LINE_TOOLS_ZIP}" \
       "https://dl.google.com/android/repository/${CMD_LINE_TOOLS_ZIP}"

    echo "INFO: Unzipping tools..."
    unzip -q -d "${TEMP_DIR}" "${TEMP_DIR}/${CMD_LINE_TOOLS_ZIP}"

    echo "Moving tools to SDK root..."
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"
    mv "${TEMP_DIR}/cmdline-tools/"* "${ANDROID_SDK_ROOT}/cmdline-tools/latest/"

    local SDK_MANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

    echo "INFO: Accepting SDK licenses..."
    yes | "${SDK_MANAGER}" --licenses

    echo "INFO: Installing SDK components..."
    "${SDK_MANAGER}" "${LATEST_PLATFORM}"
    "${SDK_MANAGER}" "${LATEST_BUILD_TOOLS}"
    "${SDK_MANAGER}" --install "${LATEST_NDK}" --channel=0
}

pushd "${OKTA_HOME}/${REPO}"
echo "============================"
echo "Configuring General tooling"
java18_0
echo "----------------------------"
yummed

echo "----------------------------"
echo "Setting up Android SDK"
ANDROID_SDK_ROOT=/opt/android-sdk-linux
install_android_sdk

export ANDROID_HOME=/opt/android-sdk-linux
export ANDROID_ADB_HOME=$ANDROID_HOME/platform-tools
export ANDROID_TOOLS_HOME=$ANDROID_HOME/tools

echo "----------------------------"
echo "Obtaining Secrets"

get_secret_from_terminus sonatype_user SONATYPE_USERNAME
get_secret_from_terminus sonatype_password SONATYPE_PASSWORD
get_secret_from_terminus gpg_passphrase GPG_PASSPHRASE
get_secret_from_terminus gpg_keyid GPG_KEYID

echo "============================"
echo "Setting up GPG"
echo "----------------------------"
which gpg \
  && fetch_gpg \
  || { echo "ERROR: gpg command not found. Please ensure gnupg is installed."; exit 1; }

echo "============================"
echo "RELEASE_ARTIFACT: ${RELEASE_ARTIFACT}"
echo "----------------------------"

echo "INFO: Preparing to publish with Gradle..."
export EXTRA_ARGS=""
if [ "${REPO}" == "okta-mobile-kotlin" ]; then
	export EXTRA_ARGS='-Dorg.gradle.jvmargs="-XX:MaxMetaspaceSize=1024M"'
fi

if [ "${SNAP_SHOT}" == "true" ]; then
  EXTRA_ARGS="${EXTRA_ARGS} -Psnapshot=true"
elif [ "${RELEASE_ARTIFACT}" == "true" ]; then
  EXTRA_ARGS="${EXTRA_ARGS} -PautomaticRelease=true"
fi

echo "INFO: Executing Gradle publish command..."
GRADLE_OUTPUT=$(./gradlew publishToMavenCentral \
	--rerun-tasks --no-daemon --no-parallel -PsignAllPublications \
	-PmavenCentralUsername="${SONATYPE_USERNAME}" \
	-PmavenCentralPassword="${SONATYPE_PASSWORD}" \
	-Psigning.keyId="${GPG_KEYID}" \
	-Psigning.password="${GPG_PASSPHRASE}" \
	-Psigning.secretKeyRingFile="${HOME}/.gnupg/secring.gpg" \
	${EXTRA_ARGS} 2>&1)
GRADLE_EXIT_CODE=$?

echo "$GRADLE_OUTPUT"

if [ $GRADLE_EXIT_CODE -ne 0 ]; then
  echo "ERROR: Gradle task failed with exit code: $GRADLE_EXIT_CODE" >&2
  exit $GRADLE_EXIT_CODE
fi

echo "INFO: Gradle task completed successfully."

echo "============================"
popd
