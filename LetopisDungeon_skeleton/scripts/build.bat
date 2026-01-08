@echo off
setlocal
cd /d %~dp0\..
if not exist gradlew.bat (
  echo [WARN] gradlew.bat not found. Run: gradle wrapper --gradle-version 8.10.2
  exit /b 1
)
call gradlew.bat clean build
if errorlevel 1 exit /b 1
echo.
echo [OK] Built: build\libs\LetopisDungeon-0.1.0.jar
