[CmdletBinding()]
param(
    [string]$DatabaseName = "dark_room_library_e2e",
    [string]$MySqlCommand = "mysql",
    [string]$HostName = "127.0.0.1",
    [int]$Port = 3306,
    [string]$User = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }),
    [string]$Password = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "root" }),
    [switch]$Reset
)

if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') {
    throw "DatabaseName may contain only letters, digits, and underscores."
}
if ($Reset -and $DatabaseName -notmatch '_e2e$') {
    throw "Reset is restricted to database names ending in _e2e."
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$bootstrapPath = Join-Path $projectRoot "sql\init-dark-room-library.sql"
$demoDataPath = Join-Path $projectRoot "sql\demo-data.sql"
if (-not (Test-Path -LiteralPath $bootstrapPath)) {
    throw "Bootstrap SQL is missing: $bootstrapPath"
}
if (-not (Test-Path -LiteralPath $demoDataPath)) {
    throw "Demo SQL is missing: $demoDataPath"
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
$sql = [System.IO.File]::ReadAllText($bootstrapPath, $utf8)
$demoSql = [System.IO.File]::ReadAllText($demoDataPath, $utf8)
$sql = $sql.Replace('`dark_room_library`', ('`{0}`' -f $DatabaseName))
$demoSql = $demoSql.Replace('`dark_room_library`', ('`{0}`' -f $DatabaseName))
$sql = "$sql`n$demoSql"
if ($Reset) {
    $sql = "DROP DATABASE IF EXISTS ``$DatabaseName``;`n$sql"
}

$previousPassword = $env:MYSQL_PWD
$tempSqlPath = Join-Path `
    ([System.IO.Path]::GetTempPath()) `
    ("dark-room-library-e2e-{0}.sql" -f [System.Guid]::NewGuid().ToString("N"))
try {
    [System.IO.File]::WriteAllText(
        $tempSqlPath,
        $sql,
        $utf8
    )
    $env:MYSQL_PWD = $Password
    $sourceCommand = "source " + $tempSqlPath.Replace("\", "/")
    & $MySqlCommand `
        "--default-character-set=utf8mb4" `
        "-h" $HostName `
        "-P" $Port `
        "-u" $User `
        "--execute" $sourceCommand
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL initialization failed with exit code $LASTEXITCODE."
    }
}
finally {
    if (Test-Path -LiteralPath $tempSqlPath) {
        Remove-Item -LiteralPath $tempSqlPath -Force
    }
    if ($null -eq $previousPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    else {
        $env:MYSQL_PWD = $previousPassword
    }
}

[PSCustomObject]@{
    Database = $DatabaseName
    Host = $HostName
    Port = $Port
    Reset = [bool]$Reset
    Bootstrap = $bootstrapPath
    DemoData = $demoDataPath
}
