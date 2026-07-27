[CmdletBinding()]
param(
    [string]$OutputDirectory,
    [string]$ArchiveName
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot "release"
} elseif (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot $OutputDirectory
}

if ([string]::IsNullOrWhiteSpace($ArchiveName)) {
    $ArchiveName = "release.zip"
}

if ([System.IO.Path]::GetFileName($ArchiveName) -ne $ArchiveName) {
    throw "ArchiveName must be a file name without directory components."
}

if (-not $ArchiveName.EndsWith(".zip", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "ArchiveName must use the .zip extension."
}

$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
$projectPrefix = $projectRoot.TrimEnd('\') + '\'

if ($outputRoot -eq $projectRoot -or -not $outputRoot.StartsWith($projectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputDirectory must be a child of the project root: $projectRoot"
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git is required to build the release archive."
}

$gitRoot = (& git -C $projectRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Unable to locate the Git repository."
}

$normalizedGitRoot = [System.IO.Path]::GetFullPath($gitRoot).TrimEnd('\')
if (-not $normalizedGitRoot.Equals($projectRoot.TrimEnd('\'), [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The script must run from the DarkRoomLibrary repository root."
}

$workingTreeState = @(& git -C $projectRoot status --porcelain=v1 --untracked-files=all) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect the Git worktree."
}
if (-not [string]::IsNullOrWhiteSpace($workingTreeState)) {
    throw "The Git worktree is not clean. Commit or remove pending files before packaging.`n$workingTreeState"
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$archivePath = Join-Path $outputRoot $ArchiveName
$hashPath = "$archivePath.sha256"

if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}
if (Test-Path -LiteralPath $hashPath) {
    Remove-Item -LiteralPath $hashPath -Force
}

& git -C $projectRoot archive --format=zip --prefix=DarkRoomLibrary/ "--output=$archivePath" HEAD
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $archivePath)) {
    throw "Git failed to create the release archive."
}

$archive = Get-Item -LiteralPath $archivePath
$hash = Get-FileHash -LiteralPath $archivePath -Algorithm SHA256
Set-Content -LiteralPath $hashPath -Value ("{0}  {1}" -f $hash.Hash.ToLowerInvariant(), $archive.Name) -Encoding ascii
$sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
$trackedFileCount = @(& git -C $projectRoot ls-tree -r --name-only HEAD).Count

[PSCustomObject]@{
    Archive = $archive.FullName
    SizeMB = [math]::Round($archive.Length / 1MB, 2)
    Sha256 = $hash.Hash.ToLowerInvariant()
    ChecksumFile = $hashPath
    SourceCommit = $sourceCommit
    TrackedFiles = $trackedFileCount
    Contents = "Committed Git files from HEAD only"
}
