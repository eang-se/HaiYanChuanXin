$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '.tools'
$downloadRoot = Join-Path $toolRoot 'downloads'
New-Item -ItemType Directory -Path $downloadRoot -Force | Out-Null

function Install-Archive($Name, $Url, $ExpectedHash, $Algorithm) {
    $archivePath = Join-Path $downloadRoot ($Name + '.zip')
    $destination = Join-Path $toolRoot $Name
    if (Test-Path (Join-Path $destination '.installed')) { return $destination }
    Write-Host "Downloading $Name ..."
    & curl.exe --fail --location --retry 3 --silent --show-error --output $archivePath $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Name" }
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm $Algorithm).Hash -ne $ExpectedHash) {
        throw "Checksum mismatch: $Name"
    }
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Expand-Archive -LiteralPath $archivePath -DestinationPath $destination -Force
    Set-Content -LiteralPath (Join-Path $destination '.installed') -Value $ExpectedHash
    return $destination
}

$jdkUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip'
$jdkDirectory = Install-Archive 'temurin-21.0.12.1' $jdkUrl 'f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e' 'SHA256'
$nodeName = 'node-v24.22.1-win-x64'
$nodeBase = 'https://nodejs.org/dist/v24.22.1/'
$nodeChecksums = (Invoke-WebRequest ($nodeBase + 'SHASUMS256.txt')).Content
$nodeHash = ([regex]::Match($nodeChecksums, '(?m)^([a-f0-9]{64})\s+' + [regex]::Escape($nodeName + '.zip') + '\s*$')).Groups[1].Value
if (!$nodeHash) { throw 'Node checksum not found' }
$nodeDirectory = Install-Archive $nodeName ($nodeBase + $nodeName + '.zip') $nodeHash 'SHA256'
$mavenName = 'apache-maven-3.9.16'
$mavenUrl = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/' + $mavenName + '-bin.zip'
$mavenHash = ((Invoke-WebRequest ($mavenUrl + '.sha512')).Content.Trim() -split '\s+')[0]
$mavenDirectory = Install-Archive $mavenName $mavenUrl $mavenHash 'SHA512'
$jdkHome = (Get-ChildItem -LiteralPath $jdkDirectory -Directory | Select-Object -First 1).FullName
$manifest = [ordered]@{
    javaHome = $jdkHome
    nodeHome = Join-Path $nodeDirectory $nodeName
    mavenHome = Join-Path $mavenDirectory $mavenName
    javaVersion = '21.0.12.1'
    nodeVersion = '24.22.1'
    mavenVersion = '3.9.16'
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $toolRoot 'toolchain.json') -Encoding utf8
Write-Host 'Project toolchain installed. Global PATH and JAVA_HOME were not changed.'

