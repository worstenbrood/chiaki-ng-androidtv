param(
	[string]$task = "assembleDebug"
)
docker run --rm --platform linux/amd64 `
	-v "${PWD}:/src" `
	-v "${PWD}/.docker-cache/gradle:/root/.gradle" `
	-w /src chiaki-tv-build-amd64 sh gradlew $task
