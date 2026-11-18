$ErrorActionPreference = 'Stop'

$oauthVariables = @(
    'GOOGLE_CLIENT_ID',
    'GOOGLE_CLIENT_SECRET',
    'GITHUB_CLIENT_ID',
    'GITHUB_CLIENT_SECRET',
    'DISCORD_CLIENT_ID',
    'DISCORD_CLIENT_SECRET',
    'FACEBOOK_CLIENT_ID',
    'FACEBOOK_CLIENT_SECRET'
)
foreach ($name in $oauthVariables) {
    if (-not [string]::IsNullOrWhiteSpace(
        [Environment]::GetEnvironmentVariable($name, 'Process')
    )) {
        continue
    }

    $userValue = [Environment]::GetEnvironmentVariable($name, 'User')
    if (-not [string]::IsNullOrWhiteSpace($userValue)) {
        [Environment]::SetEnvironmentVariable($name, $userValue, 'Process')
    }
}

$friendlyName = 'ProAula local OAuth HTTPS'
$certificateDirectory = Join-Path $env:LOCALAPPDATA 'ProAula\certificates'
$pfxPath = Join-Path $certificateDirectory 'localhost.pfx'
$thumbprintPath = Join-Path $certificateDirectory 'localhost.thumbprint'
$passwordPath = Join-Path $certificateDirectory 'localhost.password'
$null = New-Item -ItemType Directory -Path $certificateDirectory -Force

$certificate = Get-ChildItem Cert:\CurrentUser\My |
    Where-Object { $_.FriendlyName -eq $friendlyName -and $_.NotAfter -gt (Get-Date) } |
    Sort-Object NotAfter -Descending |
    Select-Object -First 1

if ($null -eq $certificate) {
    $certificate = New-SelfSignedCertificate `
        -DnsName 'localhost' `
        -CertStoreLocation Cert:\CurrentUser\My `
        -FriendlyName $friendlyName `
        -KeyExportPolicy Exportable `
        -KeyAlgorithm RSA `
        -KeyLength 2048 `
        -HashAlgorithm SHA256 `
        -Type SSLServerAuthentication `
        -NotAfter (Get-Date).AddYears(2)
}

$trustedCertificate = Get-ChildItem Cert:\CurrentUser\Root |
    Where-Object { $_.Thumbprint -eq $certificate.Thumbprint } |
    Select-Object -First 1

if ($null -eq $trustedCertificate) {
    $rootStore = New-Object `
        -TypeName System.Security.Cryptography.X509Certificates.X509Store `
        -ArgumentList 'Root', 'CurrentUser'
    try {
        $rootStore.Open(
            [System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite
        )
        $rootStore.Add($certificate)
    } finally {
        $rootStore.Close()
    }
}

$storedThumbprint = if (Test-Path $thumbprintPath) {
    (Get-Content $thumbprintPath -Raw).Trim()
} else {
    ''
}

if (-not (Test-Path $pfxPath) -or $storedThumbprint -ne $certificate.Thumbprint) {
    $randomBytes = New-Object byte[] 48
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($randomBytes)
        $rawPassword = [Convert]::ToBase64String($randomBytes)
    } finally {
        $randomGenerator.Dispose()
        [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    }
    $password = ConvertTo-SecureString $rawPassword -AsPlainText -Force
    Set-Content -Path $passwordPath -Value (ConvertFrom-SecureString $password) -NoNewline
    $rawPassword = $null
    Export-PfxCertificate -Cert $certificate -FilePath $pfxPath -Password $password -Force |
        Out-Null
    Set-Content -Path $thumbprintPath -Value $certificate.Thumbprint -NoNewline
} else {
    if (-not (Test-Path $passwordPath)) {
        throw "No se encontró la contraseña cifrada del certificado: $passwordPath"
    }
    $password = ConvertTo-SecureString (Get-Content $passwordPath -Raw).Trim()
}

$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
try {
    $env:LOCAL_HTTPS_KEYSTORE_PASSWORD =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

if ([string]::IsNullOrWhiteSpace($env:PORT)) {
    $port = '5679'
} else {
    $port = $env:PORT
}

$env:LOCAL_HTTPS_ENABLED = 'true'
$env:LOCAL_HTTPS_KEYSTORE = $pfxPath
$env:SESSION_COOKIE_SECURE = 'true'
$env:APP_PUBLIC_BASE_URL = "https://localhost:$port"
$env:SPRING_PROFILES_ACTIVE = 'local'

Write-Host "HTTPS local preparado. Abre https://localhost:$port y ejecuta la aplicación en esta terminal."
