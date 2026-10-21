package app.accueil.tv;

import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Horaires officiels du Guide Musulman pour la ville choisie dans {@link Config}.
 * La page donne les 31 prochains jours ; on la télécharge quelques fois par jour et on
 * garde une copie locale, utilisée même sans Internet. C'est le seul site que contacte l'accueil.
 */
final class GuideMusulman {

    static final String URL = Config.GUIDE_MUSULMAN_URL;

    private static final String KEY = "gm_cache";
    private static final Pattern ROW = Pattern.compile(
            "<td style=\"text-align:left;\">[^<]*?(\\d{1,2}) ([^\\s<]+)</td>"
                    + "<td>(\\d\\d):(\\d\\d)</td><td>(\\d\\d):(\\d\\d)</td><td>(\\d\\d):(\\d\\d)</td>"
                    + "<td>(\\d\\d):(\\d\\d)</td><td>(\\d\\d):(\\d\\d)</td><td>(\\d\\d):(\\d\\d)</td>");
    private static final Map<String, Integer> MONTHS = new HashMap<>();
    static {
        String[][] m = {{"janvier"}, {"février", "fevrier"}, {"mars"}, {"avril"}, {"mai"}, {"juin"},
                {"juillet"}, {"août", "aout"}, {"septembre"}, {"octobre"}, {"novembre"},
                {"décembre", "decembre"}};
        for (int i = 0; i < m.length; i++) for (String n : m[i]) MONTHS.put(n, i + 1);
    }

    private GuideMusulman() { }

    static String dayKey(Calendar c) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Télécharge la page (à appeler hors du fil principal). */
    static String download() throws Exception {
        if (URL == null || URL.isEmpty()) throw new Exception("pas d'adresse Guide Musulman");
        HttpURLConnection c = (HttpURLConnection) new URL(URL).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Android TV) AccueilTV");
        c.setRequestProperty("Accept-Language", "fr-FR,fr");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Extrait le tableau : date (aaaa-mm-jj) → minutes depuis minuit des 6 horaires. */
    static Map<String, int[]> parse(String html, Calendar today) {
        Map<String, int[]> out = new TreeMap<>();
        Matcher m = ROW.matcher(html);
        int todayMonth = today.get(Calendar.MONTH) + 1;
        while (m.find()) {
            Integer month = MONTHS.get(m.group(2).toLowerCase(Locale.FRENCH));
            if (month == null) continue;
            int day = Integer.parseInt(m.group(1));
            int year = today.get(Calendar.YEAR) + (month < todayMonth ? 1 : 0);
            int[] t = new int[6];
            for (int i = 0; i < 6; i++) {
                t[i] = Integer.parseInt(m.group(3 + 2 * i)) * 60 + Integer.parseInt(m.group(4 + 2 * i));
            }
            out.put(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day), t);
        }
        return out;
    }

    static Map<String, int[]> load(SharedPreferences p) {
        Map<String, int[]> out = new TreeMap<>();
        String s = p.getString(KEY, "");
        for (String line : s.split("\n")) {
            String[] f = line.trim().split(" ");
            if (f.length != 7) continue;
            int[] t = new int[6];
            for (int i = 0; i < 6; i++) t[i] = Integer.parseInt(f[i + 1]);
            out.put(f[0], t);
        }
        return out;
    }

    /** Fusionne les nouveaux horaires dans la copie locale et oublie les jours passés. */
    static void save(SharedPreferences p, Map<String, int[]> cache, Map<String, int[]> fresh, String todayKey) {
        cache.putAll(fresh);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, int[]> e : new TreeMap<>(cache).entrySet()) {
            if (e.getKey().compareTo(todayKey) < 0) continue;
            sb.append(e.getKey());
            for (int v : e.getValue()) sb.append(' ').append(v);
            sb.append('\n');
        }
        p.edit().putString(KEY, sb.toString()).putLong("gm_fetched", System.currentTimeMillis()).apply();
    }
}
