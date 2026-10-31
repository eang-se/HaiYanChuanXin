$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,10)
$nameA='reset_a_'+$runId;$nameB='reset_b_'+$runId
$password=[guid]::NewGuid().ToString('N');$newPassword=[guid]::NewGuid().ToString('N')
$props=@{}
Get-Content -LiteralPath (Join-Path $root '.local/application-local.properties') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$props[$matches[1]]=$matches[2]}}
function New-Client {$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession;$csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session;return @{Session=$session;Token=$csrf.token}}
function Call-Api($client,[string]$method,[string]$path,$body=$null,[int]$expected=200){
 $args=@{Uri=$base+$path;Method=$method;WebSession=$client.Session;Headers=@{'X-CSRF-TOKEN'=$client.Token};SkipHttpErrorCheck=$true}
 if($null-ne$body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $body -Compress}
 $response=Invoke-WebRequest @args
 if([int]$response.StatusCode-ne$expected){throw "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"}
}
$a=New-Client;$b=New-Client;$recover=New-Client
try {
 $options=Invoke-RestMethod "$base/auth/registration-options"
 if(!$options.enabled -or !$options.testMode){throw 'This test requires registration and test mode; it does not change settings.'}
 [void](Call-Api $a POST '/auth/register' @{username=$nameA;password=$password;qq='12345';code='0000'})
 [void](Call-Api $b POST '/auth/register' @{username=$nameB;password=$password;qq='12345';code='0000'})
 [void](Call-Api $a POST '/auth/login' @{username=$nameA;password=$password})
 [void](Call-Api $recover POST '/auth/password-reset' @{username=$nameA;code='0000';password=$newPassword} 400)
 [void](Call-Api $recover POST '/auth/password-reset/code' @{username=$nameA})
 [void](Call-Api $recover POST '/auth/password-reset/code' @{username=$nameA} 429)
 [void](Call-Api $recover POST '/auth/password-reset' @{username=$nameA;code='1111';password=$newPassword} 400)
 [void](Call-Api $recover POST '/auth/password-reset' @{username=$nameA;code='0000';password=$newPassword})
 [void](Call-Api $recover POST '/auth/password-reset' @{username=$nameA;code='0000';password=$newPassword} 400)
 [void](Call-Api $a GET '/member/works' $null 401)
 [void](Call-Api $recover POST '/auth/login' @{username=$nameA;password=$password} 401)
 [void](Call-Api $recover POST '/auth/login' @{username=$nameA;password=$newPassword})
 [void](Call-Api $b POST '/auth/login' @{username=$nameB;password=$password})
 Write-Host 'PASS: reusable test QQ, request before reset, rate limit, incorrect and one-use codes, old session invalidation, and new password login.'
} finally {
 $env:MYSQL_PWD=$props['spring.datasource.password']
 try {
  $sql="DELETE FROM qq_invitation_bindings WHERE qq_number='12345' AND member_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB')); DELETE FROM members WHERE username IN ('$nameA','$nameB') AND qq_number='12345';"
  & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=haiyan_app --database=haiyan_chuanxin --execute=$sql
  if($LASTEXITCODE-ne0){throw 'Temporary password reset test cleanup failed'}
 } finally {Remove-Item Env:MYSQL_PWD}
}
