$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent;$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,10);$actor='grouptest_'+$runId;$password=[guid]::NewGuid().ToString('N')
$groupA='roster-sync-a-'+$runId;$groupB='roster-sync-b-'+$runId
$fileA=Join-Path $root ('QQ_Whitelist/'+$groupA+'.md');$fileB=Join-Path $root ('QQ_Whitelist/'+$groupB+'.md')
$props=@{};Get-Content (Join-Path $root '.local/application-local.properties') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$props[$matches[1]]=$matches[2]}}
function Sql([string]$query){$env:MYSQL_PWD=$props['spring.datasource.password'];try{$out=& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=haiyan_app --database=haiyan_chuanxin --batch --skip-column-names --default-character-set=utf8mb4 ('--execute='+$query);if($LASTEXITCODE-ne0){throw 'Group test database operation failed'};return $out}finally{Remove-Item Env:MYSQL_PWD}}
function Call-Api($client,[string]$method,[string]$path,$body=$null,[int]$expected=200){$args=@{Uri=$base+$path;Method=$method;WebSession=$client.Session;Headers=@{'X-CSRF-TOKEN'=$client.Token};SkipHttpErrorCheck=$true};if($null-ne$body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $body -Depth 10 -Compress};$response=Invoke-WebRequest @args;if([int]$response.StatusCode-ne$expected){throw "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"};if($response.Content -and $response.Headers['Content-Type'] -like '*json*'){return $response.Content|ConvertFrom-Json}}
function Assert-True($value,[string]$message){if(!$value){throw $message}}
$existing=@{};Get-ChildItem (Join-Path $root 'QQ_Whitelist') -Filter '*.md' -File | ForEach-Object {Get-Content -LiteralPath $_.FullName -Encoding UTF8 | ForEach-Object {if($_ -match '^\s*([1-9]\d{4,11})\s+'){$existing[$matches[1]]=$true}}}
$number=$null;while(!$number){$candidate=[Security.Cryptography.RandomNumberGenerator]::GetInt32(100000000,999999999).ToString();if(!$existing.ContainsKey($candidate) -and [int](Sql "SELECT (SELECT COUNT(*) FROM members WHERE qq_number='$candidate')+(SELECT COUNT(*) FROM qq_invitation_bindings WHERE qq_number='$candidate')+(SELECT COUNT(*) FROM registration_codes WHERE qq_number='$candidate')+(SELECT COUNT(*) FROM qq_allowlist WHERE qq_number='$candidate')")-eq0){$number=$candidate}}
$createdActor=$false;$createdFiles=$false
try{
 if((Test-Path -LiteralPath $fileA)-or(Test-Path -LiteralPath $fileB)){throw 'Temporary test roster exists'}
 [IO.File]::WriteAllText($fileA,"$number 群甲昵称`n",[Text.UTF8Encoding]::new($false));$createdFiles=$true
 [IO.File]::WriteAllText($fileB,"$number 群乙昵称`n",[Text.UTF8Encoding]::new($false))
 $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession;$csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session;$client=@{Session=$session;Token=$csrf.token}
 [void](Call-Api $client POST '/auth/register' @{username=$actor;password=$password;qq='12345';code='0000'});$createdActor=$true
 [void](Sql "UPDATE members SET role='SUPER_ADMIN' WHERE username='$actor' AND qq_number='12345'")
 [void](Call-Api $client POST '/auth/login' @{username=$actor;password=$password})
 $groups=@(Call-Api $client GET '/admin/registration/qq-groups');Assert-True ((@($groups|Where-Object groupId -in @($groupA,$groupB))).Count-eq2) 'Group files not listed separately'
 [void](Call-Api $client DELETE "/admin/registration/qq-allowlist/$groupA/$number")
 $rows=@(Call-Api $client GET '/admin/registration/qq-allowlist'|Where-Object {$_.qqNumber-eq$number})
 Assert-True ($rows.Count-eq2 -and @($rows|Where-Object {$_.active}).Count-eq0) 'Disabling one group did not disable both entries'
 [void](Call-Api $client POST '/admin/registration/qq-allowlist' @{groupId=$groupB;qqNumbers=@($number)})
 $rows=@(Call-Api $client GET '/admin/registration/qq-allowlist'|Where-Object {$_.qqNumber-eq$number})
 Assert-True ($rows.Count-eq2 -and @($rows|Where-Object {$_.active}).Count-eq2) 'Enabling one group did not enable both entries'
 Write-Host 'PASS: separate group listings and synchronized enable/disable state across groups.'
}finally{
 if($number){[void](Sql "DELETE FROM qq_allowlist WHERE qq_number='$number'")}
 if($createdActor){[void](Sql "DELETE FROM qq_invitation_bindings WHERE qq_number='12345' AND member_id IN (SELECT id FROM members WHERE username='$actor'); DELETE FROM members WHERE username='$actor' AND qq_number='12345';")}
 if($createdFiles){if(Test-Path -LiteralPath $fileA){Remove-Item -LiteralPath $fileA -Force};if(Test-Path -LiteralPath $fileB){Remove-Item -LiteralPath $fileB -Force}}
}
