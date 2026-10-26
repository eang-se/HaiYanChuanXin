@echo off
set "TUNNEL_PWSH=pwsh"
where pwsh >nul 2>nul
if errorlevel 1 set "TUNNEL_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
"%TUNNEL_PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Start-TemporaryTunnel.ps1"
pause
