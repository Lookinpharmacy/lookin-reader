package com.lookin.reader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** เปิดตัวเองหลังเปิดเครื่อง หรือหลังอัปเดตแอป */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Store.log(c, "เริ่มทำงานอัตโนมัติ (" + i.getAction() + ")");
        KeepAliveService.start(c);
    }
}
