#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="8.10.2"
GRADLE_HOME="/tmp/gradle-${GRADLE_VERSION}"
GRADLE_ZIP="/tmp/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  echo "[cypher] downloading Gradle ${GRADLE_VERSION}"
  rm -f "${GRADLE_ZIP}"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${GRADLE_ZIP}"
  else
    wget -q "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O "${GRADLE_ZIP}"
  fi
  rm -rf "${GRADLE_HOME}"
  unzip -q "${GRADLE_ZIP}" -d /tmp
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "[cypher] Android SDK is not available in this build environment" >&2
  exit 20
fi

ROOT_DIR="$(pwd)"
cd "${ROOT_DIR}/cypher-brain"

"${GRADLE_HOME}/bin/gradle" \
  --no-daemon \
  --stacktrace \
  :app:clean \
  :app:assembleDebug

APK="${ROOT_DIR}/cypher-brain/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -s "${APK}" ]; then
  echo "[cypher] APK was not generated" >&2
  exit 21
fi

# The debug variant is signed by the Android Gradle Plugin and is directly
# installable for sideload testing.
echo "[cypher] APK ready: ${APK} ($(wc -c < "${APK}") bytes)"

GROUP_ID="${GROUP:-com.github.babillaojdjebsbebebe}"
ARTIFACT_ID="${ARTIFACT:-Examples-And-Stuff-Neede}"
VERSION_ID="${VERSION:-dev}"

if command -v mvn >/dev/null 2>&1; then
  mvn -q install:install-file \
    -Dfile="${APK}" \
    -DgroupId="${GROUP_ID}" \
    -DartifactId="${ARTIFACT_ID}" \
    -Dversion="${VERSION_ID}" \
    -Dpackaging=apk \
    -DgeneratePom=true
else
  GROUP_PATH="${GROUP_ID//.//}"
  DEST="${HOME}/.m2/repository/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION_ID}"
  mkdir -p "${DEST}"
  cp "${APK}" "${DEST}/${ARTIFACT_ID}-${VERSION_ID}.apk"
  cat > "${DEST}/${ARTIFACT_ID}-${VERSION_ID}.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${GROUP_ID}</groupId>
  <artifactId>${ARTIFACT_ID}</artifactId>
  <version>${VERSION_ID}</version>
  <packaging>apk</packaging>
  <name>Cypher Brain APK</name>
</project>
POM
fi

echo "[cypher] published ${GROUP_ID}:${ARTIFACT_ID}:${VERSION_ID} as APK"
