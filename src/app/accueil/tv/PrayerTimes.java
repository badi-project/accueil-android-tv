package app.accueil.tv;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Calcul astronomique des horaires de prière, hors ligne.
 * Sert de secours quand les horaires du Guide Musulman ne sont pas disponibles
 * (pas d'Internet). Angles et marges calés sur les calendriers du Guide Musulman
 * (méthode 12° par défaut du site) : écart constaté d'une minute au plus.
 */
final class PrayerTimes {

    static final String CITY = Config.CITY;
    static final double LAT = Config.LATITUDE, LNG = Config.LONGITUDE;
    static final TimeZone TZ = TimeZone.getTimeZone(Config.TIME_ZONE);

    private static final double FAJR_ANGLE = 12.7, ISHA_ANGLE = 12.7, RISE_SET = 0.833;
    // Marges en minutes : Fajr, Chourouk, Dhohr, Asr, Maghrib, Icha
    private static final double[] OFF = {-1.25, -0.25, 5, 0, 4.75, 1.0};

    static final String[] NAMES = {"Fajr", "Chourouk", "Dhohr", "Asr", "Maghrib", "Icha"};
    /** Index de Chourouk : affiché, mais ce n'est pas une prière. */
    static final int SUNRISE = 1;

    private PrayerTimes() { }

    /** Minutes depuis minuit (heure de Paris) pour le jour donné : Fajr, Chourouk, Dhohr, Asr, Maghrib, Icha. */
    static int[] forDay(int year, int month, int day) {
        Calendar noon = Calendar.getInstance(TZ);
        noon.clear();
        noon.set(year, month - 1, day, 12, 0, 0);
        double tz = TZ.getOffset(noon.getTimeInMillis()) / 3600000.0;

        double jd = julian(year, month, day) - LNG / (15 * 24);
        double fajr = 5, rise = 6, dhuhr = 12, asr = 13, set = 18, isha = 18;
        for (int i = 0; i < 2; i++) {
            double f = sunAngleTime(jd, FAJR_ANGLE, fajr / 24, true);
            double r = sunAngleTime(jd, RISE_SET, rise / 24, true);
            double d = midDay(jd, dhuhr / 24);
            double a = asrTime(jd, 1, asr / 24);
            double s = sunAngleTime(jd, RISE_SET, set / 24, false);
            double n = sunAngleTime(jd, ISHA_ANGLE, isha / 24, false);
            fajr = f; rise = r; dhuhr = d; asr = a; set = s; isha = n;
        }
        double shift = tz - LNG / 15;
        return new int[]{
                toMin(fajr + shift, OFF[0]),
                toMin(rise + shift, OFF[1]),
                toMin(dhuhr + shift, OFF[2]),
                toMin(asr + shift, OFF[3]),
                toMin(set + shift, OFF[4]),
                toMin(isha + shift, OFF[5]),
        };
    }

    private static int toMin(double hours, double offMin) {
        return (int) Math.round(hours * 60 + offMin);
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        int a = y / 100;
        int b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {déclinaison, équation du temps} */
    private static double[] sunPosition(double jd) {
        double D = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * D);
        double q = fixAngle(280.459 + 0.98564736 * D);
        double L = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double ra = Math.toDegrees(Math.atan2(cos(e) * sin(L), cos(L))) / 15;
        double eqt = q / 15 - fixHour(ra);
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(L)));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        return fixHour(12 - sunPosition(jd + t)[1]);
    }

    private static double sunAngleTime(double jd, double angle, double t, boolean beforeNoon) {
        double decl = sunPosition(jd + t)[0];
        double noon = midDay(jd, t);
        double x = (-sin(angle) - sin(decl) * sin(LAT)) / (cos(decl) * cos(LAT));
        x = Math.max(-1, Math.min(1, x));
        double h = Math.toDegrees(Math.acos(x)) / 15;
        return noon + (beforeNoon ? -h : h);
    }

    private static double asrTime(double jd, double factor, double t) {
        double decl = sunPosition(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (factor + Math.tan(Math.toRadians(Math.abs(LAT - decl))))));
        return sunAngleTime(jd, angle, t, false);
    }

    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double fixAngle(double a) { return a - 360 * Math.floor(a / 360); }
    private static double fixHour(double a) { return a - 24 * Math.floor(a / 24); }
}
