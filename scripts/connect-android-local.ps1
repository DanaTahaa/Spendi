param([Parameter(Mandatory=$true)][string]$Adb,[string]$Serial='emulator-5554')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'wait-android.ps1') -Adb $Adb -Serial $Serial
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$settings=Get-Content -LiteralPath (Join-Path $projectRoot '.local-spendi/connection.json') -Raw | ConvertFrom-Json
$headers=@{Authorization=('Bearer '+$settings.token)}
Invoke-RestMethod -Uri ($settings.baseUrl+'/v1/summaries/'+(Get-Date -Format 'yyyy-MM')) -Headers $headers -TimeoutSec 8 | Out-Null
& $Adb -s $Serial shell am force-stop com.example.spendi
& $Adb -s $Serial shell am start -W -n com.example.spendi/.MainActivity | Out-Null
if($LASTEXITCODE -ne 0){throw 'Could not open the debug app.'}
$result=& $Adb -s $Serial shell am broadcast --receiver-foreground -n com.example.spendi/.DebugConnectionReceiver --es base_url $settings.emulatorUrl --es access_token $settings.token
if($LASTEXITCODE -ne 0 -or ($result -join ' ') -notmatch 'result=1'){throw 'Debug connection was not accepted. Install the debug APK and check the selected emulator.'}
Write-Output 'Debug app connected to the local API. Credentials were not printed or embedded in the APK. On-device Room data is preserved.'
