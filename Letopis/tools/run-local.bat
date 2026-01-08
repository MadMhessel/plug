@echo off
setlocal enabledelayedexpansion

if "%SERVER_DIR%"=="" set "SERVER_DIR=..\server"
if "%SERVER_JAR%"=="" set "SERVER_JAR=%SERVER_DIR%\server.jar"
if "%XMS%"=="" set "XMS=1G"
if "%XMX%"=="" set "XMX=6G"

echo [Letopis] Проверка Java...
java -version
if errorlevel 1 (
  echo [Letopis] Ошибка: Java не найдена в PATH.
  exit /b 1
)

if not exist "gradlew.bat" (
  echo [Letopis] Ошибка: gradlew.bat не найден в корне репозитория.
  exit /b 1
)

echo [Letopis] Сборка плагина...
call gradlew.bat clean build
if errorlevel 1 (
  echo [Letopis] Ошибка: сборка завершилась неуспешно.
  exit /b 1
)

set "PLUGIN_JAR="
for /f "delims=" %%F in ('dir /b /a:-d /o-d "build\libs\Letopis-*.jar" 2^>nul') do (
  set "PLUGIN_JAR=build\libs\%%F"
  goto :jar_found
)

echo [Letopis] Ошибка: не найден JAR в build\libs\Letopis-*.jar
exit /b 1

:jar_found
if not exist "%SERVER_DIR%\plugins" (
  mkdir "%SERVER_DIR%\plugins"
)

echo [Letopis] Копирование !PLUGIN_JAR! в %SERVER_DIR%\plugins\
copy /Y "!PLUGIN_JAR!" "%SERVER_DIR%\plugins\" >nul
if errorlevel 1 (
  echo [Letopis] Ошибка: не удалось скопировать JAR.
  exit /b 1
)

if not exist "%SERVER_JAR%" (
  echo [Letopis] Ошибка: не найден сервер Paper: %SERVER_JAR%
  exit /b 1
)

echo [Letopis] Запуск сервера...
echo java -Xms%XMS% -Xmx%XMX% -jar "%SERVER_JAR%" nogui
pushd "%SERVER_DIR%"
java -Xms%XMS% -Xmx%XMX% -jar "%SERVER_JAR%" nogui
popd
