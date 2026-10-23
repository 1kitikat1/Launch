$ErrorActionPreference = 'Stop'
$appDir = 'C:\Users\Kitikat\Desktop\Loader\build\jpackage\NemesisLauncher'
$zip = 'C:\Users\Kitikat\AppData\Local\Temp\nemesis-update-test-18029596846428792380.zip'

# ждём, пока лаунчер полностью завершится
do {
    Start-Sleep -Milliseconds 700
    $busy = @()
    $busy += Get-Process -Name 'NemesisLauncher' -ErrorAction SilentlyContinue
    $busy += Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
             Where-Object { $_.CommandLine -like ('*' + $appDir + '*') }
} while ($busy.Count -gt 0)

$tmp = Join-Path $env:TEMP 'nemesis-update-unpack'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
Expand-Archive -Path $zip -DestinationPath $tmp -Force

$src = $tmp
if (Test-Path (Join-Path $tmp 'NemesisLauncher')) {
    $src = Join-Path $tmp 'NemesisLauncher'
}
robocopy $src $appDir /MIR /NFL /NDL /NJH /NJS /R:2 /W:2 | Out-Null

if (Test-Path (Join-Path $appDir 'NemesisLauncher.exe')) {
    Start-Process -FilePath (Join-Path $appDir 'NemesisLauncher.exe')
}

Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $zip -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force -ErrorAction SilentlyContinue
