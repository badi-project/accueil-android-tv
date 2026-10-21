#!/bin/bash
# Optional clean-up for Vestel-made Android TV 11 sets (Edenwood and other brands built by Vestel).
# Disables the stock Google home screen and its sponsored rows, Vestel telemetry, the store demo
# mode, FAST ad channels, the promo-app auto-installer, and speeds up animations.
# Optionally sets a system-wide ad/tracker-blocking DNS (AdGuard).
# Everything is reversible with scripts/restore-vestel.sh. Install the launcher first!
#
# Usage: bash scripts/debloat-vestel.sh <TV_IP_ADDRESS> [--dns]

TV="$1"; DNS="$2"
[ -z "$TV" ] && { echo "Usage: bash scripts/debloat-vestel.sh <TV_IP_ADDRESS> [--dns]"; exit 1; }
ADB="$(command -v adb || echo "$HOME/platform-tools/adb")"
DEV="$TV:5555"
"$ADB" connect "$DEV" >/dev/null 2>&1; sleep 2
A() { "$ADB" -s "$DEV" "$@"; }
[ "$(A get-state 2>/dev/null)" = "device" ] || { echo "✗ TV not reachable (run scripts/install.sh first)"; exit 1; }

A shell pm list packages app.accueil.tv | grep -q app.accueil.tv || {
  echo "✗ Install the launcher first (scripts/install.sh), otherwise the TV would have no home screen."; exit 1; }

A shell cmd package set-home-activity app.accueil.tv/.HomeActivity

# Packages that may not exist on your model are simply skipped.
for p in com.google.android.tvlauncher \
         com.google.android.tvrecommendations \
         com.google.android.leanbacklauncher.partnercustomizer \
         com.cltv.fast \
         fusion.android.tv.demo \
         com.vestel.vestelreporter \
         com.vestel.tv.eib \
         android.autoinstalls.config.vestel.cosmos \
         com.google.android.feedback \
         com.google.android.play.games; do
  if A shell pm list packages "$p" | grep -q "package:$p\$"; then
    echo "→ disable $p"; A shell pm disable-user --user 0 "$p"
  fi
done

echo "→ faster animations"
A shell settings put global window_animation_scale 0.5
A shell settings put global transition_animation_scale 0.5
A shell settings put global animator_duration_scale 0.5

if [ "$DNS" = "--dns" ]; then
  echo "→ private DNS: dns.adguard-dns.com (blocks ads and trackers for every app)"
  A shell settings put global private_dns_mode hostname
  A shell settings put global private_dns_specifier dns.adguard-dns.com
fi

A shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "✓ Done. Undo everything with: bash scripts/restore-vestel.sh $TV"
