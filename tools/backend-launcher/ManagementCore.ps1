Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Test-Roster([string]$Path) {
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -gt 1048576) { throw '名单不能超过 1 MB。' }
    if ($file.Name -notmatch '^[A-Za-z0-9_-]{1,100}\.md$') { throw '名单文件名只能使用字母、数字、下划线或连字符，扩展名为 .md。' }
    $encoding = [Text.UTF8Encoding]::new($false, $true)
    $lines = $encoding.GetString([IO.File]::ReadAllBytes($file.FullName)) -split "`n"
    $seen = @{}; $count = 0
    foreach ($raw in $lines) {
        $line = $raw.Replace([string][char]0xfeff, '').Trim()
        if (!$line -or $line.StartsWith('#') -or $line.StartsWith('<!--')) { continue }
        if ($line -notmatch '^([1-9][0-9]{4,11})\s+(.+?)\s*$') { throw '名单格式错误：每行应为 QQ号 空格 群内名称。' }
        $qq = $Matches[1]; $name = [regex]::Replace($Matches[2], '\p{Cc}', '').Trim()
        if (!$name) { throw '名单中存在空名称。' }
        if ($seen.ContainsKey($qq) -and $seen[$qq] -cne $name) { throw '名单中有相同 QQ 号对应不同名称。' }
        if (!$seen.ContainsKey($qq)) { $seen[$qq] = $name; $count++ }
    }
    if (!$count) { throw '名单没有有效成员，未执行更新。' }
    return $count
}
function Test-CloudSettings($Settings) {
    if ($Settings.hostName -notmatch '^[a-zA-Z0-9][a-zA-Z0-9.:-]*$' -or $Settings.hostName.StartsWith('-')) { throw '服务器地址无效。' }
    if ($Settings.user -notmatch '^[a-zA-Z_][a-zA-Z0-9_-]*$') { throw 'SSH 用户名无效。' }
    if ([int]$Settings.port -lt 1 -or [int]$Settings.port -gt 65535) { throw 'SSH 端口无效。' }
    if ($Settings.service -notmatch '^[a-zA-Z0-9_.@-]+$' -or $Settings.service.StartsWith('-')) { throw 'systemd 服务名无效。' }
    if ($Settings.rosterDir -notmatch '^/(?:[a-zA-Z0-9_.-]+/)*[a-zA-Z0-9_.-]+/?$' -or $Settings.rosterDir -match '(^|/)\.\.(/|$)') { throw '名单目录必须是简单的 Linux 绝对路径。' }
}
function Update-LocalRoster([string]$Source, [string]$Directory) {
    $count = Test-Roster $Source
    $target = Join-Path $Directory ([IO.Path]::GetFileName($Source))
    if ([IO.Path]::GetFullPath($Source) -ieq [IO.Path]::GetFullPath($target)) { throw '所选文件已在目标目录中。' }
    if (!(Test-Path -LiteralPath $Directory -PathType Container)) { throw '本地名单目录不存在。' }
    $temp = Join-Path $Directory ('.roster-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::Copy($Source, $temp)
        if (Test-Path -LiteralPath $target) {
            $backup = $target + '.' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.bak'
            [IO.File]::Replace($temp, $target, $backup)
        } else { [IO.File]::Move($temp, $target) }
    } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
    return $count
}
