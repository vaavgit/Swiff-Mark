param(
    [string]$PhoneIp = ""
)

$Adb = "C:\Users\Vaibhav\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$ApkPath = "C:\Users\Vaibhav\AndroidStudioProjects\FacialAttendanceSystem\app\build\outputs\apk\debug\app-debug.apk"
$IpCacheFile = "C:\Users\Vaibhav\AndroidStudioProjects\FacialAttendanceSystem\.wireless_adb_ip"

Write-Host "=== Swiff Mark Wireless Auto-Updater ===" -ForegroundColor Cyan

# 1. Check if phone is currently connected via USB to auto-enable TCP/IP 5555 and grab its Wi-Fi IP
$usbDevice = & $Adb devices | Select-String -Pattern "^[A-Za-z0-9]+\s+device$" | Where-Object { $_ -notmatch ":" }
if ($usbDevice) {
    $serial = ($usbDevice.ToString() -split "\s+")[0]
    Write-Host "Detected USB device: $serial. Enabling Wireless ADB on port 5555..." -ForegroundColor Green
    & $Adb -s $serial tcpip 5555 | Out-Null
    Start-Sleep -Seconds 2
    $ipRoute = & $Adb -s $serial shell "ip route | grep wlan0" 2>$null
    if ($ipRoute -match "src\s+(\d+\.\d+\.\d+\.\d+)") {
        $PhoneIp = $Matches[1]
        Set-Content -Path $IpCacheFile -Value $PhoneIp -NoNewline
        Write-Host "Saved phone Wi-Fi IP: $PhoneIp" -ForegroundColor Green
    }
}

# 2. Load cached IP if not passed as parameter
if ([string]::IsNullOrWhiteSpace($PhoneIp) -and (Test-Path $IpCacheFile)) {
    $PhoneIp = (Get-Content $IpCacheFile -Raw).Trim()
}

if (-not [string]::IsNullOrWhiteSpace($PhoneIp)) {
    $target = if ($PhoneIp -match ":") { $PhoneIp } else { "${PhoneIp}:5555" }
    Write-Host "Connecting wirelessly to $target..." -ForegroundColor Cyan
    & $Adb connect $target
}

# 3. Verify connected device (wireless or USB)
$activeDevices = & $Adb devices | Select-String -Pattern "\s+device$"
if (-not $activeDevices) {
    Write-Host "No active ADB device found over Wi-Fi. Users can also tap 'Install Wirelessly' inside Swiff Mark (pulls from GitHub Releases v1.1.0)." -ForegroundColor Yellow
    exit 1
}

Write-Host "Installing latest APK wirelessly ($ApkPath)..." -ForegroundColor Cyan
& $Adb install -r $ApkPath
if ($LASTEXITCODE -eq 0) {
    Write-Host "Launching Swiff Mark on phone..." -ForegroundColor Green
    & $Adb shell am start -n com.vaibhav.facialattendancesystem/.MainActivity
    Write-Host "SUCCESS: App updated and launched wirelessly!" -ForegroundColor Green
} else {
    Write-Host "ADB install failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
