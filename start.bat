@echo off
title Compilador StaffHelp v1.0 - Java 17

echo ============================================
echo Compilador do Plugin StaffHelp
echo ============================================
echo.

echo Procurando Java 17 instalado...
echo.

set JDK_PATH=
for /d %%i in ("C:\Program Files\Java\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Java\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set JDK_PATH=%%i

if "%JDK_PATH%"=="" (
    echo ============================================
    echo ERRO: JDK 17 nao encontrado!
    echo ============================================
    pause
    exit /b 1
)

echo Java 17 encontrado em: %JDK_PATH%
echo.

set JAVAC="%JDK_PATH%\bin\javac.exe"
set JAR="%JDK_PATH%\bin\jar.exe"

echo ============================================
echo Preparando ambiente de compilacao...
echo ============================================
echo.

if exist out rmdir /s /q out >nul 2>&1
mkdir out
mkdir out\com
mkdir out\com\staffhelp

echo.
echo ============================================
echo Verificando dependencias...
echo ============================================
echo.

if not exist spigot-api-1.20.1-R0.1-SNAPSHOT.jar (
    echo [ERRO] spigot-api-1.20.1-R0.1-SNAPSHOT.jar nao encontrado!
    pause
    exit /b 1
) else (
    echo [OK] Spigot API encontrado
)

echo.
echo ============================================
echo Compilando StaffHelp...
echo ============================================
echo.

%JAVAC% -cp spigot-api-1.20.1-R0.1-SNAPSHOT.jar -d out -sourcepath src src/com/staffhelp/StaffHelp.java -encoding UTF-8

if %errorlevel% neq 0 (
    echo ============================================
    echo ERRO AO COMPILAR O PLUGIN!
    echo ============================================
    pause
    exit /b 1
)

echo.
echo Compilacao concluida com sucesso!
echo.

echo ============================================
echo Copiando arquivos de recursos...
echo ============================================
echo.

copy resources\plugin.yml out\ >nul 2>&1
copy resources\config.yml out\ >nul 2>&1

echo.
echo ============================================
echo Criando arquivo JAR...
echo ============================================
echo.

cd out
%JAR% cf StaffHelp.jar com plugin.yml config.yml
cd ..

echo.
echo ============================================
echo PLUGIN COMPILADO COM SUCESSO!
echo ============================================
echo.
echo Arquivo gerado: out\StaffHelp.jar
echo.
pause
