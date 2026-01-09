@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%i in ("%SCRIPT_DIR%..\..\") do set "ROOT_DIR=%%~fi"
set "SERVER_DIR=%ROOT_DIR%\test_server"

if not exist "%SERVER_DIR%" (
  mkdir "%SERVER_DIR%"
)

if not exist "%SERVER_DIR%\server.jar" (
  echo Файл server.jar не найден.
  echo Положите Paper jar в %SERVER_DIR%\server.jar
  exit /b 1
)

dir /b "%ROOT_DIR%\build\libs\LetopisDungeon-*.jar" >nul 2>&1
if errorlevel 1 (
  call "%ROOT_DIR%\tools\windows\build_plugin.bat"
  if errorlevel 1 exit /b 1
)

echo eula=true>"%SERVER_DIR%\eula.txt"

if not exist "%SERVER_DIR%\plugins" (
  mkdir "%SERVER_DIR%\plugins"
)

for %%f in ("%ROOT_DIR%\build\libs\LetopisDungeon-*.jar") do (
  copy /y "%%f" "%SERVER_DIR%\plugins" >nul
)

echo Запуск сервера...
cd /d "%SERVER_DIR%"
java -Xms1G -Xmx2G -jar server.jar nogui

endlocal
