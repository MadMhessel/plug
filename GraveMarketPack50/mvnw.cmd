@REM ----------------------------------------------------------------------------
@REM Maven Wrapper for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set MAVEN_WRAPPER_DIR=%~dp0.mvn\wrapper
set MAVEN_WRAPPER_JAR=%MAVEN_WRAPPER_DIR%\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_WRAPPER_DIR%\maven-wrapper.properties

if not exist "%MAVEN_WRAPPER_JAR%" (
  if not exist "%MAVEN_WRAPPER_DIR%" mkdir "%MAVEN_WRAPPER_DIR%"
  echo Downloading Maven Wrapper...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "$uri='https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar';" ^
    "$out='%MAVEN_WRAPPER_JAR%';" ^
    "Invoke-WebRequest -Uri $uri -OutFile $out"
)

set JAVA_EXE=java
"%JAVA_EXE%" -jar "%MAVEN_WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory=%CD% %*
endlocal
