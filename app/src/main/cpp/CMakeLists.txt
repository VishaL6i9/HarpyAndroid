cmake_minimum_required(VERSION 3.22.1)
project(harpy_native)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add the native library
add_library(harpy_native SHARED
    harpy_native.cpp
    arp_operations.cpp
    network_scan.cpp
)

# Include directories
target_include_directories(harpy_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_NDK}/sources/android/native_app_glue
)

# Link libraries - use native Android libraries
target_link_libraries(harpy_native
    log
)

# Set compiler flags for optimization
target_compile_options(harpy_native PRIVATE
    -Wall
    -Wextra
    -O3
    -fPIC
)

message(STATUS "harpy_native configuration:")
message(STATUS "  Android ABI: ${ANDROID_ABI}")
message(STATUS "  C++ Standard: ${CMAKE_CXX_STANDARD}")
