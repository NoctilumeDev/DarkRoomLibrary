[CmdletBinding()]
param(
    [string]$OutputDirectory,
    [string]$ArchiveName
)

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $scriptRoot "..\release"
}
if ([string]::IsNullOrWhiteSpace($ArchiveName)) {
    $ArchiveName = "DarkRoomLibrary-source-{0}.zip" -f (Get-Date -Format "yyyyMMdd")
}

$projectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
$projectPrefix = $projectRoot.TrimEnd('\') + '\'

if ($outputRoot -eq $projectRoot -or -not $outputRoot.StartsWith($projectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputDirectory must be a child of the project root: $projectRoot"
}

$stagingRoot = Join-Path $outputRoot "staging"
$stagingProject = Join-Path $stagingRoot "DarkRoomLibrary"
$archivePath = Join-Path $outputRoot $ArchiveName

if (Test-Path -LiteralPath $stagingRoot) {
    $resolvedStaging = [System.IO.Path]::GetFullPath($stagingRoot)
    $outputPrefix = $outputRoot.TrimEnd('\') + '\'
    if (-not $resolvedStaging.StartsWith($outputPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe staging path: $resolvedStaging"
    }
    Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
}

New-Item -ItemType Directory -Path $stagingProject -Force | Out-Null

$deliveryItems = @(
    ".gitattributes",
    ".gitignore",
    "CONTRIBUTING.md",
    "NOTICE.md",
    "SECURITY.md",
    "README.md",
    "backend\dark-room-library-api\pom.xml",
    "backend\dark-room-library-api\src",
    "docs",
    "frontend\dark-room-library-web\.env.development",
    "frontend\dark-room-library-web\.env.production.example",
    "frontend\dark-room-library-web\eslint.config.mjs",
    "frontend\dark-room-library-web\index.html",
    "frontend\dark-room-library-web\package-lock.json",
    "frontend\dark-room-library-web\package.json",
    "frontend\dark-room-library-web\public",
    "frontend\dark-room-library-web\src",
    "frontend\dark-room-library-web\tests",
    "frontend\dark-room-library-web\vite.config.mjs",
    "scripts",
    "sql"
)

foreach ($relativePath in $deliveryItems) {
    $source = Join-Path $projectRoot $relativePath
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Required delivery item is missing: $relativePath"
    }

    $destination = Join-Path $stagingProject $relativePath
    $destinationParent = Split-Path -Parent $destination
    New-Item -ItemType Directory -Path $destinationParent -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -Recurse -Force
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}

Compress-Archive -LiteralPath $stagingProject -DestinationPath $archivePath -CompressionLevel Optimal
Remove-Item -LiteralPath $stagingRoot -Recurse -Force

$archive = Get-Item -LiteralPath $archivePath
$hash = Get-FileHash -LiteralPath $archivePath -Algorithm SHA256
$hashPath = "$archivePath.sha256"
Set-Content -LiteralPath $hashPath -Value ("{0}  {1}" -f $hash.Hash.ToLowerInvariant(), $archive.Name) -Encoding ascii

[PSCustomObject]@{
    Archive = $archive.FullName
    SizeMB = [math]::Round($archive.Length / 1MB, 2)
    Sha256 = $hash.Hash.ToLowerInvariant()
    ChecksumFile = $hashPath
    Excluded = "node_modules, target, dist, test-results, upload, logs, IDE files"
}
