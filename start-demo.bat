@echo off
setlocal
chcp 65001 >nul
title Khoi dong nhanh Java Chat Demo

cd /d "%~dp0"

echo ========================================================
echo        KHOI DONG HE THONG DEMO (SERVER + 2 CLIENTS)
echo ========================================================

:: 1. Build truoc neu chua co class
if not exist "out\server\ChatServer.class" (
    call "%~dp0build.bat"
    if %ERRORLEVEL% NEQ 0 (
        echo [LOI] Khong the build du an.
        pause
        exit /b 1
    )
)

:: 2. Bat Server trong cua so rieng
echo [1/3] Dang bat Server tren port 9999...
start "Java Chat Server" cmd /k "%~dp0run-server.bat"

:: Doi 2 giay de Server san sang
timeout /t 2 /nobreak >nul

:: 3. Bat 2 Client
echo [2/3] Dang bat Client 1...
start "Java Chat Client 1" cmd /c "%~dp0run-client.bat"

timeout /t 1 /nobreak >nul

echo [3/3] Dang bat Client 2...
start "Java Chat Client 2" cmd /c "%~dp0run-client.bat"

echo.
echo Da khoi dong thanh cong! Ban hay kiem tra cac cua so tren man hinh.
timeout /t 3 >nul
