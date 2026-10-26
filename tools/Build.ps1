$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Use-Environment.ps1')
Push-Location (Join-Path $projectRoot 'frontend')
try {
    if (Test-Path -LiteralPath 'package-lock.json') { & npm.cmd ci --no-fund --no-audit }
    else { & npm.cmd install --no-fund --no-audit }
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency install failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    $staticRoot = Join-Path $projectRoot 'backend/src/main/resources/static'
    $resolvedStaticRoot = [IO.Path]::GetFullPath($staticRoot)
    $expectedStaticRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'backend/src/main/resources/static'))
    if ($resolvedStaticRoot -ne $expectedStaticRoot -or !$resolvedStaticRoot.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar)) {
        throw 'Invalid generated static assets directory.'
    }
    if (Test-Path -LiteralPath $resolvedStaticRoot) { Remove-Item -LiteralPath $resolvedStaticRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $resolvedStaticRoot -Force | Out-Null
    Copy-Item -Path (Join-Path $projectRoot 'frontend/dist/*') -Destination $resolvedStaticRoot -Recurse -Force
} finally { Pop-Location }
Push-Location (Join-Path $projectRoot 'backend')
try {
    & mvn.cmd --batch-mode --no-transfer-progress verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
} finally { Pop-Location }
Write-Host 'Frontend and backend verified. Runnable JAR: backend/target/haiyan-chuanxin.jar'

