param([Parameter(Mandatory=$true)][string]$Adb,[string]$Serial='emulator-5554',[int]$TimeoutSeconds=240)
$ErrorActionPreference='Stop'
$deadline=(Get-Date).AddSeconds($TimeoutSeconds)
while((Get-Date) -lt $deadline){
    $boot=& $Adb -s $Serial shell getprop sys.boot_completed 2>$null
    if($LASTEXITCODE -eq 0 -and ($boot -join '').Trim() -eq '1'){
        $package=& $Adb -s $Serial shell pm path android 2>$null
        if($LASTEXITCODE -eq 0 -and ($package -join '') -match 'package:'){return}
    }
    Start-Sleep -Seconds 3
}
throw "Android device $Serial did not finish booting within $TimeoutSeconds seconds."
