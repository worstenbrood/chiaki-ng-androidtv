param(
	[string]$task = "assembleDebug",
	[switch]$Fresh,
	[switch]$Stop,
	[switch]$SkipNative
)
$name = "chiaki-tv-build"
if($Stop)
{
	docker rm -f $name 2>$null | Out-Null
	exit 0
}
$running = docker ps -q -f "name=^${name}$"
if($Fresh -or -not $running)
{
	docker rm -f $name 2>$null | Out-Null
	docker run -d --name $name --platform linux/amd64 `
		-v "${PWD}:/src" `
		-v "${PWD}/.docker-cache/gradle:/root/.gradle" `
		-w /src chiaki-tv-build-amd64 sleep infinity | Out-Null
}
# -SkipNative: skip the CMake/NDK tasks (reuses the .so files from the previous build).
# Only use when native/ and external/ are unchanged; a full build is required after native edits.
# Assumes the default ABIs (arm64-v8a, armeabi-v7a).
$taskArgs = @($task -split '\s+' | Where-Object { $_ })
if($SkipNative)
{
	foreach($abi in @("arm64-v8a", "armeabi-v7a"))
	{
		$taskArgs += @("-x", "buildCMakeDebug[$abi]", "-x", "configureCMakeDebug[$abi]")
		$taskArgs += @("-x", "buildCMakeRelWithDebInfo[$abi]", "-x", "configureCMakeRelWithDebInfo[$abi]")
	}
}
docker exec -w /src $name sh gradlew @taskArgs
