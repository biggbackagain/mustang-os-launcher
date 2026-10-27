$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$toolsDir = Join-Path $root '.tools'
$downloads = Join-Path $root '.downloads'
New-Item -ItemType Directory -Force $toolsDir,$downloads | Out-Null
function Fetch-Checked($url, $name, $checksum, $algorithm = 'SHA256') {
    $target = Join-Path $downloads $name
    if (-not (Test-Path -LiteralPath $target)) {
        Write-Host "Descargando $name"
        Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm $algorithm).Hash -ne $checksum) { throw "Checksum incorrecto: $name" }
    return $target
}
if (-not (Test-Path -LiteralPath (Join-Path $toolsDir 'jdk'))) {
    $release = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $pkg = $release[0].binary.package
    $zip = Fetch-Checked $pkg.link 'jdk17.zip' $pkg.checksum
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $toolsDir 'java') -Force
    $jdkFolder = Get-ChildItem -LiteralPath (Join-Path $toolsDir 'java') -Directory | Select-Object -First 1
    Move-Item -LiteralPath $jdkFolder.FullName -Destination (Join-Path $toolsDir 'jdk')
}
$env:JAVA_HOME = Join-Path $toolsDir 'jdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $root '.gradle'
if (-not (Test-Path -LiteralPath (Join-Path $toolsDir 'gradle-8.13'))) {
    $hash = (Invoke-RestMethod 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256').Trim()
    $zip = Fetch-Checked 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' 'gradle.zip' $hash
    Expand-Archive -LiteralPath $zip -DestinationPath $toolsDir -Force
}
$sdk = Join-Path $toolsDir 'android-sdk'
if (-not (Test-Path -LiteralPath (Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'))) {
    [xml]$repo = Invoke-RestMethod 'https://dl.google.com/android/repository/repository2-1.xml'
    $entry = $repo.SelectNodes("//*[local-name()='remotePackage']") | Where-Object { $_.path -eq 'cmdline-tools;latest' } | Select-Object -First 1
    $archive = $entry.archives.archive | Where-Object { $_.'host-os' -eq 'windows' }
    $zip = Fetch-Checked ("https://dl.google.com/android/repository/" + $archive.complete.url) 'android-cli.zip' ([string]$archive.complete.checksum) 'SHA1'
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $toolsDir 'android-cli') -Force
    New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
    Move-Item -LiteralPath (Join-Path $toolsDir 'android-cli\cmdline-tools') -Destination (Join-Path $sdk 'cmdline-tools\latest')
}
$env:ANDROID_HOME = $sdk
Write-Output 'Aceptando licencias de las herramientas Android para esta compilacion local.'
1..30 | ForEach-Object { 'y' } | & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$sdk" --licenses | Out-Null
& "$sdk\cmdline-tools\latest\bin\android.exe" --no-metrics "--sdk=$sdk" sdk install 'platform-tools' 'platforms/android-35' 'build-tools/35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar Android SDK' }
"sdk.dir=$($sdk.Replace('\','/'))" | Set-Content -LiteralPath (Join-Path $root 'local.properties') -Encoding ASCII
& "$toolsDir\gradle-8.13\bin\gradle.bat" -p $root wrapper --gradle-version 8.13 --distribution-type bin --gradle-distribution-sha256-sum '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper fallo' }
Write-Output 'Toolchain lista. Ejecuta scripts\verify.ps1'
