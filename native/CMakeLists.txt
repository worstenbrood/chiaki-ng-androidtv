cmake_minimum_required(VERSION 3.16)

project(chiaki-tv)

# Top-level CMake for the Chiaki TV app.
# Mirrors the Android-relevant parts of external/chiaki-ng/CMakeLists.txt,
# but builds OUR copy of the JNI layer (native/jni) which adds audio
# volume/device selection on top of upstream's chiaki-jni.

set(CHIAKI_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../external/chiaki-ng")

# lib/CMakeLists.txt checks this to skip desktop-only deps (libevent)
set(CHIAKI_ENABLE_ANDROID ON)

option(CHIAKI_LIB_ENABLE_OPUS "Use Opus as part of Chiaki Lib" ON)
option(CHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT "Use OpenSSL as CMake external project" OFF)
option(CHIAKI_LIB_JSONC_EXTERNAL_PROJECT "Build json-c from source via FetchContent" OFF)
option(CHIAKI_LIB_MINIUPNPC_EXTERNAL_PROJECT "Build miniupnpc from source via FetchContent" OFF)

set(CHIAKI_VERSION_MAJOR 1)
set(CHIAKI_VERSION_MINOR 10)
set(CHIAKI_VERSION_PATCH 0)
set(CHIAKI_VERSION ${CHIAKI_VERSION_MAJOR}.${CHIAKI_VERSION_MINOR}.${CHIAKI_VERSION_PATCH})

set(CMAKE_CXX_STANDARD 14)

# Extra optimization for release builds (NDK default is -O2); appended so the
# stock "-O2 -DNDEBUG" is kept and -O3 wins as the later flag.
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -flto=thin")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -flto=thin")

# curl >= 8.0 no longer reads the CURL_CA_BUNDLE env var; the default trust store
# must be baked in at compile time. CaBundle.kt extracts the bundled Mozilla
# cacert.pem asset to exactly this path before any holepunch call runs.
set(CURL_CA_BUNDLE "/data/data/com.chiaking.tv/files/ca-bundle.pem" CACHE STRING "Default CA bundle" FORCE)

list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
list(APPEND CMAKE_MODULE_PATH "${CHIAKI_ROOT}/cmake")

find_package(PythonInterp 3 REQUIRED) # Make sure nanopb doesn't find Python 2.7

if(CHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT)
	include(OpenSSLExternalProject)
endif()

add_subdirectory(${CHIAKI_ROOT}/third-party third-party)

# Opus (mic audio): built from our own submodule; FindOpus.cmake shim below
# satisfies chiaki-lib's find_package(Opus REQUIRED) without touching the submodule.
if(CHIAKI_LIB_ENABLE_OPUS)
	set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
	set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
	set(OPUS_INSTALL_PKGCONFIG_MODULE OFF CACHE BOOL "" FORCE)
	set(OPUS_INSTALL_PKGCONFIG_FILE OFF CACHE BOOL "" FORCE)
	add_subdirectory(third-party/opus opus EXCLUDE_FROM_ALL)
endif()

add_definitions(-DCHIAKI_VERSION_MAJOR=${CHIAKI_VERSION_MAJOR} -DCHIAKI_VERSION_MINOR=${CHIAKI_VERSION_MINOR} -DCHIAKI_VERSION_PATCH=${CHIAKI_VERSION_PATCH} -DCHIAKI_VERSION=\"${CHIAKI_VERSION}\")

if(CHIAKI_LIB_JSONC_EXTERNAL_PROJECT)
	include(JsoncExternalProject)
endif()

if(CHIAKI_LIB_MINIUPNPC_EXTERNAL_PROJECT)
	include(MiniupnpcExternalProject)
endif()

add_subdirectory(${CHIAKI_ROOT}/lib lib)

# Local perf overrides for C core files (see docs/UPSTREAM_SYNC.md):
# swap upstream gkcrypt.c/takion.c for our copies in native/overrides and shadow
# the public chiaki/gkcrypt.h (BEFORE PUBLIC so chiaki-lib AND all consumers,
# e.g. chiaki-jni, see the same struct definition).
get_target_property(CHIAKI_LIB_SOURCES chiaki-lib SOURCES)
list(FILTER CHIAKI_LIB_SOURCES EXCLUDE REGEX "gkcrypt\\.c$")
list(FILTER CHIAKI_LIB_SOURCES EXCLUDE REGEX "takion\\.c$")
list(APPEND CHIAKI_LIB_SOURCES
	"${CMAKE_CURRENT_SOURCE_DIR}/overrides/gkcrypt.c"
	"${CMAKE_CURRENT_SOURCE_DIR}/overrides/takion.c")
set_target_properties(chiaki-lib PROPERTIES SOURCES "${CHIAKI_LIB_SOURCES}")
target_include_directories(chiaki-lib BEFORE PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/overrides/include")
# appended (not BEFORE) so public lib/include still wins; lets the override .c files
# find lib/src-local headers like utils.h and chiaki/feedback.h
target_include_directories(chiaki-lib PRIVATE "${CHIAKI_ROOT}/lib/src")

add_subdirectory(jni)
