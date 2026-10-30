#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <sdk-dir> [windows|linux]" >&2
  exit 1
fi

SDK_DIR="$1"
HOST_KIND="${2:-auto}"
BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-36.1.0}"

if [[ "$HOST_KIND" == "auto" ]]; then
  if [[ "$SDK_DIR" == /mnt/* ]]; then
    HOST_KIND="windows"
  else
    HOST_KIND="linux"
  fi
fi

if [[ ! -d "$SDK_DIR" ]]; then
  echo "SDK directory not found: $SDK_DIR" >&2
  exit 1
fi

BUILD_TOOLS_DIR="$SDK_DIR/build-tools/$BUILD_TOOLS_VERSION"
if [[ ! -d "$BUILD_TOOLS_DIR" ]]; then
  echo "Build-tools $BUILD_TOOLS_VERSION not installed in: $SDK_DIR" >&2
  exit 1
fi

if [[ "$HOST_KIND" == "windows" ]]; then
  REQUIRED_FILE="$BUILD_TOOLS_DIR/aapt.exe"
  WRONG_FILE="$BUILD_TOOLS_DIR/aapt"
else
  REQUIRED_FILE="$BUILD_TOOLS_DIR/aapt"
  WRONG_FILE="$BUILD_TOOLS_DIR/aapt.exe"
fi

if [[ ! -e "$REQUIRED_FILE" ]]; then
  echo "Expected tool missing for $HOST_KIND SDK: $REQUIRED_FILE" >&2
  exit 1
fi

if [[ -e "$WRONG_FILE" ]]; then
  echo "Potential mixed-host SDK detected. Unexpected file present: $WRONG_FILE" >&2
  exit 1
fi

echo "SDK looks healthy for $HOST_KIND: $SDK_DIR"
echo "Verified build-tools: $BUILD_TOOLS_VERSION"
