@echo off
taskkill /IM calculadora.exe /F >nul 2>&1
powershell -command "Get-CimInstance Win32_Process -Filter \"name='msedge.exe'\" | Where-Object { $_.CommandLine -like '*calc-edge*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
timeout /t 2 /nobreak >nul

for %%D in (dist dist2 dist3 dist4 dist5 dist6 dist7 dist8 build-input teste) do (
  if exist %%D rmdir /s /q %%D
)
if exist "%TEMP%\calc-edge" rmdir /s /q "%TEMP%\calc-edge"

call sbt clean backend/assembly || (echo ERRO no sbt & pause & exit /b 1)

mkdir build-input
copy /y backend\target\scala-2.13\calculadora.jar build-input\ >nul

jpackage --type app-image --name calculadora --input build-input --main-jar calculadora.jar --main-class calc.Main --dest dist || (echo ERRO no jpackage & pause & exit /b 1)

echo.
echo Pronto: dist\calculadora\calculadora.exe
pause