cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_library(log-lib log)

# Check if llama.cpp submodule is present
if (EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(STATUS "Found llama.cpp source submodule at ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)
    set(LLAMA_STANDALONE OFF CACHE BOOL "Build standalone binaries" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
    add_subdirectory(llama.cpp)
endif()

add_library(
    llama-android
    SHARED
    llama-bridge.cpp
)

target_include_directories(
    llama-android
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

target_compile_options(
    llama-android
    PRIVATE
    -O3
    -Wall
    -Wextra
    -fexceptions
    -frtti
)

target_link_libraries(
    llama-android
    ${log-lib}
)
