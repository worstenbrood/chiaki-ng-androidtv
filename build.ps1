param(
	[string]$task = "assembleDebug",
	[switch]$Fresh,
	[switch]$Stop
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
docker exec -w /src $name sh gradlew $task
