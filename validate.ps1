# Runs both repository data checks and a compile check against the local game/mod APIs.
$ErrorActionPreference = "Stop"

python .github/scripts/validate_mod_data.py
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& "$PSScriptRoot\build.ps1" -ValidateOnly
if (-not $?) {
    exit 1
}
