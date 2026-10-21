#!/bin/bash
# Undoes scripts/debloat-vestel.sh and removes the launcher.
# Usage: bash scripts/restore-vestel.sh <TV_IP_ADDRESS>

TV="$1"
[ -z "$TV" ] && { echo "Usage: bash scripts/restore-vestel.sh <TV_IP_ADDRESS>"; exit 1; }
ADB="$(command -v adb || echo "$HOME/platform-tools/adb")"
DEV="$TV:5555"
"$ADB" connect "$DEV" >/dev/null 2>&1; sleep 2
A() { "$ADB" -s "$DEV" "$@"; }

for p in com.google.android.tvlauncher com.google.android.tvrecommendations \
         com.google.android.leanbacklauncher.partnercustomizer com.cltv.fast \
         fusion.android.tv.demo com.vestel.vestelreporter com.vestel.tv.eib \
         android.autoinstalls.config.vestel.cosmos com.google.android.feedback \
         com.google.android.play.games; do
  A shell pm enable "$p" >/dev/null 2>&1
done
A shell settings put global private_dns_mode opportunistic
A shell settings delete global private_dns_specifier >/dev/null 2>&1
A shell settings put global window_animation_scale 1
A shell settings put global transition_animation_scale 1
A shell settings put global animator_duration_scale 1
A shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity >/dev/null 2>&1
A uninstall app.accueil.tv
echo "✓ TV restored."
