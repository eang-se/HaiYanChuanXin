@echo off
pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Start-Local.ps1"
if errorlevel 1 pause

