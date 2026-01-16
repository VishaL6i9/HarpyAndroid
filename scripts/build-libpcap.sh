#!/bin/bash

# Build libpcap for Android
# Usage: ./build-libpcap.sh [arm64-v8a|armeabi-v7a|x86|x86_64]

set -e

LIBPCAP_VERSION="1.10.4"
ANDROID_ABI="${1:-arm64-v8a}"
ANDROID_API="${ANDROID_API:-24}"
ANDROID_NDK="${ANDROID_NDK_HOME}"

if [ -z "$ANDROID_NDK" ]; then
    echo "Error: ANDROID_NDK_HOME not set"
    exit 1
fi

echo "Building libpcap for Android"
echo "  Version: $LIBPCAP_VERSION"
echo "  ABI: $ANDROID_ABI"
echo "  API: $ANDROID_API"
echo "  NDK: $ANDROID_NDK"

# Create working directory
WORK_DIR=$(mktemp -d)
cd "$WORK_DIR"

echo "Working directory: $WORK_DIR"

# Download libpcap
echo "Downloading libpcap..."
wget -q https://github.com/the-tcpdump-group/libpcap/archive/refs/tags/libpcap-${LIBPCAP_VERSION}.tar.gz
tar xzf libpcap-${LIBPCAP_VERSION}.tar.gz
cd libpcap-${LIBPCAP_VERSION}

# Create build directory
mkdir -p build
cd build

# Create Android toolchain file
cat > Android.cmake << EOF
set(CMAKE_ANDROID_NDK       "${ANDROID_NDK}")
set(CMAKE_SYSTEM_NAME       "Android")
set(CMAKE_SYSTEM_VERSION    ${ANDROID_API})
set(CMAKE_ANDROID_ARCH_ABI  "${ANDROID_ABI}")
set(CMAKE_ANDROID_STL_TYPE  "c++_shared")
set(CMAKE_ANDROID_TOOLCHAIN "clang")
EOF

echo "Configuring libpcap..."
cmake \
  -DDISABLE_DBUS=True \
  -DDISABLE_RDMA=True \
  -DDISABLE_DAG=True \
  -DDISABLE_SEPTEL=True \
  -DDISABLE_SNF=True \
  -DDISABLE_AIRPCAP=True \
  -DDISABLE_TC=True \
  -DCMAKE_GENERATOR=Ninja \
  -DCMAKE_C_FLAGS="-O3" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE=Android.cmake \
  ..

echo "Building libpcap..."
ninja

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Create prebuilt directory structure
PREBUILT_DIR="${PROJECT_ROOT}/app/src/main/cpp/prebuilt"
mkdir -p "${PREBUILT_DIR}/${ANDROID_ABI}"
mkdir -p "${PREBUILT_DIR}/include"

echo "Copying libraries..."
cp libpcap.a "${PREBUILT_DIR}/${ANDROID_ABI}/"
cp ../pcap.h "${PREBUILT_DIR}/include/"

echo "Build complete!"
echo "Libraries installed to: ${PREBUILT_DIR}"
echo ""
echo "To use in your build, add to build.gradle:"
echo "  externalNativeBuild {"
echo "    cmake {"
echo "      arguments \"-DLIBPCAP_PREBUILT=ON\""
echo "    }"
echo "  }"

# Cleanup
cd /
rm -rf "$WORK_DIR"
