# Windows x64 builds

Run from PowerShell:

```powershell
.\build.ps1 -Local
.\build.ps1 -Local "assembleRelease"
.\build.ps1 -Local "assembleDebug -Pabis=arm64-v8a"
```

The default builds both arm64-v8a and armeabi-v7a. Outputs are in
`app/build-windows/outputs/apk/`; CMake caches are in `app/.cxx-windows/`.
Docker remains available through `build.ps1` without `-Local`, with its original
output and cache directories. `-Fresh`, `-Stop`, and `-SkipNative` are Docker-only.

## Prerequisites

- JDK 17 (`JAVA_HOME`).
- Android SDK (`ANDROID_HOME`): platforms;android-35, build-tools;34.0.0,
  platform-tools, ndk;28.2.13676358, cmake;3.22.1. Accept the SDK licenses.
- Python 3 with `python -m pip install protobuf`, and `protoc` on PATH.
- MSYS2 at `C:/msys64`, or set `CHIAKI_MSYS_ROOT` to its installation directory.
  In MSYS2 install `pacman -S --needed make perl`.
- Git submodules initialized as described in AGENTS.md.

The launcher uses the user PATH and JAVA_HOME/ANDROID_HOME settings, so restart
PowerShell after installing tools. Standalone CMake, Strawberry Perl and GNUWin32
Make are not required: Gradle uses SDK CMake/Ninja, and OpenSSL uses MSYS2 tools.

MSYS2 supplies the Windows POSIX shell tools for OpenSSL, while the Windows NDK
compiles Android binaries. No Docker, WSL or CPU emulation is used. Local CMake
overrides are in `native/cmake/`; keep them aligned with upstream on core updates.
