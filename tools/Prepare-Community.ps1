$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$configPath=Join-Path $projectRoot '.local/application-local.properties'
if (!(Test-Path -LiteralPath $configPath)) { throw 'Initialize the database first.' }
if (Select-String -LiteralPath $configPath -Pattern '^platform.bootstrap.password=' -Quiet) { Write-Host 'Bootstrap credentials already configured.'; exit 0 }
$bytes=New-Object byte[] 20
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$password=[Convert]::ToHexString($bytes).ToLowerInvariant()
Add-Content -LiteralPath $configPath -Value "`nplatform.bootstrap.password=$password" -Encoding utf8
$note="海燕传信初始管理员`n登录地址：http://127.0.0.1:8091/#/login`n账号：admin`n密码：$password`n`n此文件包含登录凭据，请妥善保管。管理员可在社务台生成社员邀请码。"
[IO.File]::WriteAllText((Join-Path $projectRoot '.local/admin-account.txt'),$note,[Text.UTF8Encoding]::new($false))
Write-Host 'Initial admin credentials saved to protected .local/admin-account.txt.'
