<#
.SYNOPSIS
Exports the actual Springdoc HTTP contract without Docker or running services.
.DESCRIPTION
Uses the existing test profile (H2 and TestRedisConfig) and all production controllers/DTOs.
No production security configuration is changed. This does not validate PostgreSQL migrations
or replace staging. Java 21 and Maven dependencies are still required.

From the repository root:
  ./scripts/export-openapi-test.ps1

To subsequently regenerate the checked-in TypeScript contract, run from web/:
  pnpm exec openapi-typescript ../server/skillhub-app/target/openapi/openapi.json -o src/api/generated/schema.d.ts

The script itself only writes the JSON export; it never modifies generated TypeScript.
#>
[CmdletBinding()]
param(
    [string] $Output = "server/skillhub-app/target/openapi/openapi.json",
    [string] $MavenCommand = "mvn"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$contractPath = if ([IO.Path]::IsPathRooted($Output)) {
    [IO.Path]::GetFullPath($Output)
} else {
    [IO.Path]::GetFullPath((Join-Path $repoRoot $Output))
}

Push-Location (Join-Path $repoRoot "server")
try {
    & $MavenCommand -pl skillhub-app -am `
        "-Dtest=OpenApiContractExportTest" `
        "-Dsurefire.failIfNoSpecifiedTests=false" `
        "-Dastronhub.openapi.export=true" `
        "-Dastronhub.openapi.output=$contractPath" test
    if ($LASTEXITCODE -ne 0) {
        throw "OpenAPI contract export failed (Maven exit code $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

$contract = Get-Content -LiteralPath $contractPath -Raw | ConvertFrom-Json
if ($contract.openapi -notlike "3.*" -or $null -eq $contract.paths -or $null -eq $contract.components.schemas) {
    throw "The exported document is not an OpenAPI 3 contract."
}
Write-Output "Exported Springdoc contract: $contractPath"
