@echo off
setlocal
chcp 65001 >nul

cd /d "%~dp0"

echo ========================================================
echo               DANG BIEN DICH (BUILD) DU AN
echo ========================================================

:: Tim javac
where javac >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "JAVAC_EXE=javac"
) else if exist "%USERPROFILE%\.jdks\ms-25.0.4.1\bin\javac.exe" (
    set "JAVAC_EXE=%USERPROFILE%\.jdks\ms-25.0.4.1\bin\javac.exe"
) else if exist "%USERPROFILE%\.jdks\openjdk-27\bin\javac.exe" (
    set "JAVAC_EXE=%USERPROFILE%\.jdks\openjdk-27\bin\javac.exe"
) else (
    echo [LOI] Khong tim thay javac! Vui long kiem tra JDK.
    pause
    exit /b 1
)

if not exist "out" mkdir out

echo Dang bien dich cac file Java tu chat-app\src...
pushd chat-app\src
"%JAVAC_EXE%" -encoding UTF-8 -d "..\..\out" common\Message.java server\*.java client\*.java
set BUILD_STATUS=%ERRORLEVEL%
popd

if %BUILD_STATUS% EQU 0 (
    echo [THANH CONG] Da bien dich xong tat ca vao thu muc out/!
) else (
    echo [THAT BAI] Co loi xay ra khi bien dich.
    pause
    exit /b 1
)

endlocal
