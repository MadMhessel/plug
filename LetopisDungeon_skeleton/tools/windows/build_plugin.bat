@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%i in ("%SCRIPT_DIR%..\..\") do set "ROOT_DIR=%%~fi"

cd /d "%ROOT_DIR%"

for /f "usebackq delims=" %%g in (`powershell -ExecutionPolicy Bypass -File "%ROOT_DIR%\tools\windows\bootstrap_gradle.ps1"`) do set "GRADLE_CMD=%%g"

if "%GRADLE_CMD%"=="" (
  echo Не удалось найти gradle.bat.
  exit /b 1
)

"%GRADLE_CMD%" clean build
if errorlevel 1 (
  echo Ошибка сборки.
  exit /b 1
)

for %%f in ("%ROOT_DIR%\build\libs\LetopisDungeon-*.jar") do (
  echo JAR: %%~ff
)

if "%MC_PLUGINS_DIR%"=="" (
  echo Переменная MC_PLUGINS_DIR не задана.
  echo Укажите путь к папке plugins, например:
  echo set MC_PLUGINS_DIR=C:\Servers\Paper\plugins
  exit /b 0
)

if not exist "%MC_PLUGINS_DIR%" (
  echo Папка MC_PLUGINS_DIR не найдена: %MC_PLUGINS_DIR%
  exit /b 1
)

for %%f in ("%ROOT_DIR%\build\libs\LetopisDungeon-*.jar") do (
  echo Копирование %%~nxf в %MC_PLUGINS_DIR%
  copy /y "%%f" "%MC_PLUGINS_DIR%" >nul
)

echo Готово.
endlocal
