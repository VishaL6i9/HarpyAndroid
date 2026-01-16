cmake_minimum_required(VERSION 3.22.1)
project(harpy_native)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Option to use prebuilt libpcap
option(LIBPCAP_PREBUILT "Use prebuilt libpcap library" OFF)

if(LIBPCAP_PREBUILT)
    # Define prebuilt libpcap library (SHARED)
    add_library(libpcap SHARED IMPORTED)
    set_target_properties(libpcap PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libpcap.so"
        INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/include"
    )
    message(STATUS "Using prebuilt libpcap.so from: ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}")
else()
    message(STATUS "libpcap not configured. Using shell command fallback.")
endif()

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

if(LIBPCAP_PREBUILT)
    target_include_directories(harpy_native PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/include
    )
endif()

# Link libraries
target_link_libraries(harpy_native
    log
)

if(LIBPCAP_PREBUILT)
    target_link_libraries(harpy_native
        libpcap
    )
endif()

# Set compiler flags for optimization
target_compile_options(harpy_native PRIVATE
    -Wall
    -Wextra
    -O3
    -fPIC
)

# Define preprocessor flags
if(LIBPCAP_PREBUILT)
    target_compile_definitions(harpy_native PRIVATE
        HAVE_LIBPCAP=1
    )
endif()

message(STATUS "harpy_native configuration:")
message(STATUS "  Android ABI: ${ANDROID_ABI}")
message(STATUS "  libpcap: ${LIBPCAP_PREBUILT}")
message(STATUS "  C++ Standard: ${CMAKE_CXX_STANDARD}")

