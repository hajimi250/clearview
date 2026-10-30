$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$distDir = Join-Path $PSScriptRoot "dist"
$fileListPath = Join-Path $distDir "package-filelist.txt"
$zipPath = Join-Path $distDir "shennong-zhijian-source-20260429.zip"

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if (Test-Path $fileListPath) {
    Remove-Item -LiteralPath $fileListPath -Force
}
if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

$paths = New-Object System.Collections.Generic.List[string]

function Get-RelativePathCompat {
    param(
        [string]$BasePath,
        [string]$TargetPath
    )

    $baseUri = New-Object System.Uri(($BasePath.TrimEnd("\/") + "\"))
    $targetUri = New-Object System.Uri($TargetPath)
    return [System.Uri]::UnescapeDataString(
        $baseUri.MakeRelativeUri($targetUri).ToString()
    ).Replace("/", "\")
}

function Add-RepoFiles {
    param(
        [string]$BaseRelative,
        [string[]]$ExcludeDirNames = @(),
        [string[]]$ExcludeFileRegexes = @()
    )

    $basePath = Join-Path $projectRoot $BaseRelative
    if (-not (Test-Path $basePath)) {
        return
    }

    Get-ChildItem -Path $basePath -Recurse -File | Where-Object {
        $relative = Get-RelativePathCompat -BasePath $projectRoot -TargetPath $_.FullName
        $segments = $relative -split "[\\/]"
        -not ($segments | Where-Object { $ExcludeDirNames -contains $_ }) -and
        -not ($ExcludeFileRegexes | Where-Object { $relative -match $_ })
    } | ForEach-Object {
        $paths.Add((Get-RelativePathCompat -BasePath $projectRoot -TargetPath $_.FullName))
    }
}

$topFiles = @(
    "README.md",
    "android-app/.editorconfig",
    "android-app/.gitattributes",
    "android-app/.gitignore",
    "android-app/build.gradle.kts",
    "android-app/check-sdk-health.sh",
    "android-app/gradle.properties",
    "android-app/gradlew",
    "android-app/gradlew.bat",
    "android-app/gradlew-wsl.sh",
    "android-app/local.properties.windows.example",
    "android-app/local.properties.wsl.example",
    "android-app/README.md",
    "android-app/settings.gradle.kts",
    "android-app/start-mobile-backend.ps1",
    "cdc_backend/README.md",
    "cdc_backend/FRONTEND_INTEGRATION_DISEASE_SEGMENTATION.md",
    "cdc_backend/FRONTEND_HANDOFF_MANUAL.md",
    "cdc_backend/device.py",
    "cdc_backend/http_service.py",
    "cdc_backend/infer.py",
    "cdc_backend/predictor.py",
    "cdc_backend/record_store.py",
    "cdc_backend/requirements.txt",
    "cdc_backend/segmentor.py",
    "cdc_backend/start.sh",
    "cdc_backend/stop.sh",
    "cdc_backend/weather_service.py",
    "cdc_backend/__init__.py",
    "cdc_backend/classifier/class_names.json",
    "cdc_backend/classifier/model_config.json",
    "cdc_backend/classifier/retrieval_memory.pt",
    "cdc_backend/classifier/checkpoints/best_model.pth",
    "cdc_backend/segment_models/__init__.py",
    "cdc_backend/segment_models/supervised_models.py",
    "cdc_backend/segmentor_training/deploy/swinunet_strong77_best.pt",
    "cdc_backend/segmentor_training/deploy/pvtv2b2seg_best.pt",
    "cdc_backend/segmentor_training/deploy/convnexttinyseg_best.pt",
    "cdc_backend/segmentor_training/deploy/student_swinunet_fast_best.pt",
    "cdc_backend/static/offline-fast-v1/README.md",
    "cdc_backend/static/offline-fast-v1/shennong_classifier.onnx",
    "cdc_backend/static/offline-fast-v1/student_swinunet_fast.onnx",
    "cdc_backend/DEPLOY_WINDOWS_README.md",
    "cdc_backend/start_backend_windows.ps1",
    "model/mobile/classifier/shennong_classifier.metadata.json",
    "model/mobile/classifier/shennong_classifier.onnx",
    "model/mobile/segmentor/student_swinunet_fast.metadata.json",
    "model/mobile/segmentor/student_swinunet_fast.onnx"
)

foreach ($file in $topFiles) {
    $fullPath = Join-Path $projectRoot $file
    if (Test-Path $fullPath) {
        $paths.Add($file)
    }
}

$androidExcludeDirs = @(".gradle", ".idea", ".kotlin", ".omx", "build", "temp", "dist")
$androidExcludeFiles = @(
    "[\\\\/]local\\.properties$",
    "^android-app[\\\\/]app[\\\\/]release[\\\\/]",
    "\\.apk$",
    "\\.out$",
    "\\.err$",
    "\\.log$",
    "\\.iml$"
)

Add-RepoFiles -BaseRelative "android-app/gradle" -ExcludeDirNames $androidExcludeDirs -ExcludeFileRegexes $androidExcludeFiles
Add-RepoFiles -BaseRelative "android-app/app" -ExcludeDirNames $androidExcludeDirs -ExcludeFileRegexes $androidExcludeFiles
Add-RepoFiles -BaseRelative "android-app/core" -ExcludeDirNames $androidExcludeDirs -ExcludeFileRegexes $androidExcludeFiles
Add-RepoFiles -BaseRelative "android-app/feature" -ExcludeDirNames $androidExcludeDirs -ExcludeFileRegexes $androidExcludeFiles

$uniquePaths = $paths | Sort-Object -Unique
[System.IO.File]::WriteAllLines(
    $fileListPath,
    $uniquePaths,
    (New-Object System.Text.UTF8Encoding($false))
)

Push-Location $projectRoot
try {
    tar -a -cf $zipPath -T $fileListPath
    if ($LASTEXITCODE -ne 0) {
        throw "tar failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Output "ZIP_CREATED`t$zipPath"
Write-Output "FILE_COUNT`t$($uniquePaths.Count)"
Write-Output "ZIP_SIZE`t$((Get-Item $zipPath).Length)"
