package com.dxmaster.multispace;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class KeepAliveService extends Service {
    private PowerManager.WakeLock wl;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String ch = "dx_call";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(ch, "DX Call", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        nb.setContentTitle("DX MultiSpace")
          .setContentText("কল চলছে...")
          .setSmallIcon(android.R.drawable.ic_btn_speak_now)
          .setContentIntent(PendingIntent.getActivity(this, 0,
                  new Intent(this, HubActivity.class), PendingIntent.FLAG_IMMUTABLE));
        Notification n = nb.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, n);
        }
        if (wl == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dx:call");
            wl.acquire();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wl != null && wl.isHeld()) wl.release();
        super.onDestroy();
    }
}
