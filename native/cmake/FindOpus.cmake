# Shim that satisfies chiaki-lib's find_package(Opus REQUIRED) with the opus
# target built from native/third-party/opus (added via add_subdirectory in
# native/CMakeLists.txt before the lib is added).
#
# chiaki includes <opus/opus.h>, but the opus source tree keeps its headers
# flat in include/, so stage them into an <dir>/opus/ layout in the build tree.

set(OPUS_SHIM_INCLUDE_DIR "${CMAKE_BINARY_DIR}/opus-include-shim")
file(MAKE_DIRECTORY "${OPUS_SHIM_INCLUDE_DIR}/opus")
file(GLOB OPUS_SHIM_HEADERS "${CMAKE_CURRENT_LIST_DIR}/../third-party/opus/include/*.h")
foreach(opus_shim_header ${OPUS_SHIM_HEADERS})
	configure_file("${opus_shim_header}" "${OPUS_SHIM_INCLUDE_DIR}/opus/" COPYONLY)
endforeach()

set(Opus_FOUND TRUE)
set(Opus_LIBRARIES opus)
set(Opus_INCLUDE_DIRS "${OPUS_SHIM_INCLUDE_DIR}")
