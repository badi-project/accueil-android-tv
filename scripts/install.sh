#!/bin/bash
# Installs the launcher on an Android TV over the network (ADB) and makes it the home screen.
# Usage: bash scripts/install.sh <TV_IP_ADDRESS> [path/to/AccueilTV.apk]
# On the TV first: Settings > Device Preferences > About > press "Build" 7 times,
# then Developer options > enable "USB debugging". TV and computer must share the same network.

TV="$1"
APK="${2:-$(dirname "$0")/../build/AccueilTV.apk}"
if [ -z "$TV" ]; then
  echo "Usage: bash scripts/install.sh <TV_IP_ADDRESS> [AccueilTV.apk]"; exit 1
fi
[ -f "$APK" ] || { echo "APK not found: $APK (run ./build.sh first)"; exit 1; }

ADB="$(command -v adb)"
if [ -z "$ADB" ]; then
  ADB="$HOME/platform-tools/adb"
  if [ ! -x "$ADB" ]; then
    case "$(uname -s)" in
      Darwin) OS=darwin ;; Linux) OS=linux ;; *) echo "Install adb manually."; exit 1 ;;
    esac
    echo "→ Downloading Google platform-tools…"
    curl -fsSL -o /tmp/platform-tools.zip \
      "https://dl.google.com/android/repository/platform-tools-latest-$OS.zip" || exit 1
    unzip -oq /tmp/platform-tools.zip -d "$HOME"
  fi
fi

DEV="$TV:5555"
echo "→ Connecting to $DEV — accept \"Allow USB debugging\" on the TV (tick \"Always allow\")."
"$ADB" connect "$DEV" >/dev/null 2>&1
STATE=""
for i in $(seq 1 45); do
  STATE="$("$ADB" -s "$DEV" get-state 2>/dev/null)"
  [ "$STATE" = "device" ] && break
  { [ -z "$STATE" ] || [ "$STATE" = "offline" ]; } && "$ADB" connect "$DEV" >/dev/null 2>&1
  sleep 2
done
[ "$STATE" = "device" ] || { echo "✗ The TV did not accept the connection (${STATE:-no answer})."; exit 1; }
A() { "$ADB" -s "$DEV" "$@"; }

echo "→ Installing…"
A install -r "$APK" || exit 1
A shell cmd package set-home-activity app.accueil.tv/.HomeActivity
A shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "✓ Done. Press Home on the remote."
echo "  If the stock home screen comes back, see scripts/debloat-vestel.sh (disables it)."
