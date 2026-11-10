package com.lookin.reader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ส่งข้อมูลไปเครื่องสาขา ถ้าส่งไม่ผ่านจะลองใหม่อัตโนมัติ */
final class Net {
    private static final ExecutorService EX = Executors.newFixedThreadPool(2);
    private static final Handler H = new Handler(Looper.getMainLooper());

    private Net() {}

    static void notifyMoney(Context c, String title, String text, String pkg, String label) {
        LinkedHashMap<String, String> q = new LinkedHashMap<>();
        q.put("text", text);
        q.put("title", title);
        q.put("pkg", pkg);
        q.put("src", "lookin-reader");
        send(c.getApplicationContext(), "/api/qrpay/notify", q, 1, 8, label);
    }

    static void ping(Context c) {
        LinkedHashMap<String, String> q = new LinkedHashMap<>();
        q.put("src", "lookin-reader");
        send(c.getApplicationContext(), "/api/qrpay/ping", q, 1, 3, null);
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    private static void send(final Context c, final String path, final LinkedHashMap<String, String> q,
                             final int attempt, final int max, final String label) {
        EX.execute(() -> {
            String base = Store.server(c), key = Store.key(c);
            if (base.isEmpty() || key.isEmpty()) {
                Store.log(c, "ยังไม่ได้ตั้งค่าที่อยู่เครื่องสาขา/รหัส key");
                return;
            }
            StringBuilder u = new StringBuilder(base).append(path).append("?key=").append(enc(key));
            for (Map.Entry<String, String> e : q.entrySet()) u.append('&').append(e.getKey()).append('=').append(enc(e.getValue()));

            int code = -1;
            String body = "", err = null;
            HttpURLConnection h = null;
            try {
                h = (HttpURLConnection) new URL(u.toString()).openConnection();
                h.setConnectTimeout(5000);
                h.setReadTimeout(7000);
                h.setUseCaches(false);
                code = h.getResponseCode();
                InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
                if (in != null) {
                    byte[] buf = new byte[400];
                    int n = in.read(buf);
                    if (n > 0) body = new String(buf, 0, n, "UTF-8");
                    in.close();
                }
            } catch (Exception ex) {
                err = ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
            } finally {
                if (h != null) h.disconnect();
            }

            long now = System.currentTimeMillis();
            if (code >= 200 && code < 300) {
                if (path.endsWith("/ping")) Store.setLong(c, "lastPingOk", now);
                else Store.setLong(c, "lastNotifyOk", now);
                Store.setStr(c, "lastErr", "");
                if (label != null) Store.log(c, label + " → ส่งสำเร็จ" + (attempt > 1 ? " (ครั้งที่ " + attempt + ")" : "") + "  " + body.replace('\n', ' '));
            } else {
                String why = err != null ? err : "HTTP " + code + " " + body.replace('\n', ' ');
                Store.setStr(c, "lastErr", Store.hm(now) + " " + why);
                if (attempt < max) {
                    if (label != null) Store.log(c, label + " → ส่งไม่ผ่าน (" + why + ") จะลองใหม่");
                    H.postDelayed(() -> send(c, path, q, attempt + 1, max, label), Math.min(15000L, 2000L * attempt));
                } else {
                    Store.log(c, (label != null ? label : "ส่งสัญญาณ") + " → ส่งไม่ผ่าน เลิกลอง (" + why + ")");
                }
            }
        });
    }
}
