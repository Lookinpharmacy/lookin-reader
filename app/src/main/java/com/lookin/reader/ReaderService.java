package com.lookin.reader;

import android.app.Notification;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** ตัวอ่านแจ้งเตือน: Android เรียกทุกครั้งที่มีแจ้งเตือนใหม่ หรือแจ้งเตือนเดิมถูกอัปเดตทับ */
public class ReaderService extends NotificationListenerService {
    static final String SELFTEST_TITLE = "LOOKIN-SELFTEST";

    /** ชื่อแอปหรือชื่อแพ็กเกจที่ถือว่าเป็นแอปรับเงิน (ตัวพิมพ์เล็ก) */
    private static final String[] MONEY_APPS = {
            "แม่มณี", "mae manee", "maemanee", "mae-manee",
            "ถุงเงิน", "tung ngern", "tungngern", "thungngern",
            "k shop", "kshop", "k+ shop"
    };

    private static final Map<String, Long> RECENT = new HashMap<>();

    @Override
    public void onListenerConnected() {
        Store.setLong(this, "listenerConnected", System.currentTimeMillis());
        Store.log(this, "ตัวอ่านแจ้งเตือนเชื่อมต่อแล้ว");
        KeepAliveService.start(this);
    }

    @Override
    public void onListenerDisconnected() {
        Store.setLong(this, "listenerConnected", 0);
        Store.log(this, "ตัวอ่านแจ้งเตือนถูกตัด — ขอเชื่อมใหม่");
        try { requestRebind(new ComponentName(this, ReaderService.class)); } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try { handle(sbn); } catch (Throwable t) { Store.log(this, "อ่านแจ้งเตือนผิดพลาด: " + t); }
    }

    private void handle(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle e = n.extras;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));

        // แจ้งเตือนทดสอบของแอปเราเอง = พิสูจน์ว่ายังอ่านแจ้งเตือนได้จริง
        if (pkg.equals(getPackageName())) {
            if (SELFTEST_TITLE.equals(title)) {
                Store.setLong(this, "lastSelfSeen", System.currentTimeMillis());
                Net.notifyMoney(this, title, str(e.getCharSequence(Notification.EXTRA_TEXT)), pkg, null);
            }
            return;
        }

        String body = pickBody(n, e);
        String all = (title + " " + body);
        if (!all.contains("บาท") && !all.toLowerCase(Locale.ROOT).contains("thb")) return;

        String label = appLabel(pkg);
        if (!isMoneyApp(pkg, label)) {
            Store.log(this, "ข้าม (แอป " + label + " / " + pkg + "): " + cut(all));
            return;
        }
        if (duplicate(pkg + "|" + title + "|" + body)) return;

        Store.setLong(this, "lastMoneySeen", System.currentTimeMillis());
        Net.notifyMoney(this, title, body, pkg, "เงินเข้า [" + label + "] " + cut(body));
    }

    /** เลือกข้อความที่มีจำนวนเงิน รองรับแจ้งเตือนแบบยาว แบบรวมหลายรายการ และแบบข้อความแชต */
    private static String pickBody(Notification n, Bundle e) {
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (big.contains("บาท")) return big;
        if (text.contains("บาท")) return text;
        String ticker = str(n.tickerText);
        if (ticker.contains("บาท")) return ticker;
        Parcelable[] msgs = e.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null) {
            for (int i = msgs.length - 1; i >= 0; i--) {
                if (msgs[i] instanceof Bundle) {
                    String t = str(((Bundle) msgs[i]).getCharSequence("text"));
                    if (t.contains("บาท")) return t;
                }
            }
        }
        CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence l : lines) {
                String t = str(l);
                if (t.contains("บาท")) return t;
            }
        }
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        if (sub.contains("บาท")) return sub;
        return big.isEmpty() ? text : big;
    }

    private boolean isMoneyApp(String pkg, String label) {
        String s = (pkg + " " + label).toLowerCase(Locale.ROOT);
        for (String k : MONEY_APPS) if (s.contains(k)) return true;
        String extra = Store.getStr(this, "extraApps").toLowerCase(Locale.ROOT);
        for (String k : extra.split(",")) {
            k = k.trim();
            if (!k.isEmpty() && s.contains(k)) return true;
        }
        return false;
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception ex) {
            return pkg;
        }
    }

    /** กันส่งซ้ำ เมื่อแอปส่งแจ้งเตือนข้อความเดียวกันซ้ำภายใน 20 วินาที (เช่น แจ้งเตือนกลุ่ม) */
    private static synchronized boolean duplicate(String k) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = RECENT.entrySet().iterator();
        while (it.hasNext()) if (now - it.next().getValue() > 20000) it.remove();
        if (RECENT.containsKey(k)) return true;
        RECENT.put(k, now);
        return false;
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    private static String cut(String s) { return s.length() > 70 ? s.substring(0, 70) + "…" : s; }
}
