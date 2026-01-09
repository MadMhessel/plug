$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Resolve-Path (Join-Path $scriptDir '..\..')
$toolsDir = Join-Path $rootDir '.tools'
$gradleRoot = Join-Path $toolsDir 'gradle'
$gradleVersion = '8.10.2'
$gradleFolder = Join-Path $gradleRoot "gradle-$gradleVersion"
$gradleZip = Join-Path $gradleRoot "gradle-$gradleVersion-bin.zip"
$gradleBat = Join-Path $gradleFolder 'bin\gradle.bat'

if (!(Test-Path $gradleBat)) {
    if (!(Test-Path $gradleRoot)) {
        New-Item -ItemType Directory -Path $gradleRoot | Out-Null
    }

    if (!(Test-Path $gradleZip)) {
        $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
        Write-Host "Downloading Gradle $gradleVersion..."
        Invoke-WebRequest -Uri $url -OutFile $gradleZip
    }

    Write-Host "Extracting Gradle to $gradleRoot"
    Expand-Archive -Path $gradleZip -DestinationPath $gradleRoot -Force
}

Write-Output $gradleBat
