$ErrorActionPreference = 'Stop'

$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$certificateDirectory = Join-Path $projectDirectory '.certs'
$privateConfiguration = Join-Path $projectDirectory 'backend\.env'
if (-not (Test-Path -LiteralPath $privateConfiguration)) {
    throw 'Falta backend/.env. Configura primero las variables privadas del backend.'
}

$opensslCommand = Get-Command openssl.exe -ErrorAction SilentlyContinue
$opensslPath = if ($opensslCommand) { $opensslCommand.Source } else {
    @('C:\Program Files\Git\usr\bin\openssl.exe', 'C:\Program Files\Git\mingw64\bin\openssl.exe') |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $opensslPath) { throw 'No se encuentra OpenSSL. Instala Git para Windows o añade OpenSSL al PATH.' }

New-Item -ItemType Directory -Path $certificateDirectory -Force | Out-Null
$userSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& "$env:WINDIR\System32\icacls.exe" $certificateDirectory '/inheritance:r' '/grant:r' `
    ("*${userSid}:(OI)(CI)F") '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron proteger las claves privadas del certificado.' }

$configuration = [IO.File]::ReadAllText($privateConfiguration)
$passwordMatch = [regex]::Match($configuration, '(?m)^SSL_KEY_STORE_PASSWORD=(.+)\r?$')
if ($passwordMatch.Success) {
    $certificatePassword = $passwordMatch.Groups[1].Value.Trim()
} else {
    $passwordBytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    $random.GetBytes($passwordBytes)
    $random.Dispose()
    $certificatePassword = [Convert]::ToBase64String($passwordBytes)
}

$certificatePath = Join-Path $certificateDirectory 'localhost.pem'
$keyPath = Join-Path $certificateDirectory 'localhost-key.pem'
$keyStorePath = Join-Path $certificateDirectory 'localhost.p12'
$certificate = $null
if (Test-Path -LiteralPath $certificatePath) {
    $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2($certificatePath)
}
$generate = -not $certificate -or $certificate.NotAfter -lt (Get-Date).AddDays(7) -or `
    -not (Test-Path -LiteralPath $keyPath) -or -not (Test-Path -LiteralPath $keyStorePath) -or -not $passwordMatch.Success

function Invoke-OpenSsl([string[]] $Arguments) {
    $ErrorActionPreference = 'Continue'
    $opensslOutput = & $opensslPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('OpenSSL no pudo preparar el certificado: ' + ($opensslOutput -join [Environment]::NewLine)) }
}

if ($generate) {
    $nextCertificate = Join-Path $certificateDirectory 'next-localhost.pem'
    $nextKey = Join-Path $certificateDirectory 'next-localhost-key.pem'
    $nextKeyStore = Join-Path $certificateDirectory 'next-localhost.p12'
    $previousPassword = [Environment]::GetEnvironmentVariable('ESIBUY_HTTPS_KEY_PASSWORD', 'Process')
    try {
        $env:ESIBUY_HTTPS_KEY_PASSWORD = $certificatePassword
        Invoke-OpenSsl -Arguments @('req', '-x509', '-newkey', 'rsa:3072', '-sha256', '-nodes', '-days', '365',
            '-keyout', $nextKey, '-out', $nextCertificate, '-subj', '/CN=localhost',
            '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1',
            '-addext', 'basicConstraints=critical,CA:FALSE', '-addext', 'extendedKeyUsage=serverAuth',
            '-addext', 'keyUsage=critical,digitalSignature,keyEncipherment')
        Invoke-OpenSsl -Arguments @('pkcs12', '-export', '-out', $nextKeyStore, '-inkey', $nextKey,
            '-in', $nextCertificate, '-name', 'esibuy', '-passout', 'env:ESIBUY_HTTPS_KEY_PASSWORD')
        # Son archivos concretos dentro de .certs; solo se sustituyen tras completar las dos operaciones.
        Move-Item -LiteralPath $nextCertificate -Destination $certificatePath -Force
        Move-Item -LiteralPath $nextKey -Destination $keyPath -Force
        Move-Item -LiteralPath $nextKeyStore -Destination $keyStorePath -Force
    } finally {
        [Environment]::SetEnvironmentVariable('ESIBUY_HTTPS_KEY_PASSWORD', $previousPassword, 'Process')
    }
    $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2($certificatePath)
}

$trustedCertificates = New-Object Security.Cryptography.X509Certificates.X509Store('Root', 'CurrentUser')
try {
    $trustedCertificates.Open([Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
    if (-not ($trustedCertificates.Certificates | Where-Object { $_.Thumbprint -eq $certificate.Thumbprint })) {
        $trustedCertificates.Add($certificate)
    }
} finally { $trustedCertificates.Close() }

$settings = [ordered]@{
    FRONTEND_ORIGIN = 'https://localhost:4200'
    SSL_KEY_STORE = ([Uri] $keyStorePath).AbsoluteUri
    SSL_KEY_STORE_PASSWORD = $certificatePassword
}
foreach ($setting in $settings.GetEnumerator()) {
    $pattern = '(?m)^' + [regex]::Escape($setting.Key) + '=.*\r?$'
    $entry = $setting.Key + '=' + $setting.Value
    if ([regex]::IsMatch($configuration, $pattern)) {
        $configuration = [regex]::Replace($configuration, $pattern, [Text.RegularExpressions.MatchEvaluator] { param($match) $entry })
    } else {
        $configuration = $configuration.TrimEnd() + [Environment]::NewLine + $entry + [Environment]::NewLine
    }
}
$configuration = [regex]::Replace($configuration, '(?m)^SESSION_COOKIE_SECURE=.*\r?\n?', '')
[IO.File]::WriteAllText($privateConfiguration, $configuration, (New-Object Text.UTF8Encoding($false)))
Write-Output 'HTTPS preparado: https://localhost:4200 y https://localhost:8443.'
Write-Output 'Certificado confiable para el usuario actual. Claves privadas guardadas fuera de Git.'
