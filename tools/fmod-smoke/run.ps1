param(
    [string] $NativeRoot,
    [string] $Jdk = $env:JAVA_HOME,
    [switch] $Wav
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$gradleArgs = @('--offline', 'fmodSmoke')
if ($NativeRoot) {
    $source = (Resolve-Path -LiteralPath $NativeRoot).Path
    $gradleArgs += "-PfmodSmokeSource=$source"
}
if ($Wav) {
    $gradleArgs += '-PfmodSmokeWav'
}

# 通过 Gradle 使用项目实际编译出的 Kotlin 加载器及其运行时依赖。
$previousJdk = $env:JAVA_HOME
Push-Location -LiteralPath $root
try {
    $env:JAVA_HOME = $Jdk
    & ./gradlew.bat @gradleArgs
    exit $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJdk
    Pop-Location
}
