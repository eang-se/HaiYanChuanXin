$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,10)
$username='quicktest_'+$runId
$password=[guid]::NewGuid().ToString('N')
$properties=@{}
Get-Content -LiteralPath (Join-Path $root '.local/application-local.properties') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$properties[$matches[1]]=$matches[2]}}
function New-Client {$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession;$csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session;return @{Session=$session;Token=$csrf.token}}
function Call-Api($client,[string]$method,[string]$path,$body=$null,[int]$expected=200){$args=@{Uri=$base+$path;Method=$method;WebSession=$client.Session;Headers=@{'X-CSRF-TOKEN'=$client.Token};SkipHttpErrorCheck=$true};if($null-ne$body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $body -Depth 10 -Compress};$response=Invoke-WebRequest @args;if([int]$response.StatusCode-ne$expected){throw "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"};if($response.Content -and $response.Headers['Content-Type'] -like '*json*'){return $response.Content|ConvertFrom-Json}}
function Assert-True($condition,[string]$message){if(!$condition){throw $message}}
$client=New-Client;$created=$false
try{
 $options=Invoke-RestMethod "$base/auth/registration-options"
 if(!$options.enabled-or!$options.testMode){throw 'This test requires open test registration; site settings were not changed.'}
 [void](Call-Api $client POST '/auth/register' @{username=$username;password=$password;qq='12345';code='0000'});$created=$true
 [void](Call-Api $client POST '/auth/login' @{username=$username;password=$password;remember=$true})
 $stored=$client.Session.Cookies.GetCookies([uri]("$base/auth/remembered"))['haiyan_remember']
 Assert-True ($null-ne$stored -and $stored.HttpOnly) 'Remembered cookie must be HttpOnly'
 $oldValue=$stored.Value
 $shown=Call-Api $client GET '/auth/remembered'
 Assert-True ($shown.username-eq$username) 'Remembered account not shown'
 [void](Call-Api $client POST '/auth/logout')
 $client.Token=(Invoke-RestMethod "$base/auth/csrf" -WebSession $client.Session).token
 [void](Call-Api $client POST '/auth/quick-login')
 $current=Call-Api $client GET '/auth/me';Assert-True ($current.username-eq$username) 'Quick login failed'
 $rotated=$client.Session.Cookies.GetCookies([uri]("$base/auth/remembered"))['haiyan_remember']
 Assert-True ($rotated.Value-ne$oldValue) 'Quick login did not rotate the device token'
 $replay=New-Client;$replay.Session.Cookies.Add((New-Object System.Net.Cookie('haiyan_remember',$oldValue,'/api/auth','127.0.0.1')))
 [void](Call-Api $replay POST '/auth/quick-login' $null 401)
 [void](Call-Api $client POST '/auth/remembered/forget')
 $forgot=Call-Api $client GET '/auth/remembered';Assert-True (!$forgot.username) 'Forgetting device did not revoke token'
 Write-Host 'PASS: remembered account, HttpOnly cookie, logout and quick login, token rotation, replay rejection and forgetting the device.'
}finally{
 if($created){$env:MYSQL_PWD=$properties['spring.datasource.password'];try{$sql="DELETE FROM remembered_devices WHERE member_id IN (SELECT id FROM members WHERE username='$username'); DELETE FROM qq_invitation_bindings WHERE qq_number='12345' AND member_id IN (SELECT id FROM members WHERE username='$username'); DELETE FROM members WHERE username='$username' AND qq_number='12345';";& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=haiyan_app --database=haiyan_chuanxin ('--execute='+$sql);if($LASTEXITCODE-ne0){throw 'Quick login test cleanup failed'}}finally{Remove-Item Env:MYSQL_PWD}}
}
