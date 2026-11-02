@echo off
setlocal
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar "%~dp0dist\ra1-act1.jar" %*
if errorlevel 1 pause
