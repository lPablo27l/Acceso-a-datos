@echo off
setlocal
cd /d "%~dp0"
set "AADTEX_DEMO=./demostraciones/demo-%RANDOM%-%RANDOM%"
echo Nueva demostracion en %AADTEX_DEMO%
call iniciar.cmd "--aadtex.data-dir=%AADTEX_DEMO%"
