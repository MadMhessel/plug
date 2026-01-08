@echo off
setlocal EnableExtensions

REM Запуск локального Paper-сервера из папки server/ рядом с репозиторием.
REM Скрипт сначала пытается собрать плагин через Gradle Wrapper (gradlew),
REM если его нет — через установленный gradle.

set "ROOT=%~dp0..\"
set "SERVER_DIR=%ROOT%server"
set "SERVER_JAR=%SERVER_DIR%\server.jar"

pushd "%ROOT%" || exit /b 1

echo [Letopis] Сборка плагина...
if exist "%ROOT%gradlew.bat" (
  call "%ROOT%gradlew.bat" clean build || exit /b 1
) else (
  where gradle >nul 2>nul
  if errorlevel 1 (
    echo [Letopis] НЕ НАЙДЕН gradlew.bat и НЕ НАЙДЕН gradle в PATH.
    echo [Letopis] Решение: выполните `gradle wrapper` в корне проекта и повторите,
    echo          либо установите Gradle и добавьте его в PATH.
    popd
    exit /b 1
  )
  gradle clean build || exit /b 1
)

if not exist "%SERVER_JAR%" (
  echo [Letopis] Не найден %SERVER_JAR%
  echo [Letopis] Положите Paper jar в server\server.jar
  popd
  exit /b 1
)

if not exist "%SERVER_DIR%\eula.txt" (
  echo [Letopis] Не найден server\eula.txt
  echo [Letopis] Один раз запустите сервер вручную и примите EULA.
  popd
  exit /b 1
)

echo [Letopis] Установка плагина в server\plugins...
if not exist "%SERVER_DIR%\plugins" mkdir "%SERVER_DIR%\plugins"

set "COPIED=0"
for %%F in ("%ROOT%build\libs\Letopis-*.jar") do (
  copy /Y "%%~fF" "%SERVER_DIR%\plugins\" >nul
  set "COPIED=1"
)

if "%COPIED%"=="0" (
  echo [Letopis] Не найден собранный jar в build\libs\Letopis-*.jar
  popd
  exit /b 1
)

echo [Letopis] Старт сервера...
pushd "%SERVER_DIR%" || exit /b 1
java -Xms512M -Xmx2G -jar server.jar nogui
popd
popd
