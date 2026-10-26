param(
    [string]$CredentialSource = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'realm/realm/application-local.properties'),
    [string]$MySqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localRoot = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Path $localRoot -Force | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$acl = Get-Acl -LiteralPath $localRoot
$acl.SetAccessRuleProtection($true, $false)
$rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $localRoot -AclObject $acl
$configPath = Join-Path $localRoot 'application-local.properties'
if (Test-Path -LiteralPath $configPath) {
    Write-Host 'Local database configuration already exists; credentials were preserved.'
    exit 0
}
if (!(Test-Path -LiteralPath $CredentialSource)) { throw 'Provide a local MySQL admin properties file with -CredentialSource.' }
$properties = @{}
Get-Content -LiteralPath $CredentialSource | ForEach-Object {
    if ($_ -match '^\s*([^#!][^=:\s]*)\s*[=:]\s*(.*)$') { $properties[$matches[1]] = $matches[2] }
}
if (!$properties.ContainsKey('spring.datasource.username') -or !$properties.ContainsKey('spring.datasource.password')) {
    throw 'Datasource username/password missing from credential source.'
}
function Escape-Option([string]$Value) { return $Value.Replace('\', '\\').Replace('"', '\"') }
$optionPath = Join-Path $localRoot ('bootstrap-' + [guid]::NewGuid().ToString('N') + '.cnf')
$optionContent = "[client]`nuser=`"$(Escape-Option $properties['spring.datasource.username'])`"`npassword=`"$(Escape-Option $properties['spring.datasource.password'])`"`nhost=127.0.0.1`nport=3306`nprotocol=TCP`ndefault-character-set=utf8mb4`n"
[IO.File]::WriteAllText($optionPath, $optionContent, [Text.UTF8Encoding]::new($false))
$randomBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$appPassword = [Convert]::ToHexString($randomBytes).ToLowerInvariant()
$sql = @"
CREATE DATABASE IF NOT EXISTS haiyan_chuanxin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'haiyan_app'@'localhost' IDENTIFIED BY '$appPassword';
CREATE USER IF NOT EXISTS 'haiyan_app'@'127.0.0.1' IDENTIFIED BY '$appPassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON haiyan_chuanxin.* TO 'haiyan_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON haiyan_chuanxin.* TO 'haiyan_app'@'127.0.0.1';
SELECT 'Database and scoped application user are ready';
"@
try {
    $startInfo = [Diagnostics.ProcessStartInfo]::new($MySqlPath)
    $startInfo.ArgumentList.Add('--defaults-extra-file=' + $optionPath)
    $startInfo.ArgumentList.Add('--batch')
    $startInfo.ArgumentList.Add('--skip-column-names')
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.UseShellExecute = $false
    $process = [Diagnostics.Process]::Start($startInfo)
    $process.StandardInput.WriteLine($sql)
    $process.StandardInput.Close()
    $output = $process.StandardOutput.ReadToEnd()
    $errorOutput = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw ('Database initialization failed: ' + $errorOutput) }
    $configuration = "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/haiyan_chuanxin?connectionTimeZone=Asia/Shanghai&characterEncoding=UTF-8`nspring.datasource.username=haiyan_app`nspring.datasource.password=$appPassword`n"
    [IO.File]::WriteAllText($configPath, $configuration, [Text.UTF8Encoding]::new($false))
    Write-Host $output.Trim()
    Write-Host 'Credentials saved in protected .local/application-local.properties (not printed).'
} finally {
    if (Test-Path -LiteralPath $optionPath) { Remove-Item -LiteralPath $optionPath -Force }
}
