@echo off
cd /d "%~dp0..\backend"
"C:\Program Files\Java\jdk-17\bin\java.exe" -jar "%CD%\target\speedlink-backend-0.0.1-SNAPSHOT.jar" > "%CD%\target\backend-detached.log" 2>&1
