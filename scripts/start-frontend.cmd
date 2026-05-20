@echo off
cd /d "%~dp0..\frontend"
npm.cmd run dev > frontend.log 2> frontend.err.log
