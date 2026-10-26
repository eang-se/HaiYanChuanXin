$projectRoot = Split-Path $PSScriptRoot -Parent
$manifestPath = Join-Path $projectRoot '.tools/toolchain.json'
if (!(Test-Path -LiteralPath $manifestPath)) { throw 'Run tools/Install-Toolchain.ps1 first.' }
$toolchain = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$env:JAVA_HOME = $toolchain.javaHome
$env:PATH = $toolchain.nodeHome + ';' + (Join-Path $toolchain.mavenHome 'bin') + ';' + (Join-Path $toolchain.javaHome 'bin') + ';' + $env:PATH

