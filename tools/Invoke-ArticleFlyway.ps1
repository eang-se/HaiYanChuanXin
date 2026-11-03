param([Parameter(Mandatory=$true)][string]$ConfigPath)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Use-Environment.ps1')
$config=@{}
Get-Content -LiteralPath $ConfigPath | ForEach-Object {
    if ($_ -match '^\s*([^#!][^=:\s]*)\s*[=:]\s*(.*)$') { $config[$matches[1]]=$matches[2] }
}
foreach ($name in @('spring.datasource.url','spring.datasource.username','spring.datasource.password')) {
    if (!$config.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($config[$name])) { throw "Missing $name in configuration" }
}
$tempRoot=Join-Path $projectRoot 'tmp'
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
$runId=[guid]::NewGuid().ToString('N')
$classPathFile=Join-Path $tempRoot "flyway-classpath-$runId.txt"
$classDir=Join-Path $tempRoot "flyway-classes-$runId"
New-Item -ItemType Directory -Path $classDir | Out-Null
try {
    & mvn.cmd -q -f (Join-Path $projectRoot 'backend/pom.xml') dependency:build-classpath "-Dmdep.outputFile=$classPathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve backend dependencies' }
    $dependencies=Get-Content -LiteralPath $classPathFile -Raw
    & javac.exe -encoding UTF-8 -cp $dependencies.Trim() -d $classDir (Join-Path $PSScriptRoot 'FlywayArticleUpgrade.java')
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the Flyway helper' }
    $env:ARTICLE_UPGRADE_DB_URL=$config['spring.datasource.url']
    $env:ARTICLE_UPGRADE_DB_USER=$config['spring.datasource.username']
    $env:ARTICLE_UPGRADE_DB_PASSWORD=$config['spring.datasource.password']
    $env:ARTICLE_UPGRADE_MIGRATIONS=Join-Path $projectRoot 'backend/src/main/resources/db/migration'
    & java.exe -cp ($classDir+';'+$dependencies.Trim()) FlywayArticleUpgrade
    if ($LASTEXITCODE -ne 0) { throw 'Flyway upgrade failed' }
} finally {
    foreach ($name in @('ARTICLE_UPGRADE_DB_URL','ARTICLE_UPGRADE_DB_USER','ARTICLE_UPGRADE_DB_PASSWORD','ARTICLE_UPGRADE_MIGRATIONS')) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $classPathFile) { Remove-Item -LiteralPath $classPathFile -Force }
    $classFile=Join-Path $classDir 'FlywayArticleUpgrade.class'
    if (Test-Path -LiteralPath $classFile) { Remove-Item -LiteralPath $classFile -Force }
    if (Test-Path -LiteralPath $classDir) { Remove-Item -LiteralPath $classDir -Force }
}
