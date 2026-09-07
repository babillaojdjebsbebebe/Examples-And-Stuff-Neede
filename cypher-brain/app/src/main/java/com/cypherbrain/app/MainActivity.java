package com.cypherbrain.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestRuntimePermissions();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 56, 40, 40);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(12, 12, 14));

        TextView title = new TextView(this);
        title.setText("CYPHER BRAIN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView info = new TextView(this);
        info.setText("Segundo cerebro para freestyle.\n\nESCUCHAR = analiza la voz ajena que llega al micrófono.\nMI TURNO = detiene inmediatamente el reconocimiento.\n\nEl audio no se guarda.");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(16);
        info.setPadding(0, 30, 0, 30);
        root.addView(info, matchWrap());

        Button overlay = button("1 · ACTIVAR BURBUJA");
        overlay.setOnClickListener(v -> enableOverlay());
        root.addView(overlay, matchWrap());

        Button listen = button("👂 ESCUCHAR RIVAL");
        listen.setOnClickListener(v -> sendServiceAction(OverlayService.ACTION_LISTEN));
        root.addView(listen, matchWrap());

        Button myTurn = button("🔥 MI TURNO");
        myTurn.setOnClickListener(v -> sendServiceAction(OverlayService.ACTION_STOP_LISTEN));
        root.addView(myTurn, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("Consejo: usa Discord por altavoz cuando quieras que el micrófono del teléfono escuche al otro participante. Android puede impedir que dos apps usen el mismo micrófono simultáneamente.");
        hint.setTextColor(Color.GRAY);
        hint.setTextSize(13);
        hint.setPadding(0, 30, 0, 0);
        root.addView(hint, matchWrap());
        return root;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 10, 0, 10);
        return p;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        return b;
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        }
        startOverlayService();
    }

    private void startOverlayService() {
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void sendServiceAction(String action) {
        if (!Settings.canDrawOverlays(this)) {
            enableOverlay();
            return;
        }
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startOverlayService();
        }
    }
}
