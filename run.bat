@echo off
setlocal
cd /d "%~dp0"
if not exist backend\out mkdir backend\out
javac -cp "backend\lib\*" -d backend\out backend\src\com\missingperson\App.java
if errorlevel 1 exit /b 1
java -cp "backend\out;backend\lib\*" com.missingperson.App
