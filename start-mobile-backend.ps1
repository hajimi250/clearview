param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8000,
    [string]$PythonExe = "python",
    [string]$AdbPath,
    [switch]$ReverseOnly
)

$ErrorActionPreference = "Stop"

function Resolve-PythonExe {
    param([string]$Requested)

    $command = Get-Command -Name $Requested -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command -and $command.Source -notlike "*\WindowsApps\python*.exe") {
        return $command.Source
    }

    throw "Python not found. Activate the clearview conda environment or pass -PythonExe with its python.exe path."
}

function Get-SdkAdbPath {
    param([string]$SdkDir)

    if ($SdkDir -and (Test-Path -LiteralPath $SdkDir -PathType Container)) {
        $candidate = Join-Path $SdkDir "platform-tools\adb.exe"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    return $null
}

function Resolve-AdbPath {
    param(
        [string]$Requested,
        [string]$LocalPropertiesPath
    )

    if ($Requested) {
        if (-not (Test-Path -LiteralPath $Requested -PathType Leaf)) {
            throw "The specified -AdbPath does not exist: $Requested"
        }
        return (Resolve-Path -LiteralPath $Requested).ProviderPath
    }

    foreach ($sdkDir in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        $candidate = Get-SdkAdbPath -SdkDir $sdkDir
        if ($candidate) {
            return $candidate
        }
    }

    if (Test-Path -LiteralPath $LocalPropertiesPath -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $LocalPropertiesPath -Encoding UTF8 |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($sdkLine -match '^\s*sdk\.dir\s*=\s*(.*)$') {
            $sdkDir = [regex]::Replace($Matches[1].Trim(), '\\([\\: =])', '$1')
            $candidate = Get-SdkAdbPath -SdkDir $sdkDir
            if ($candidate) {
                return $candidate
            }
        }
    }

    $command = Get-Command -Name adb.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command) {
        return $command.Source
    }
    throw "ADB not found. Install Android SDK Platform-Tools or pass -AdbPath with the adb.exe path."
}

function Invoke-UsbAdb {
    param([string[]]$Arguments)

    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell wraps native stderr as errors, even for successful ADB startup.
        $ErrorActionPreference = "Continue"
        $output = & $script:AdbPath -d @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    if ($exitCode -ne 0) {
        throw "ADB failed (exit $exitCode): $($output -join [Environment]::NewLine)`nConnect exactly one USB phone, enable USB debugging, unlock it and accept the authorization prompt."
    }
    return @($output | ForEach-Object { ([string]$_).Trim() })
}

$AdbPath = Resolve-AdbPath -Requested $AdbPath -LocalPropertiesPath (Join-Path $PSScriptRoot "local.properties")
if (-not $ReverseOnly) {
    $PythonExe = Resolve-PythonExe -Requested $PythonExe
}

$deviceState = @(Invoke-UsbAdb -Arguments @("get-state"))
if ($deviceState -notcontains "device") {
    throw "The USB phone is not ready. Check its USB debugging authorization and reconnect."
}

$null = Invoke-UsbAdb -Arguments @("reverse", "tcp:8000", "tcp:$Port")
$mappings = @(Invoke-UsbAdb -Arguments @("reverse", "--list"))
if (-not ($mappings -match "^\S+\s+tcp:8000\s+tcp:$Port$")) {
    throw "ADB did not report the expected reverse mapping: phone tcp:8000 -> PC tcp:$Port."
}

Write-Host "USB reverse verified: phone tcp:8000 -> PC tcp:$Port" -ForegroundColor Green
Write-Host "Debug App service address: http://127.0.0.1:8000/"
Write-Host "local.properties was not changed. No LAN address or firewall rule is needed for this USB path."
Write-Host "After reconnecting USB or restarting ADB, run this script again with -ReverseOnly."
if ($Port -ne 8000) {
    Write-Host "Keep -Port $Port when restoring the mapping; the phone still uses port 8000."
}

if ($ReverseOnly) {
    Write-Host "Only the USB mapping was checked; backend availability was not checked. Use the App service-status check."
    exit 0
}

Write-Host "Starting ClearView with $PythonExe on 127.0.0.1:$Port" -ForegroundColor Green
Write-Host "If the backend is already running, use -ReverseOnly instead; no existing process will be stopped."
Write-Host "Use the clearview conda environment. The assistant and first-time model downloads still require PC internet access."

$backendExitCode = 1
Push-Location (Join-Path $PSScriptRoot "server")
try {
    & $PythonExe "run.py" --host 127.0.0.1 --port $Port
    $backendExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($backendExitCode -ne 0) {
    Write-Host "ClearView exited with code $backendExitCode. Check the Python environment and port; use -ReverseOnly if a backend is already running." -ForegroundColor Red
}
exit $backendExitCode
