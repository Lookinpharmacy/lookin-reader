package com.lookin.reader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** หน้าตั้งค่าและดูสถานะ */
public class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(20, 137, 78), RED = Color.rgb(180, 35, 24), INK = Color.rgb(29, 27, 46);

    private EditText server, key, extra;
    private TextView stAccess, stNotif, stBattery, stSelf, stNet, logView;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 2000); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = text("Lookin Reader", 24, true);
        root.addView(title);
        root.addView(text("ตัวอ่านแจ้งเตือนเงินเข้า (แม่มณี / ถุงเงิน / K SHOP) ส่งไปเครื่องสาขา v" + version(), 14, false));

        root.addView(header("ขั้นที่ 1  ตั้งค่าเครื่องสาขา"));
        root.addView(text("ที่อยู่เครื่องสาขา", 14, false));
        server = edit(Store.server(this), "http://192.168.1.201:3001");
        root.addView(server);
        root.addView(text("รหัส key (หรือวางลิงก์เต็มจาก MacroDroid ลงช่องนี้ได้เลย)", 14, false));
        key = edit(Store.key(this), "เช่น bbb38f…");
        root.addView(key);
        Button save = button("บันทึก");
        save.setOnClickListener(v -> {
            Store.save(this, server.getText().toString(), key.getText().toString());
            server.setText(Store.server(this));
            key.setText(Store.key(this));
            Store.log(this, "บันทึกการตั้งค่า: " + Store.server(this));
            Toast.makeText(this, "บันทึกแล้ว", Toast.LENGTH_SHORT).show();
            KeepAliveService.test(this);
        });
        root.addView(save);

        root.addView(header("ขั้นที่ 2  ให้สิทธิ์ (ทุกข้อต้องเป็น ✓ สีเขียว)"));
        stAccess = text("", 16, true);
        root.addView(stAccess);
        Button bAccess = button("เปิดหน้าสิทธิ์อ่านแจ้งเตือน");
        bAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(bAccess);

        stNotif = text("", 16, true);
        root.addView(stNotif);
        Button bNotif = button("อนุญาตให้แอปแสดงแจ้งเตือน");
        bNotif.setOnClickListener(v -> askPostNotifications(true));
        root.addView(bNotif);

        stBattery = text("", 16, true);
        root.addView(stBattery);
        Button bBat = button("ปิดการประหยัดแบตเตอรี่ให้แอปนี้");
        bBat.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        root.addView(bBat);

        root.addView(header("ขั้นที่ 3  ทดสอบ"));
        stSelf = text("", 16, true);
        root.addView(stSelf);
        stNet = text("", 15, false);
        root.addView(stNet);
        Button bTest = button("ทดสอบตอนนี้");
        bTest.setOnClickListener(v -> {
            KeepAliveService.test(this);
            Toast.makeText(this, "ส่งแจ้งเตือนทดสอบแล้ว รอ 5 วินาที", Toast.LENGTH_SHORT).show();
        });
        root.addView(bTest);

        root.addView(header("ตั้งค่าเพิ่มเติม"));
        root.addView(text("แอปรับเงินอื่นที่ให้อ่านด้วย (พิมพ์ชื่อแอป คั่นด้วย , ) — ปกติเว้นว่างไว้", 14, false));
        extra = edit(Store.getStr(this, "extraApps"), "");
        root.addView(extra);
        Button bExtra = button("บันทึกรายชื่อแอป");
        bExtra.setOnClickListener(v -> {
            Store.setStr(this, "extraApps", extra.getText().toString().trim());
            Toast.makeText(this, "บันทึกแล้ว", Toast.LENGTH_SHORT).show();
        });
        root.addView(bExtra);

        root.addView(header("บันทึกการทำงาน (ล่าสุดอยู่บน)"));
        logView = text("", 12, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        sv.addView(root);
        setContentView(sv);

        askPostNotifications(false);
        KeepAliveService.start(this);
    }

    @Override protected void onResume() { super.onResume(); h.post(refresher); }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(refresher); }

    private void refresh() {
        boolean access = hasListenerAccess();
        mark(stAccess, access, "สิทธิ์อ่านแจ้งเตือน: เปิดแล้ว", "สิทธิ์อ่านแจ้งเตือน: ยังไม่เปิด — กดปุ่มด้านล่าง แล้วเปิด Lookin Reader");

        boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        mark(stNotif, notif, "แสดงแจ้งเตือนได้", "ยังไม่อนุญาตให้แสดงแจ้งเตือน (จำเป็นสำหรับการทดสอบตัวเอง)");

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean bat = pm.isIgnoringBatteryOptimizations(getPackageName());
        mark(stBattery, bat, "แบตเตอรี่: ไม่จำกัด", "แบตเตอรี่: ยังถูกจำกัด — กดปุ่มด้านล่าง แล้วเลือก อนุญาต");

        long now = System.currentTimeMillis();
        long seen = Store.getLong(this, "lastSelfSeen");
        boolean selfOk = seen > 0 && now - seen < 6 * 60 * 1000L;
        mark(stSelf, selfOk, "อ่านแจ้งเตือนทดสอบได้ (ล่าสุด " + Store.hm(seen) + ")",
                "ยังไม่เห็นแจ้งเตือนทดสอบ (ล่าสุด " + Store.hm(seen) + ")");

        long ping = Store.getLong(this, "lastPingOk"), noti = Store.getLong(this, "lastNotifyOk");
        boolean netOk = ping > 0 && now - ping < 6 * 60 * 1000L;
        String err = Store.getStr(this, "lastErr");
        stNet.setText((netOk ? "✓ " : "✗ ") + "ติดต่อเครื่องสาขาล่าสุด " + Store.hm(Math.max(ping, noti))
                + (err.isEmpty() ? "" : "\nผิดพลาดล่าสุด: " + err));
        stNet.setTextColor(netOk ? GREEN : RED);

        logView.setText(Store.logText(this));
    }

    private void mark(TextView t, boolean ok, String good, String bad) {
        t.setText((ok ? "✓ " : "✗ ") + (ok ? good : bad));
        t.setTextColor(ok ? GREEN : RED);
    }

    private boolean hasListenerAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    private void askPostNotifications(boolean openSettingsIfDenied) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
            if (openSettingsIfDenied && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openAppNotificationSettings();
            }
        } else if (openSettingsIfDenied) {
            openAppNotificationSettings();
        }
    }

    private void openAppNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(i); } catch (Exception ignored) { }
    }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(INK);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 18, true);
        t.setTextColor(Color.rgb(75, 42, 134));
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setTextColor(INK);
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }
}
