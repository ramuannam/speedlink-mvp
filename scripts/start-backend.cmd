@echo off
cd /d "%~dp0..\backend"
mvn.cmd spring-boot:run > backend.log 2> backend.err.log
