@echo off
setlocal enabledelayedexpansion
cd /d %~dp0\..

set SERVER_DIR=%~dp0..\server
set PAPER_JAR=%SERVER_DIR%\paper.jar
set WORLD_DIR=%SERVER_DIR%\world
set DP_DIR=%WORLD_DIR%\datapacks
set PLUGINS_DIR=%SERVER_DIR%\plugins

if not exist "%PAPER_JAR%" (
  echo [ERR] Не найден %PAPER_JAR%
  echo Положи Paper jar как server\paper.jar
  exit /b 1
)

if not exist gradlew.bat (
  echo [ERR] gradlew.bat не найден. Запусти: gradle wrapper --gradle-version 8.10.2
  exit /b 1
)

call gradlew.bat clean build
if errorlevel 1 exit /b 1

if not exist "%PLUGINS_DIR%" mkdir "%PLUGINS_DIR%"
copy /Y "build\libs\LetopisDungeon-0.1.0.jar" "%PLUGINS_DIR%\LetopisDungeon.jar" >nul

if not exist "%DP_DIR%" mkdir "%DP_DIR%"
if exist "datapack\LetopisDungeonRooms" (
  xcopy /E /I /Y "datapack\LetopisDungeonRooms" "%DP_DIR%\LetopisDungeonRooms" >nul
)

echo [OK] Запуск сервера...
cd /d "%SERVER_DIR%"
java -Xms512M -Xmx4G -jar "%PAPER_JAR%" nogui
