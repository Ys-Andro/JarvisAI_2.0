cmake_minimum_required(VERSION 3.22.1)

project("jarvis_llama_android" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization and ARM64-v8a flags
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_compile_options(-O3 -fPIC -pthread)
endif()

# Configuration flags for llama.cpp / ggml
set(GGML_STANDALONE OFF CACHE BOOL "Disable ggml standalone" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable llama examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable llama tests" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable llama server" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libraries for internal components" FORCE)

# Locate Android system logging library
find_library(log-lib log)

# If llama.cpp git submodule is present, add it directly as a subdirectory
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(STATUS "llama.cpp submodule detected at ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")
    add_subdirectory(llama.cpp)
    set(HAVE_LLAMA_SUBMODULE TRUE)
else()
    message(WARNING "llama.cpp submodule NOT found at ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp. Building JNI bridge in stub/interface mode.")
    set(HAVE_LLAMA_SUBMODULE FALSE)
endif()

# Define the shared library that Android loads via System.loadLibrary("llama_android")
add_library(llama_android SHARED
    llama_android.cpp
)

# Header include paths
target_include_directories(llama_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
)

if(HAVE_LLAMA_SUBMODULE)
    target_compile_definitions(llama_android PRIVATE HAVE_LLAMA_CPP=1)
    target_link_libraries(llama_android
        PRIVATE
        llama
        ggml
        ${log-lib}
    )
else()
    target_link_libraries(llama_android
        PRIVATE
        ${log-lib}
    )
endif()
