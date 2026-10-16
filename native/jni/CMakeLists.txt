cmake_minimum_required(VERSION 3.2)

set(CMAKE_CXX_STANDARD 14)

add_library(chiaki-jni SHARED
        chiaki-jni.c
        log.h
        log.c
        video-decoder.h
        video-decoder.c
        audio-decoder.h
        audio-decoder.c
        audio-output.h
        audio-output.cpp
        circular-buf.hpp)
target_link_libraries(chiaki-jni chiaki-lib)

# mic encoder tuning calls opus_encoder_ctl directly; reuse the same staged
# <opus/opus.h> layout chiaki-lib uses (see ../cmake/FindOpus.cmake)
if(CHIAKI_LIB_ENABLE_OPUS)
	target_link_libraries(chiaki-jni opus)
	target_include_directories(chiaki-jni PRIVATE "${CMAKE_BINARY_DIR}/opus-include-shim")
endif()

find_library(ANDROID_LIB_LOG log)
find_library(ANDROID_LIB_MEDIANDK mediandk)
find_library(ANDROID_LIB_ANDROID android)
target_link_libraries(chiaki-jni "${ANDROID_LIB_LOG}" "${ANDROID_LIB_MEDIANDK}" "${ANDROID_LIB_ANDROID}")

find_package (oboe REQUIRED CONFIG)
target_link_libraries(chiaki-jni oboe::oboe)
