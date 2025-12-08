#!/system/bin/sh
# Home Launcher Redirect Service Script
# This script runs after boot_completed

MODDIR=${0%/*}

# Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# Additional wait to ensure system is fully ready
sleep 2

# Start the specified launcher
am start -n com.android.launcher/com.android.launcher.Launcher

# Log the action
# LOG_FILE="/data/local/tmp/launcher_redirect.log"
# echo "$(date): Launcher started" >> "$LOG_FILE"
