# Accueil — a minimal, ad-free home screen for Android TV

*Version française : [README.fr.md](README.fr.md)*

**Accueil** is a tiny Android TV launcher in the spirit of the Apple TV home screen: a dark background
that takes the colours of the selected app, large rounded app tiles that lift when focused, the time
and date in the corner — and nothing else. No recommendations, no sponsored rows, no ads, no tracking.

It also shows the day's **Muslim prayer times** for a city of your choice (optional).

- **Small and fast**: about 40 KB, plain Java, no libraries. Built for low-end TVs (tested on an
  Android TV 11, 32-bit MediaTek set with 1.3 GB of RAM).
- **Private**: the only network request it makes is reading the public prayer-times page of
  [guidemusulman.com](https://www.guidemusulman.com) for your city (set `GUIDE_MUSULMAN_URL` to `""`
  or `SHOW_PRAYER_TIMES` to `false` and it never goes online). No analytics, no account.
- **Your layout**: long-press OK (or press Menu) on a tile to move it, hide it, or open the app info.
  Hidden apps stay reachable from the button at the bottom of the screen.

## Prayer times

- Times come from the Guide Musulman monthly page of your city (31 days ahead). They are refreshed
  every 6 hours and kept on the TV, so they still show when the internet is down.
- With no internet and no saved times, the launcher computes them itself (sun position for your
  coordinates). The angles and margins were calibrated against Guide Musulman timetables (its default
  12° method): the difference is one minute at most. The bar then says “CALCULÉ HORS LIGNE”.
- The next prayer is highlighted with a countdown; after Isha the bar shows tomorrow's times.
  Daylight-saving time changes are handled automatically.

## Configure

Edit [`src/app/accueil/tv/Config.java`](src/app/accueil/tv/Config.java):

| Setting | Meaning |
|---|---|
| `SHOW_PRAYER_TIMES` | `false` hides the prayer-times bar |
| `CITY` | name shown above the times |
| `LATITUDE`, `LONGITUDE` | used by the offline calculation |
| `TIME_ZONE` | e.g. `Europe/Paris` |
| `GUIDE_MUSULMAN_URL` | the **monthly** page of your city on guidemusulman.com, or `""` |

## Build

No Android Studio or Gradle needed. On Ubuntu/Debian:

```bash
sudo apt-get install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner default-jdk
bash build.sh         # → build/AccueilTV.apk
```

The first build creates a local signing key (`keystore.jks`, ignored by git). Keep it: Android only
accepts updates signed with the same key. GitHub Actions also builds the APK on every push
(*Actions* tab → *AccueilTV-apk* artifact).

## Install on the TV

1. On the TV: *Settings › Device Preferences › About*, press *Build* 7 times, then
   *Developer options › USB debugging* → on. Note the TV's IP address (*About › Status*).
2. From a computer on the same network:

   ```bash
   bash scripts/install.sh <TV_IP_ADDRESS>
   ```

   Accept *Allow USB debugging* on the TV. The script downloads Google's `adb` if needed,
   installs the launcher and makes it the home screen.

### Optional: clean up a Vestel-made TV

[`scripts/debloat-vestel.sh`](scripts/debloat-vestel.sh) disables the stock Google home screen and
its sponsored content, Vestel telemetry, the store demo mode, FAST ad channels and the promo-app
auto-installer, and speeds up animations. Add `--dns` to block ads and trackers for every app with
AdGuard's public DNS. Packages your model doesn't have are skipped.

```bash
bash scripts/debloat-vestel.sh <TV_IP_ADDRESS> --dns
bash scripts/restore-vestel.sh <TV_IP_ADDRESS>   # undo everything and remove the launcher
```

Use at your own risk: it only *disables* system packages (nothing is deleted) and the restore script
re-enables them.

## How it works

- `HomeActivity.java` — the launcher (HOME activity, grid, focus animations, blurred ambient
  background, reorder/hide, clock, prayer bar).
- `GuideMusulman.java` — downloads and parses the city's monthly timetable, keeps a local copy.
- `PrayerTimes.java` — offline calculation (approximate solar coordinates from the U.S. Naval
  Observatory, prayer-time method as described by Hamid Zarrabi-Zadeh on praytimes.org).
- `build.sh` — `aapt` + `javac` + `dx` + `zipalign` + `apksigner`, against the API 23 `android.jar`.

## Credits and license

Prayer times are provided by [guidemusulman.com](https://www.guidemusulman.com); this project is not
affiliated with it. Check with your local mosque for congregational times.

MIT — see [LICENSE](LICENSE).
