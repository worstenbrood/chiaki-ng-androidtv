#!/usr/bin/env bash
# MSYS tools run on Windows; clang.exe cross-compiles for Android.
set -euo pipefail
export PATH="/usr/bin:/bin:$PATH"
stage=$1
source_dir=$(cygpath -u "$2")
install_dir=$(cygpath -m "$3")
export ANDROID_NDK_HOME=$(cygpath -u "$4")
abi=$5
api=$6
ndk_bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/windows-x86_64/bin"
export PATH="$ndk_bin:/usr/bin:/bin"
export LC_ALL=C
# Keep POSIX paths for Perl/make, but let MSYS translate compiler arguments.
case "$abi" in
    arm64-v8a) target=android-arm64; triple=aarch64-linux-android ;;
    armeabi-v7a) target=android-arm; triple=armv7a-linux-androideabi ;;
    x86) target=android-x86; triple=i686-linux-android ;;
    x86_64) target=android-x86_64; triple=x86_64-linux-android ;;
    *) echo "Unsupported Android ABI: $abi" >&2; exit 1 ;;
esac
case "$stage" in
    configure)
        perl "$source_dir/Configure" "$target" "--prefix=$install_dir" no-shared "-D__ANDROID_API__=$api"
        ;;
    build|install)
        goal=build_libs
        if [[ $stage == install ]]; then goal=install_dev; fi
        make -j4 "CC=clang --target=$triple$api" "$goal"
        ;;
    *) exit 1 ;;
esac
