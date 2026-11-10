package com.lookin.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** เก็บค่าตั้งค่า สถานะ และบันทึกการทำงาน */
final class Store {
    static final String DEFAULT_SERVER = "http://192.168.1.201:3001";
    private static final int MAX_LOG_LINES = 80;

    private Store() {}

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("lookin", Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        String s = p(c).getString("server", DEFAULT_SERVER).trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.isEmpty() && !s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        return s;
    }

    static String key(Context c) {
        return p(c).getString("key", "").trim();
    }

    /** รับค่าที่ผู้ใช้กรอก ถ้าวางลิงก์เต็มจาก MacroDroid มา จะแยกที่อยู่และ key ให้เอง */
    static String save(Context c, String serverIn, String keyIn) {
        String server = serverIn == null ? "" : serverIn.trim();
        String key = keyIn == null ? "" : keyIn.trim();
        for (String s : new String[]{server, key}) {
            if (s.contains("key=")) {
                try {
                    Uri u = Uri.parse(s.startsWith("http") ? s : "http://" + s);
                    String k = u.getQueryParameter("key");
                    if (k != null && !k.isEmpty()) key = k;
                    if (u.getHost() != null) {
                        server = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
                    }
                } catch (Exception ignored) { }
            }
        }
        p(c).edit().putString("server", server).putString("key", key).apply();
        return server(c);
    }

    static long getLong(Context c, String k) { return p(c).getLong(k, 0L); }

    static void setLong(Context c, String k, long v) { p(c).edit().putLong(k, v).apply(); }

    static String getStr(Context c, String k) { return p(c).getString(k, ""); }

    static void setStr(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }

    static String hm(long t) {
        if (t <= 0) return "-";
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(t));
    }

    static synchronized void log(Context c, String msg) {
        String line = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date()) + "  " + msg;
        String old = p(c).getString("log", "");
        StringBuilder sb = new StringBuilder(line);
        if (!old.isEmpty()) {
            String[] lines = old.split("\n");
            for (int i = 0; i < lines.length && i < MAX_LOG_LINES - 1; i++) sb.append('\n').append(lines[i]);
        }
        p(c).edit().putString("log", sb.toString()).apply();
    }

    static String logText(Context c) { return p(c).getString("log", ""); }
}
