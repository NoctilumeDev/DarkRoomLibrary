#requires -Version 7.0

[CmdletBinding()]
param(
    [string]$ApiBaseUrl = "http://localhost:20606/api/dark-room-library/v1",
    [string]$AdminAccount = "drl_root_aurora",
    [string]$AdminPassword = $(if ($env:DRL_DEMO_ADMIN_PASSWORD) {
        $env:DRL_DEMO_ADMIN_PASSWORD
    } else {
        $env:DRL_DEMO_PASSWORD
    }),
    [string]$ReaderAccount = "drl_reader_yandeng",
    [string]$CoordinatorAccount = "drl_keeper_qingwu",
    [string]$BookName = "暗室藏书",
    [string]$ReaderAvatarPath,
    [string]$CoordinatorAvatarPath,
    [string]$BookCoverPath
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "Set DRL_DEMO_PASSWORD or DRL_DEMO_ADMIN_PASSWORD before seeding demo media."
}
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$mediaRoot = Join-Path $projectRoot "frontend\dark-room-library-web\public\demo-media"
$apiRoot = $ApiBaseUrl.TrimEnd("/")

if ([string]::IsNullOrWhiteSpace($ReaderAvatarPath)) {
    $ReaderAvatarPath = Join-Path $mediaRoot "reader-avatar.webp"
}
if ([string]::IsNullOrWhiteSpace($CoordinatorAvatarPath)) {
    $CoordinatorAvatarPath = Join-Path $mediaRoot "coordinator-avatar.webp"
}
if ([string]::IsNullOrWhiteSpace($BookCoverPath)) {
    $BookCoverPath = Join-Path $mediaRoot "dark-room-library-cover.webp"
}

function Get-ApiUri {
    param([Parameter(Mandatory)][string]$Path)
    return "$apiRoot/$($Path.TrimStart('/'))"
}

function Assert-ApiSuccess {
    param(
        [Parameter(Mandatory)]$Response,
        [Parameter(Mandatory)][string]$Operation
    )
    if ($null -eq $Response -or [int]$Response.code -ne 200) {
        $message = if ($null -ne $Response) { [string]$Response.msg } else { "empty response" }
        throw "$Operation failed: $message"
    }
    return $Response
}

function Invoke-JsonApi {
    param(
        [Parameter(Mandatory)][ValidateSet("GET", "POST", "PUT", "DELETE")][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        $Body,
        [string]$Token
    )
    $parameters = @{
        Uri = Get-ApiUri $Path
        Method = $Method
    }
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $parameters.Headers = @{ Authorization = "Bearer $Token" }
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Get-CaptchaAnswer {
    param([Parameter(Mandatory)][string]$Expression)
    $match = [regex]::Match($Expression, '^\s*(-?\d+)\s*([+\-×xX*])\s*(-?\d+)')
    if (-not $match.Success) {
        throw "Unsupported captcha expression: $Expression"
    }
    $left = [int]$match.Groups[1].Value
    $operator = $match.Groups[2].Value
    $right = [int]$match.Groups[3].Value
    switch ($operator) {
        "+" { return $left + $right }
        "-" { return $left - $right }
        default { return $left * $right }
    }
}

function Get-LoginToken {
    $captcha = Assert-ApiSuccess `
        (Invoke-JsonApi -Method GET -Path "/captcha/generate") `
        "Generate captcha"
    $answer = Get-CaptchaAnswer $captcha.data.expression
    $login = Assert-ApiSuccess `
        (Invoke-JsonApi -Method POST -Path "/user/login" -Body @{
            userAccount = $AdminAccount
            userPwd = $AdminPassword
            captchaId = $captcha.data.captchaId
            captchaAnswer = $answer
        }) `
        "Login as $AdminAccount"
    if ([string]::IsNullOrWhiteSpace([string]$login.data.token)) {
        throw "Login response did not contain a token."
    }
    return [string]$login.data.token
}

function Get-SingleEntity {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$Query,
        [Parameter(Mandatory)][string]$Description,
        [Parameter(Mandatory)][string]$Token
    )
    $response = Assert-ApiSuccess `
        (Invoke-JsonApi -Method POST -Path $Path -Body $Query -Token $Token) `
        "Query $Description"
    $items = @($response.data)
    if ($items.Count -ne 1) {
        throw "Expected exactly one $Description, found $($items.Count)."
    }
    return $items[0]
}

function Send-MediaFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Token
    )
    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $response = Invoke-RestMethod `
        -Uri (Get-ApiUri "/file/upload") `
        -Method POST `
        -Headers @{ Authorization = "Bearer $Token" } `
        -Form @{ file = Get-Item -LiteralPath $resolvedPath }
    $result = Assert-ApiSuccess $response "Upload $(Split-Path -Leaf $resolvedPath)"
    return [string]$result.data
}

function Get-FileNameFromUrl {
    param([Parameter(Mandatory)][string]$Url)
    $match = [regex]::Match($Url, '[?&]fileName=([^&]+)')
    if (-not $match.Success) {
        throw "Uploaded file URL does not contain fileName: $Url"
    }
    return [uri]::UnescapeDataString($match.Groups[1].Value)
}

function Assert-BoundFile {
    param(
        [Parameter(Mandatory)][string]$FileUrl,
        [Parameter(Mandatory)][string]$ExpectedRefType,
        [Parameter(Mandatory)][int]$ExpectedRefId,
        [Parameter(Mandatory)][string]$Token
    )
    $fileName = Get-FileNameFromUrl $FileUrl
    $response = Assert-ApiSuccess `
        (Invoke-JsonApi -Method POST -Path "/file/query" -Body @{
            current = 1
            size = 10
            fileName = $fileName
        } -Token $Token) `
        "Verify stored file $fileName"
    $files = @($response.data)
    if ($files.Count -ne 1) {
        throw "Expected one stored_file record for $fileName, found $($files.Count)."
    }
    $file = $files[0]
    $bindingIsInvalid = [int]$file.status -ne 1 `
        -or [string]$file.refType -ne $ExpectedRefType `
        -or [int]$file.refId -ne $ExpectedRefId
    if ($bindingIsInvalid) {
        throw "File $fileName was not bound as $ExpectedRefType/$ExpectedRefId."
    }
    return $file
}

foreach ($mediaPath in @($ReaderAvatarPath, $CoordinatorAvatarPath, $BookCoverPath)) {
    if (-not (Test-Path -LiteralPath $mediaPath -PathType Leaf)) {
        throw "Demo media file is missing: $mediaPath"
    }
}

$token = Get-LoginToken
$reader = Get-SingleEntity `
    -Path "/user/query" `
    -Query @{ current = 1; size = 10; userAccount = $ReaderAccount } `
    -Description "reader account $ReaderAccount" `
    -Token $token
$coordinator = Get-SingleEntity `
    -Path "/user/query" `
    -Query @{ current = 1; size = 10; userAccount = $CoordinatorAccount } `
    -Description "coordinator account $CoordinatorAccount" `
    -Token $token
$book = Get-SingleEntity `
    -Path "/book/query" `
    -Query @{ current = 1; size = 10; name = $BookName } `
    -Description "book $BookName" `
    -Token $token

$readerAvatarUrl = Send-MediaFile -Path $ReaderAvatarPath -Token $token
Assert-ApiSuccess `
    (Invoke-JsonApi -Method PUT -Path "/user/backUpdate" -Body @{
        id = [int]$reader.id
        userAvatar = $readerAvatarUrl
    } -Token $token) `
    "Bind reader avatar" | Out-Null
$readerFile = Assert-BoundFile `
    -FileUrl $readerAvatarUrl `
    -ExpectedRefType "user_avatar" `
    -ExpectedRefId ([int]$reader.id) `
    -Token $token

$coordinatorAvatarUrl = Send-MediaFile -Path $CoordinatorAvatarPath -Token $token
Assert-ApiSuccess `
    (Invoke-JsonApi -Method PUT -Path "/user/backUpdate" -Body @{
        id = [int]$coordinator.id
        userAvatar = $coordinatorAvatarUrl
    } -Token $token) `
    "Bind coordinator avatar" | Out-Null
$coordinatorFile = Assert-BoundFile `
    -FileUrl $coordinatorAvatarUrl `
    -ExpectedRefType "user_avatar" `
    -ExpectedRefId ([int]$coordinator.id) `
    -Token $token

$bookCoverUrl = Send-MediaFile -Path $BookCoverPath -Token $token
Assert-ApiSuccess `
    (Invoke-JsonApi -Method PUT -Path "/book/update" -Body @{
        id = [int]$book.id
        version = [int]$book.version
        name = [string]$book.name
        author = [string]$book.author
        isbn = [string]$book.isbn
        publisher = [string]$book.publisher
        category = [string]$book.category
        totalCount = [int]$book.totalCount
        availableCount = [int]$book.availableCount
        cover = $bookCoverUrl
        description = [string]$book.description
        bookshelfId = $book.bookshelfId
    } -Token $token) `
    "Bind book cover" | Out-Null
$coverFile = Assert-BoundFile `
    -FileUrl $bookCoverUrl `
    -ExpectedRefType "book_cover" `
    -ExpectedRefId ([int]$book.id) `
    -Token $token

[PSCustomObject]@{
    Reader = "$($reader.userName) <$ReaderAccount>"
    ReaderAvatar = $readerAvatarUrl
    ReaderFileStatus = $readerFile.status
    Coordinator = "$($coordinator.userName) <$CoordinatorAccount>"
    CoordinatorAvatar = $coordinatorAvatarUrl
    CoordinatorFileStatus = $coordinatorFile.status
    Book = $book.name
    BookCover = $bookCoverUrl
    BookCoverFileStatus = $coverFile.status
}
