param([Parameter(Mandatory=$true)][string]$Adb,[string]$Serial='emulator-5554')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'wait-android.ps1') -Adb $Adb -Serial $Serial
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$localRoot=Join-Path $projectRoot '.local-spendi'
$settings=Get-Content -LiteralPath (Join-Path $localRoot 'connection.json') -Raw | ConvertFrom-Json
$apkFolders=@((Join-Path $projectRoot 'app/build/outputs/apk'),(Join-Path $projectRoot 'app/build/intermediates/apk')) | Where-Object {Test-Path -LiteralPath $_}
$apks=Get-ChildItem -LiteralPath $apkFolders -File -Filter '*.apk' -Recurse
$app=$apks | Where-Object Name -eq 'app-debug.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$test=$apks | Where-Object Name -eq 'app-debug-androidTest.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if(!$app -or !$test){throw 'Build :app:assembleDebug :app:assembleDebugAndroidTest before running this script.'}
Write-Output 'Installing the debug app without clearing its data...'
& $Adb -s $Serial install -r $app.FullName | Out-Null
if($LASTEXITCODE -ne 0){throw 'Debug app installation failed.'}
Write-Output 'Installing the instrumentation APK...'
& $Adb -s $Serial install -r $test.FullName | Out-Null
if($LASTEXITCODE -ne 0){throw 'Test APK installation failed.'}
Write-Output 'Running Android integration against the local API...'
$result=& $Adb -s $Serial shell am instrument -w -r -e class com.example.spendi.CloudIntegrationTest -e baseUrl $settings.emulatorUrl -e accessToken $settings.token com.example.spendi.test/androidx.test.runner.AndroidJUnitRunner
$text=($result -join "`n").Replace($settings.token,'[redacted]')
[IO.File]::WriteAllText((Join-Path $localRoot 'android-test.log'),$text)
if($LASTEXITCODE -ne 0 -or $text -notmatch 'OK \(1 test\)'){throw "Android integration failed. Check $localRoot/android-test.log"}
Write-Output 'OK (1 Android integration test): encrypted credentials, create/retry, list, edit, stale-version rejection, delete, summary, offline failure, and Room preservation.'
