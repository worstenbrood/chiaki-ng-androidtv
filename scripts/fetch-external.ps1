# Downloads chiaki-ng and its submodule sources into external/ (required before building).
# Usage: pwsh scripts/fetch-external.ps1
$ErrorActionPreference = "Stop"
$root = Join-Path $PSScriptRoot ".."
$ext = Join-Path $root "external"
New-Item -ItemType Directory -Force $ext | Out-Null

function Get-Zip($url, $dest) {
    $tmp = Join-Path $env:TEMP ("chiaki_" + (Split-Path $url -Leaf))
    if (-not (Test-Path $dest) -or (Get-ChildItem $dest | Measure-Object).Count -eq 0) {
        Write-Host "downloading $url"
        curl.exe -sSL --retry 8 --retry-all-errors -o $tmp $url
        $extract = Join-Path $env:TEMP ("chiaki_x_" + (Split-Path $dest -Leaf))
        Remove-Item -Recurse -Force $extract -ErrorAction SilentlyContinue
        tar.exe -xf $tmp -C $env:TEMP
        $dir = Get-ChildItem $env:TEMP -Directory | Where-Object Name -match '^(chiaki-ng-main|nanopb-|jerasure-|gf-complete-|curl-)' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        New-Item -ItemType Directory -Force $dest | Out-Null
        Copy-Item "$($dir.FullName)/*" $dest -Recurse -Force
        Remove-Item -Recurse -Force $dir.FullName, $tmp
    } else {
        Write-Host "exists: $dest"
    }
}

Get-Zip "https://codeload.github.com/chiaki-ng/chiaki-ng/zip/refs/heads/main" (Join-Path $ext "chiaki-ng")
Get-Zip "https://codeload.github.com/nanopb/nanopb/zip/refs/tags/nanopb-0.4.9.2" (Join-Path $ext "chiaki-ng\third-party\nanopb")
Get-Zip "https://codeload.github.com/streetpea/jerasure/zip/refs/heads/main" (Join-Path $ext "chiaki-ng\third-party\jerasure")
Get-Zip "https://codeload.github.com/streetpea/gf-complete/zip/refs/heads/main" (Join-Path $ext "chiaki-ng\third-party\gf-complete")
Get-Zip "https://codeload.github.com/curl/curl/zip/refs/tags/curl-8_8_0" (Join-Path $ext "chiaki-ng\third-party\curl")
Write-Host "done"
