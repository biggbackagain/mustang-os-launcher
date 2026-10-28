$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (Test-Path -LiteralPath "$root\.tools\jdk") { $env:JAVA_HOME = "$root\.tools\jdk" }
$env:GRADLE_USER_HOME = "$root\.gradle"
& "$root\gradlew.bat" -p $root :core:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Verificacion fallo' }
New-Item -ItemType Directory -Force "$root\output" | Out-Null
Copy-Item -LiteralPath "$root\app\build\outputs\apk\debug\app-debug.apk" -Destination "$root\output\MustangOS-debug.apk" -Force
Copy-Item -LiteralPath "$root\app\build\outputs\apk\release\app-release.apk" -Destination "$root\output\MustangOS.apk" -Force
Get-FileHash -LiteralPath "$root\output\MustangOS-debug.apk" -Algorithm SHA256
Get-FileHash -LiteralPath "$root\output\MustangOS.apk" -Algorithm SHA256
