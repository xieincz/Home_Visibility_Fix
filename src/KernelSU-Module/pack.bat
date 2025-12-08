@echo off
REM Windows batch script to pack KernelSU module

echo Packing KernelSU module...

cd ..
if exist home_launcher_redirect.zip del home_launcher_redirect.zip

powershell -command "Compress-Archive -Path 'ks\*' -DestinationPath 'home_launcher_redirect.zip' -Force"

if exist home_launcher_redirect.zip (
    echo.
    echo Success! Module packed as: home_launcher_redirect.zip
    echo You can now install it via KernelSU Manager.
) else (
    echo.
    echo Error: Failed to create ZIP file.
)

pause
