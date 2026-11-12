@echo off
setlocal
chcp 65001 >nul
title Java TCP Chat - Server

cd /d "%~dp0"

:: Tim java
where java >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "JAVA_EXE=java"
) else if exist "%USERPROFILE%\.jdks\ms-25.0.4.1\bin\java.exe" (
    set "JAVA_EXE=%USERPROFILE%\.jdks\ms-25.0.4.1\bin\java.exe"
) else if exist "%USERPROFILE%\.jdks\openjdk-27\bin\java.exe" (
    set "JAVA_EXE=%USERPROFILE%\.jdks\openjdk-27\bin\java.exe"
) else (
    echo [LOI] Khong tim thay java! Vui long kiem tra JDK.
    pause
    exit /b 1
)

:: Neu chua build out/ thi tu dong build truoc
if not exist "out\server\ChatServer.class" (
    echo Chua bien dich project, dang goi build.bat...
    call "%~dp0build.bat"
    if %ERRORLEVEL% NEQ 0 (
        echo Khong the khoi dong server do loi build.
        pause
        exit /b 1
    )
)

echo ========================================================
echo               KHOI DONG CHAT SERVER (Port 9999)
echo ========================================================
echo Luu y: Dam bao MySQL trong XAMPP dang bat (port 3306).
echo Nhan Ctrl + C de dung Server.
echo.

"%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "out;lib\*;lib\mysql-connector-j-26.7.0.jar;.idea\libraries\mysql-connector-j-26.7.0.jar" server.ChatServer

pause
