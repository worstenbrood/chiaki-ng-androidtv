param([string]$Task = 'assembleDebug')
$ErrorActionPreference = 'Stop'
if(-not $env:JAVA_HOME) { $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User') }
if(-not $env:ANDROID_HOME) { $env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User') }
if(-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk" }
if(-not $env:CHIAKI_MSYS_ROOT) { $env:CHIAKI_MSYS_ROOT = 'C:/msys64' }
$env:PATH = "$env:JAVA_HOME/bin;" + [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + $env:PATH
$env:LC_ALL = 'C'
$env:LANG = 'C'
foreach($tool in @('java', 'python', 'protoc')) {
	if(-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Missing build tool: $tool" }
}
foreach($required in @("$env:JAVA_HOME/bin/javac.exe", "$env:ANDROID_HOME/ndk/28.2.13676358/source.properties", "$env:ANDROID_HOME/cmake/3.22.1/bin/ninja.exe", "$env:CHIAKI_MSYS_ROOT/usr/bin/bash.exe", "$env:CHIAKI_MSYS_ROOT/usr/bin/make.exe", "$env:CHIAKI_MSYS_ROOT/usr/bin/perl.exe")) {
	if(-not (Test-Path -LiteralPath $required)) { throw "Missing build dependency: $required" }
}
& python -c 'import google.protobuf'
if($LASTEXITCODE -ne 0) { throw 'Install Python protobuf: python -m pip install protobuf' }
Push-Location $PSScriptRoot
try {
	$taskArgs = @($Task -split '\s+' | Where-Object { $_ })
	& ./gradlew.bat @taskArgs --console=plain
	$buildExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $buildExitCode
