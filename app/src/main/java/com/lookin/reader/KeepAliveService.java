package com.lookin.reader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;

/**
 * บริการทำงานตลอดเวลา (มีไอคอนค้างในแถบแจ้งเตือน)
 * ทุก 2 นาที: ส่งสัญญาณ ping + โพสต์แจ้งเตือนทดสอบให้ตัวอ่านจับ
 * ถ้าตัวอ่านไม่เห็นแจ้งเตือนทดสอบเกิน 5 นาที จะขอ Android เชื่อมตัวอ่านใหม่เอง
 */
public class KeepAliveService extends Service {
    static final String ACTION_TEST = "com.lookin.reader.TEST";
    private static final long PERIOD = 2 * 60 * 1000L;
    private static final int ID_RUN = 1, ID_SELF = 77;
    private static final String CH_RUN = "run", CH_SELF = "self";

    private Handler h;
    private WifiManager.WifiLock wifiLock;
    private long startedAt;
    private int selftestNo;

    static void start(Context c) {
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) {
            Store.log(c, "เปิดบริการไม่ได้: " + e);
        }
    }

    static void test(Context c) {
        Intent i = new Intent(c, KeepAliveService.class).setAction(ACTION_TEST);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel run = new NotificationChannel(CH_RUN, "สถานะการทำงาน", NotificationManager.IMPORTANCE_LOW);
        run.setShowBadge(false);
        NotificationChannel self = new NotificationChannel(CH_SELF, "แจ้งเตือนทดสอบตัวเอง", NotificationManager.IMPORTANCE_LOW);
        self.setShowBadge(false);
        nm.createNotificationChannel(run);
        nm.createNotificationChannel(self);
        startForeground(ID_RUN, runNotice("กำลังเริ่ม…"));

        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "lookin:wifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        } catch (Exception e) {
            Store.log(this, "ล็อก Wi-Fi ไม่ได้: " + e);
        }

        startedAt = System.currentTimeMillis();
        h = new Handler(Looper.getMainLooper());
        h.post(tick);
        Store.log(this, "บริการเริ่มทำงาน");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID_RUN, runNotice(statusLine()));
        if (intent != null && ACTION_TEST.equals(intent.getAction())) {
            Store.log(this, "กดทดสอบจากหน้าแอป");
            h.removeCallbacks(tick);
            h.post(tick);
        }
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            PowerManager.WakeLock wl = null;
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lookin:tick");
                wl.acquire(30000);
            } catch (Exception ignored) { }

            Net.ping(KeepAliveService.this);
            postSelftest();
            h.postDelayed(KeepAliveService.this::checkListener, 20000);
            h.postDelayed(this, PERIOD);
        }
    };

    private void postSelftest() {
        selftestNo++;
        Notification n = new Notification.Builder(this, CH_SELF)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(ReaderService.SELFTEST_TITLE)
                .setContentText("test " + Store.hm(System.currentTimeMillis()) + " #" + selftestNo)
                .setTimeoutAfter(15000)
                .setAutoCancel(true)
                .build();
        try {
            getSystemService(NotificationManager.class).notify(ID_SELF, n);
        } catch (Exception e) {
            Store.log(this, "โพสต์แจ้งเตือนทดสอบไม่ได้: " + e);
        }
    }

    private void checkListener() {
        long now = System.currentTimeMillis();
        long seen = Store.getLong(this, "lastSelfSeen");
        boolean stale = now - Math.max(seen, startedAt) > 5 * 60 * 1000L;
        if (stale) {
            long lastFix = Store.getLong(this, "lastRebind");
            if (now - lastFix > 4 * 60 * 1000L) {
                Store.setLong(this, "lastRebind", now);
                Store.log(this, "ไม่เห็นแจ้งเตือนทดสอบตั้งแต่ " + Store.hm(seen) + " — ขอ Android เชื่อมตัวอ่านใหม่");
                try {
                    NotificationListenerService.requestRebind(new ComponentName(this, ReaderService.class));
                } catch (Exception e) {
                    Store.log(this, "ขอเชื่อมใหม่ไม่ได้: " + e);
                }
            }
        }
        getSystemService(NotificationManager.class).notify(ID_RUN, runNotice(statusLine()));
    }

    private String statusLine() {
        long seen = Store.getLong(this, "lastSelfSeen");
        boolean ok = seen > 0 && System.currentTimeMillis() - seen < 6 * 60 * 1000L;
        return (ok ? "✓ อ่านแจ้งเตือนได้" : "⚠ ยังไม่เห็นแจ้งเตือนทดสอบ") + " · ทดสอบล่าสุด " + Store.hm(seen);
    }

    private Notification runNotice(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH_RUN)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Lookin Reader ทำงานอยู่")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        Store.log(this, "บริการถูกปิด");
        if (h != null) h.removeCallbacksAndMessages(null);
        try { if (wifiLock != null) wifiLock.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
