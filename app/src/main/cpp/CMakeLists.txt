cmake_minimum_required(VERSION 3.22.1)
project(harpy_native)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add the native JNI library
add_library(harpy_native SHARED
    harpy_native.cpp
    arp_operations.cpp
    network_scan.cpp
    dns_handler.cpp
    dhcp_spoofing.cpp
)

# Add the standalone root helper binary
# We name it libharpy_root_helper.so so Android packages it in the lib/ folder
# but we build it as an EXECUTABLE so it has a main() and can be run via su
add_executable(harpy_root_helper
    root_helper_main.cpp
    arp_operations.cpp
    network_scan.cpp
    dns_handler.cpp
    dhcp_spoofing.cpp
)

# Force the name to follow Android library naming conventions for packaging
set_target_properties(harpy_root_helper PROPERTIES 
    OUTPUT_NAME "harpy_root_helper"
    PREFIX "lib"
    SUFFIX ".so"
)

# Include directories
target_include_directories(harpy_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(harpy_root_helper PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Link libraries
target_link_libraries(harpy_native log)
target_link_libraries(harpy_root_helper log)

# Set compiler flags for optimization
target_compile_options(harpy_native PRIVATE -Wall -Wextra -O3 -fPIC)
target_compile_options(harpy_root_helper PRIVATE -Wall -Wextra -O3 -fPIC)

message(STATUS "harpy_native configuration:")
message(STATUS "  Android ABI: ${ANDROID_ABI}")
message(STATUS "  C++ Standard: ${CMAKE_CXX_STANDARD}")
