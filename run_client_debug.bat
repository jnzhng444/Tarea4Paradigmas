@echo off
REM Script para ejecutar el cliente con consola visible para ver logs

echo ========================================
echo Ejecutando Cliente con Consola Debug
echo ========================================
echo.

cd ClienteC\output
start cmd /k dkj_client.exe

echo.
echo Clientes ejecutados en ventanas separadas con consola
pause
