@echo off
rem 修复缺失的 Gradle wrapper jar（双击运行一次即可）
chcp 65001 >nul
setlocal
cd /d "%~dp0"

if not exist "gradle\wrapper" mkdir "gradle\wrapper"

echo 正在下载 gradle-wrapper.jar ...
curl -L --fail --show-error -o "gradle\wrapper\gradle-wrapper.jar" "https://raw.githubusercontent.com/gradle/gradle/v9.8.1/gradle/wrapper/gradle-wrapper.jar"

if errorlevel 1 (
    echo.
    echo 下载失败。请检查网络后重试，或手动下载以下文件放到 gradle\wrapper\ 目录：
    echo https://raw.githubusercontent.com/gradle/gradle/v9.8.1/gradle/wrapper/gradle-wrapper.jar
) else (
    echo.
    echo 完成！gradle-wrapper.jar 已就位，现在可以回到 Android Studio 打开项目了。
)

pause
