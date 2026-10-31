$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,10)
$roster=Join-Path $root ('QQ_Whitelist/roster-check-'+$runId+'.md')
$names=@(('rostera_'+$runId),('rosterb_'+$runId))
$display=@(('名单昵称甲'+$runId),('名单昵称乙'+$runId))
$password=[guid]::NewGuid().ToString('N')
$props=@{}
Get-Content -LiteralPath (Join-Path $root '.local/application-local.properties') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$props[$matches[1]]=$matches[2]}}
function Sql([string]$query){$env:MYSQL_PWD=$props['spring.datasource.password'];try{$out=& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=haiyan_app --database=haiyan_chuanxin --batch --skip-column-names --default-character-set=utf8mb4 ('--execute='+$query);if($LASTEXITCODE-ne0){throw 'Roster test database operation failed'};return $out}finally{Remove-Item Env:MYSQL_PWD}}
function New-Client {$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession;$csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session;return @{Session=$session;Token=$csrf.token}}
function Call-Api($client,[string]$method,[string]$path,$body=$null,[int]$expected=200){$args=@{Uri=$base+$path;Method=$method;WebSession=$client.Session;Headers=@{'X-CSRF-TOKEN'=$client.Token};SkipHttpErrorCheck=$true};if($null-ne$body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $body -Depth 10 -Compress};$response=Invoke-WebRequest @args;if([int]$response.StatusCode-ne$expected){throw "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"};if($response.Content -and $response.Headers['Content-Type'] -like '*json*'){return $response.Content|ConvertFrom-Json}}
function Assert-True($value,[string]$message){if(!$value){throw $message}}
$existing=@{};Get-ChildItem (Join-Path $root 'QQ_Whitelist') -Filter '*.md' -File | ForEach-Object {Get-Content -LiteralPath $_.FullName -Encoding UTF8 | ForEach-Object {if($_ -match '^\s*([1-9]\d{4,11})\s+'){$existing[$matches[1]]=$true}}}
$numbers=@();while($numbers.Count-lt2){$candidate=[Security.Cryptography.RandomNumberGenerator]::GetInt32(100000000,999999999).ToString();if(!$existing.ContainsKey($candidate) -and $numbers-notcontains$candidate -and [int](Sql "SELECT (SELECT COUNT(*) FROM members WHERE qq_number='$candidate')+(SELECT COUNT(*) FROM qq_invitation_bindings WHERE qq_number='$candidate')+(SELECT COUNT(*) FROM registration_codes WHERE qq_number='$candidate')+(SELECT COUNT(*) FROM qq_allowlist WHERE qq_number='$candidate')")-eq0){$numbers+=,$candidate}}
$created=$false
try{
 if(Test-Path -LiteralPath $roster){throw 'Temporary roster already exists'}
 [IO.File]::WriteAllText($roster,($numbers[0]+' '+$display[0]+"`n"),[Text.UTF8Encoding]::new($false));$created=$true
 for($i=0;$i-lt2;$i++){
  if($i-eq1){[IO.File]::AppendAllText($roster,($numbers[$i]+' '+$display[$i]+"`n"),[Text.UTF8Encoding]::new($false))}
  [void](Sql "INSERT INTO registration_codes(qq_number,code_hash,expires_at,sent_at) VALUES('$($numbers[$i])',SHA2('654321',256),DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 MINUTE),CURRENT_TIMESTAMP)")
  $client=New-Client
  [void](Call-Api $client POST '/auth/register' @{username=$names[$i];password=$password;qq=$numbers[$i];code='654321'})
  [void](Call-Api $client POST '/auth/login' @{username=$names[$i];password=$password})
  $me=Call-Api $client GET '/auth/me'
  Assert-True ($me.penName-eq$display[$i]) 'Default nickname did not match roster entry'
 }
 Write-Host 'PASS: Markdown eligibility, roster default nickname, and adding another entry without restarting.'
}finally{
 if($created){
  $numberList=($numbers|ForEach-Object {"'$_'"}) -join ',';$nameList=($names|ForEach-Object {"'$_'"}) -join ','
  [void](Sql "DELETE FROM qq_invitation_bindings WHERE qq_number IN ($numberList); DELETE FROM registration_codes WHERE qq_number IN ($numberList); DELETE FROM members WHERE username IN ($nameList) AND qq_number IN ($numberList);")
  if(Test-Path -LiteralPath $roster){Remove-Item -LiteralPath $roster -Force}
 }
}
