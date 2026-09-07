package com.cypherbrain.app;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;
    private static final String START_OVERLAY = "START_OVERLAY";

    private String pendingAction;
    private TextView setupStatus;
    private LocalEngineDiagnostics.Result engineStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engineStatus = LocalEngineDiagnostics.run(this);
        setContentView(buildUi());
        refreshSetupStatus();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(34), dp(28), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(12, 12, 14));

        TextView title = new TextView(this);
        title.setText("CYPHER BRAIN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Segundo cerebro de freestyle · fonema · multis ×10 · asociaciones · puentes");
        subtitle.setTextColor(Color.rgb(185, 185, 195));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle, matchWrap());

        setupStatus = new TextView(this);
        setupStatus.setTextColor(Color.LTGRAY);
        setupStatus.setTextSize(14);
        setupStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(setupStatus, matchWrap());

        if (!engineStatus.ok) {
            TextView engineError = new TextView(this);
            engineError.setText("⚠ MOTOR LOCAL: " + engineStatus.message);
            engineError.setTextColor(Color.rgb(255, 110, 110));
            engineError.setTextSize(13);
            root.addView(engineError, matchWrap());
        } else {
            TextView engineInfo = new TextView(this);
            engineInfo.setText(
                    "Motor local OK · " + engineStatus.bridges + " puentes · " +
                    engineStatus.domains + " universos · " +
                    engineStatus.rhymeFamilies + " familias fonéticas"
            );
            engineInfo.setTextColor(Color.rgb(120, 230, 165));
            engineInfo.setTextSize(12);
            root.addView(engineInfo, matchWrap());
        }

        Button overlay = button("1 · ACTIVAR BURBUJA");
        overlay.setEnabled(engineStatus.ok);
        overlay.setOnClickListener(v -> enableOverlay());
        root.addView(overlay, matchWrap());

        Button compatibility = button("2 · COMPATIBILIDAD DISCORD · OPCIONAL");
        compatibility.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(compatibility, matchWrap());

        Button listen = button("👂 ESCUCHAR RIVAL");
        listen.setEnabled(engineStatus.ok);
        listen.setOnClickListener(v -> sendServiceAction(OverlayService.ACTION_LISTEN));
        root.addView(listen, matchWrap());

        Button myTurn = button("🔥 MI TURNO · CORTAR AUDIO");
        myTurn.setOnClickListener(v -> sendServiceAction(OverlayService.ACTION_STOP_LISTEN));
        root.addView(myTurn, matchWrap());

        TextView info = new TextView(this);
        info.setText("La app no guarda audio. MI TURNO cancela el reconocimiento y cualquier resultado tardío se descarta.\n\nSi Discord monopoliza el micrófono en tu teléfono, activa Compatibilidad Discord. Ese servicio no lee texto de pantalla, no ejecuta gestos y no procesa eventos de accesibilidad.");
        info.setTextColor(Color.GRAY);
        info.setTextSize(12);
        info.setPadding(0, dp(20), 0, 0);
        root.addView(info, matchWrap());
        return root;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(6), 0, dp(6));
        return p;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission(String actionAfterGrant) {
        pendingAction = actionAfterGrant;
        if (hasAudioPermission()) {
            continuePendingAction();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void enableOverlay() {
        pendingAction = START_OVERLAY;
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(i);
            return;
        }
        requestAudioPermission(START_OVERLAY);
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void startOverlayService() {
        if (!engineStatus.ok || !hasAudioPermission() || !Settings.canDrawOverlays(this)) return;
        requestNotificationPermissionIfNeeded();
        Intent i = new Intent(this, OverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (RuntimeException e) {
            Toast.makeText(
                    this,
                    "No se pudo iniciar la burbuja. Abre Cypher Brain e inténtalo de nuevo.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void sendServiceAction(String action) {
        if (!engineStatus.ok && OverlayService.ACTION_LISTEN.equals(action)) {
            Toast.makeText(this, "El motor local no superó el autodiagnóstico.", Toast.LENGTH_LONG).show();
            return;
        }

        pendingAction = action;
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(i);
            return;
        }
        if (!hasAudioPermission()) {
            requestAudioPermission(action);
            return;
        }
        dispatchServiceAction(action);
        pendingAction = null;
    }

    private void dispatchServiceAction(String action) {
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Abre primero la burbuja de Cypher Brain.", Toast.LENGTH_SHORT).show();
        }
    }

    private void continuePendingAction() {
        String action = pendingAction;
        pendingAction = null;
        if (action == null || START_OVERLAY.equals(action)) {
            startOverlayService();
        } else {
            dispatchServiceAction(action);
        }
    }

    private boolean isCompatibilityServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabled)) return false;

        ComponentName target = new ComponentName(this, AudioCompatibilityAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName candidate = ComponentName.unflattenFromString(splitter.next());
            if (target.equals(candidate)) return true;
        }
        return false;
    }

    private void refreshSetupStatus() {
        if (setupStatus == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean mic = hasAudioPermission();
        boolean compat = isCompatibilityServiceEnabled();

        setupStatus.setText(
                (engineStatus.ok ? "✓" : "✕") + " Motor   " +
                (overlay ? "✓" : "○") + " Burbuja   " +
                (mic ? "✓" : "○") + " Micrófono   " +
                (compat ? "✓" : "○") + " Discord"
        );
        setupStatus.setTextColor(
                engineStatus.ok && overlay && mic
                        ? Color.rgb(120, 230, 165)
                        : Color.rgb(235, 190, 90)
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                continuePendingAction();
            } else {
                pendingAction = null;
                Toast.makeText(
                        this,
                        "El modo ESCUCHAR necesita permiso de micrófono.",
                        Toast.LENGTH_LONG
                ).show();
            }
            refreshSetupStatus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSetupStatus();

        if (Settings.canDrawOverlays(this) && pendingAction != null) {
            if (hasAudioPermission()) continuePendingAction();
            else requestAudioPermission(pendingAction);
        }
    }
}
