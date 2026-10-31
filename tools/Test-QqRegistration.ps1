# Tests a temporary locally generated verification code; does not send email.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,10)
$nameA='qqtesta_'+$runId;$nameB='qqtestb_'+$runId
$password=[guid]::NewGuid().ToString('N')
$code=[Security.Cryptography.RandomNumberGenerator]::GetInt32(0,1000000).ToString('D6')
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($code))).ToLowerInvariant()
$settings=@{}
Get-Content -LiteralPath (Join-Path $root '.local/application-local.properties') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$settings[$matches[1]]=$matches[2]}}
function New-Client {$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession;$csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session;return @{Session=$session;Token=$csrf.token}}
function Call-Api($client,[string]$method,[string]$path,$body=$null,[int]$expected=200){
 $args=@{Uri=$base+$path;Method=$method;WebSession=$client.Session;Headers=@{'X-CSRF-TOKEN'=$client.Token};SkipHttpErrorCheck=$true}
 if($null-ne$body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $body -Depth 10 -Compress}
 $response=Invoke-WebRequest @args
 if([int]$response.StatusCode-ne$expected){throw "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"}
 if($response.Content -and $response.Headers['Content-Type'] -like '*json*'){return $response.Content|ConvertFrom-Json}
}
function Assert-True($condition,[string]$message){if(!$condition){throw $message}}
$admin=New-Client;$first=New-Client;$second=New-Client
$qq=$null;$optionPath=$null;$jobs=@()
try {
 [void](Call-Api $admin POST '/auth/login' @{username='admin';password=$settings['platform.bootstrap.password']})
 $options=Call-Api $first GET '/auth/registration-options'
 Assert-True $options.enabled 'Registration is closed; test will not change administrator settings.'
 $existing=@(Call-Api $admin GET '/admin/registration/qq-allowlist' | ForEach-Object {$_.qqNumber})
 do {$qq=[Security.Cryptography.RandomNumberGenerator]::GetInt32(100000000,999999999).ToString()} while($existing -contains $qq)
 [void](Call-Api $admin POST '/admin/registration/qq-allowlist' @{qqNumbers=@($qq)})
 $optionPath=Join-Path $root ('.local/qq-registration-test-'+$runId+'.cnf')
 [IO.File]::WriteAllText($optionPath,"[client]`nuser=haiyan_app`npassword=$($settings['spring.datasource.password'])`nhost=127.0.0.1`nport=3306`nprotocol=TCP`ndatabase=haiyan_chuanxin`n",[Text.UTF8Encoding]::new($false))
 $sql="INSERT INTO registration_codes(qq_number,code_hash,expires_at,sent_at,attempts,consumed) VALUES('$qq','$hash',DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 MINUTE),CURRENT_TIMESTAMP,0,FALSE);"
 $sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' ('--defaults-extra-file='+$optionPath) --default-character-set=utf8mb4
 if($LASTEXITCODE-ne0){throw 'Could not prepare temporary verification code'}
 for($i=0;$i-lt5;$i++){[void](Call-Api $first POST '/auth/register' @{username=$nameA;password=$password;qq=$qq;code='wrong'} 400)}
 [void](Call-Api $first POST '/auth/register' @{username=$nameA;password=$password;qq=$qq;code=$code} 400)
 "UPDATE registration_codes SET attempts=0 WHERE qq_number='$qq';" | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' ('--defaults-extra-file='+$optionPath)
 if($LASTEXITCODE-ne0){throw 'Could not reset temporary test attempt count'}
 $gate=[Threading.ManualResetEventSlim]::new($false)
 foreach($pair in @(@($first,$nameA),@($second,$nameB))){
  $jobs+=Start-ThreadJob -ArgumentList @($base,$pair[0].Session,$pair[0].Token,$pair[1],$password,$qq,$code,$gate) -ScriptBlock {
   param($baseUri,$webSession,$csrfToken,$username,$testPassword,$testQq,$testCode,$startGate)
   $body=ConvertTo-Json -Compress @{username=$username;password=$testPassword;qq=$testQq;code=$testCode}
   $startGate.Wait()
   $response=Invoke-WebRequest ($baseUri+'/auth/register') -Method Post -WebSession $webSession -Headers @{'X-CSRF-TOKEN'=$csrfToken} -ContentType 'application/json' -Body $body -SkipHttpErrorCheck -TimeoutSec 20
   return [int]$response.StatusCode
  }
 }
 $gate.Set()
 $finished=@($jobs | Wait-Job -Timeout 25)
 Assert-True ($finished.Count-eq2) 'Parallel registration requests did not finish'
 $status=@($jobs | Receive-Job | Sort-Object)
 Assert-True ($status.Count-eq2 -and $status[0]-eq200 -and $status[1]-eq400) ('Expected exactly one success and one rejection; got '+($status -join ','))
 [void](Call-Api $first POST '/auth/register' @{username=('qqtestc_'+$runId);password=$password;qq=$qq;code=$code} 400)
 Write-Host 'PASS: QQ invitation bound once, failed-code attempt limit, and concurrent registration. No email sent.'
} finally {
 if($jobs.Count){$jobs | Wait-Job -Timeout 25 | Out-Null;$jobs | Remove-Job -Force -ErrorAction SilentlyContinue}
 if($optionPath -and (Test-Path -LiteralPath $optionPath) -and $qq -match '^[0-9]{9}$'){
  $sql=@"
DELETE FROM members WHERE username IN ('$nameA','$nameB') AND qq_number='$qq';
DELETE FROM qq_invitation_bindings WHERE qq_number='$qq';
DELETE FROM registration_codes WHERE qq_number='$qq';
DELETE FROM qq_allowlist WHERE qq_number='$qq';
"@
  try{$sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' ('--defaults-extra-file='+$optionPath);if($LASTEXITCODE-ne0){throw 'Temporary registration test cleanup failed'}}finally{Remove-Item -LiteralPath $optionPath -Force}
 }
}
