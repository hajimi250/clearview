#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
cd "$PROJECT_DIR"

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-36.1.0}"

if [[ "$SDK_DIR" == /mnt/* ]]; then
  echo "Refusing to use a Windows-mounted SDK inside WSL: $SDK_DIR" >&2
  echo "Install a dedicated Linux SDK, for example at \$HOME/Android/Sdk." >&2
  exit 1
fi

if [[ ! -d "$SDK_DIR" ]]; then
  echo "Android SDK not found at: $SDK_DIR" >&2
  echo "Set ANDROID_HOME / ANDROID_SDK_ROOT to your Linux SDK path before running this script." >&2
  exit 1
fi

if [[ ! -x "$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION/aapt" ]]; then
  echo "Missing Linux build-tools $BUILD_TOOLS_VERSION in $SDK_DIR" >&2
  echo "Expected executable: $SDK_DIR/build-tools/$BUILD_TOOLS_VERSION/aapt" >&2
  exit 1
fi

TMP_BACKUP="$(mktemp)"
HAD_LOCAL_PROPERTIES=0

cleanup() {
  if [[ "$HAD_LOCAL_PROPERTIES" -eq 1 ]]; then
    cp "$TMP_BACKUP" "$PROJECT_DIR/local.properties"
  else
    rm -f "$PROJECT_DIR/local.properties"
  fi
  rm -f "$TMP_BACKUP"
}

trap cleanup EXIT

if [[ -f "$PROJECT_DIR/local.properties" ]]; then
  cp "$PROJECT_DIR/local.properties" "$TMP_BACKUP"
  HAD_LOCAL_PROPERTIES=1
fi

cat > "$PROJECT_DIR/local.properties" <<EOF
## Auto-generated for WSL builds by gradlew-wsl.sh
sdk.dir=$SDK_DIR
EOF

exec ./gradlew "$@"
