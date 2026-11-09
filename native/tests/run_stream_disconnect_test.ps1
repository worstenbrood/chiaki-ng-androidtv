param([string]$Device)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$sdkRoot = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
if(-not $sdkRoot) { $sdkRoot = "$env:LOCALAPPDATA/Android/Sdk" }
$clang = "$sdkRoot/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
$nativeBuild = Get-ChildItem "$repoRoot/app/.cxx-windows/RelWithDebInfo/*/arm64-v8a/lib/libchiaki.a" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if(-not $nativeBuild) { throw 'Build the local release first: .\build.ps1 -Local assembleRelease' }
$nativeDir = $nativeBuild.Directory.Parent.FullName
$outputDir = "$repoRoot/app/build-windows/native-tests"
New-Item -ItemType Directory -Force $outputDir | Out-Null
$output = "$outputDir/stream-disconnect-test"
$libraries = @('lib/libchiaki.a', 'openssl-install-prefix/lib/libssl.a',
    'openssl-install-prefix/lib/libcrypto.a', 'third-party/libjerasure.a',
    'third-party/libgf_complete.a', 'third-party/nanopb/libprotobuf-nanopb.a',
    'third-party/curl/lib/libcurl.a', '_deps/json-c-build/libjson-c.a',
    '_deps/miniupnpc-build/libminiupnpc.a', 'opus/libopus.a') |
    ForEach-Object { Join-Path $nativeDir $_ }
& $clang --target=aarch64-linux-android24 -std=gnu11 -O1 -g -ffunction-sections -fdata-sections `
    "-I$repoRoot/native/overrides/include" "-I$repoRoot/external/chiaki-ng/lib/include" `
    "$PSScriptRoot/stream_disconnect_test.c" '-Wl,--gc-sections' '-Wl,--start-group' @libraries `
    '-Wl,--end-group' -llog -latomic -lm -o $output
if($LASTEXITCODE -ne 0) { throw 'Native regression test compilation failed' }
if($Device) {
    $adb = "$sdkRoot/platform-tools/adb.exe"
    & $adb -s $Device push $output /data/local/tmp/chiaki-stream-disconnect-test
    if($LASTEXITCODE -ne 0) { throw 'Uploading native regression test failed' }
    & $adb -s $Device shell chmod 700 /data/local/tmp/chiaki-stream-disconnect-test
    if($LASTEXITCODE -ne 0) { throw 'Setting test executable permission failed' }
    & $adb -s $Device shell /data/local/tmp/chiaki-stream-disconnect-test
    if($LASTEXITCODE -ne 0) { throw 'Native disconnect regression failed' }
}
