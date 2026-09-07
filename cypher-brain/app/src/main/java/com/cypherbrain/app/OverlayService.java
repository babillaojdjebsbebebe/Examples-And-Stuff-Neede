package com.cypherbrain.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service implements RecognitionListener {
    public static final String ACTION_LISTEN = "com.cypherbrain.app.LISTEN";
    public static final String ACTION_STOP_LISTEN = "com.cypherbrain.app.STOP_LISTEN";
    private static final String CHANNEL_ID = "cypher_brain_live";
    private static final int NOTIFICATION_ID = 41;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LinearLayout overlay;
    private LinearLayout panel;
    private TextView bubble;
    private TextView status;
    private TextView transcript;
    private TextView output;
    private Button complexityButton;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private BridgeRepository bridges;
    private RhymeEngine rhymes;
    private boolean listening = false;
    private boolean recognitionRunning = false;
    private String lastRendered = "";
    private int complexity = 2;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Listo · micrófono inactivo"));
        bridges = new BridgeRepository(this);
        rhymes = new RhymeEngine(this);
        setupRecognizer();
        if (Settings.canDrawOverlays(this)) createOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (overlay == null) createOverlay();
        String action = intent == null ? null : intent.getAction();
        if (ACTION_LISTEN.equals(action)) startListeningMode();
        else if (ACTION_STOP_LISTEN.equals(action)) stopListeningMode();
        return START_STICKY;
    }

    private void setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PA");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PA");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
    }

    private void createOverlay() {
        if (overlay != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.END);

        bubble = new TextView(this);
        bubble.setText("CB");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(16);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(round(Color.rgb(116, 66, 255), 60));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(56), dp(56));
        bp.gravity = Gravity.END;
        overlay.addView(bubble, bp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setBackground(round(Color.argb(245, 15, 15, 18), 22));
        panel.setVisibility(View.GONE);
        overlay.addView(panel, new LinearLayout.LayoutParams(dp(340), dp(470)));

        status = text("● LISTO", 13, Color.LTGRAY);
        panel.addView(status);

        transcript = text("Esperando frase…", 15, Color.WHITE);
        transcript.setPadding(0, dp(8), 0, dp(8));
        panel.addView(transcript);

        ScrollView scroll = new ScrollView(this);
        output = text("Grafo local: " + bridges.potentialConnections() + " conexiones posibles.", 14, Color.rgb(225, 225, 230));
        scroll.addView(output);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        complexityButton = smallButton("COMPLEJIDAD · " + complexityName());
        complexityButton.setOnClickListener(v -> cycleComplexity());
        panel.addView(complexityButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button listen = smallButton("👂 ESCUCHAR");
        Button stop = smallButton("🔥 MI TURNO");
        controls.addView(listen, new LinearLayout.LayoutParams(0, dp(48), 1f));
        controls.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1f));
        panel.addView(controls);

        listen.setOnClickListener(v -> startListeningMode());
        stop.setOnClickListener(v -> stopListeningMode());
        bubble.setOnClickListener(v -> panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(8);
        params.y = dp(180);
        addDrag(bubble, params);
        windowManager.addView(overlay, params);
    }

    private void cycleComplexity() {
        complexity = complexity == 3 ? 1 : complexity + 1;
        if (complexityButton != null) complexityButton.setText("COMPLEJIDAD · " + complexityName());
        String current = lastRendered;
        lastRendered = "";
        if (current != null && !current.isEmpty()) renderPhrase(current);
    }

    private String complexityName() {
        if (complexity == 1) return "DIRECTO";
        if (complexity == 3) return "NICHO";
        return "COMPLEJO";
    }

    private void addDrag(View handle, WindowManager.LayoutParams params) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = params.x; startY = params.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                        params.x = Math.max(0, startX - (int) dx);
                        params.y = Math.max(0, startY + (int) dy);
                        if (windowManager != null && overlay != null) windowManager.updateViewLayout(overlay, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void startListeningMode() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("FALTA PERMISO DE MICRÓFONO", Color.RED);
            return;
        }
        if (recognizer == null) {
            setStatus("RECONOCIMIENTO NO DISPONIBLE", Color.RED);
            return;
        }
        listening = true;
        panel.setVisibility(View.VISIBLE);
        bubble.setText("👂");
        setStatus("● ESCUCHANDO RIVAL", Color.rgb(98, 230, 150));
        updateNotification("Escuchando · toca MI TURNO para parar");
        beginRecognition();
    }

    private void stopListeningMode() {
        listening = false;
        recognitionRunning = false;
        main.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
        }
        if (bubble != null) bubble.setText("CB");
        setStatus("● MI TURNO · AUDIO INACTIVO", Color.rgb(255, 190, 70));
        updateNotification("Mi turno · micrófono inactivo");
    }

    private void beginRecognition() {
        if (!listening || recognitionRunning || recognizer == null) return;
        recognitionRunning = true;
        try {
            recognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            recognitionRunning = false;
            scheduleRestart(700);
        }
    }

    private void scheduleRestart(long delay) {
        if (!listening) return;
        main.postDelayed(this::beginRecognition, delay);
    }

    private void renderPhrase(String phrase) {
        if (phrase == null) return;
        String clean = phrase.trim();
        if (clean.length() < 2 || clean.equalsIgnoreCase(lastRendered)) return;
        lastRendered = clean;

        String concept = bridges.bestConceptFromPhrase(clean);
        List<String> pack = rhymes.rhymePack(clean, 10);
        List<String> assoc = bridges.directAssociations(concept, 6);
        List<String> routes = bridges.bridgesFor(concept, 6, complexity);

        transcript.setText("“" + clean + "”\nFOCO: " + concept + "  ·  FONEMA: /" + rhymes.phonemePattern(clean) + "/");
        StringBuilder s = new StringBuilder();
        s.append("COMPLEJIDAD: ").append(complexityName()).append("\n\nMULTIS ×10\n");
        for (int i = 0; i < pack.size(); i++) {
            if (i > 0) s.append(" · ");
            s.append(pack.get(i));
        }
        s.append("\n\nASOCIACIONES\n");
        for (String a : assoc) s.append("• ").append(a).append('\n');
        s.append("\nPUENTES\n");
        for (String r : routes) s.append("• ").append(r).append('\n');
        output.setText(s.toString());
    }

    private ArrayList<String> matches(Bundle results) {
        return results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    }

    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { recognitionRunning = false; }

    @Override
    public void onError(int error) {
        recognitionRunning = false;
        if (listening) scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 900 : 350);
    }

    @Override
    public void onResults(Bundle results) {
        recognitionRunning = false;
        ArrayList<String> m = matches(results);
        if (m != null && !m.isEmpty()) renderPhrase(m.get(0));
        scheduleRestart(180);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> m = matches(partialResults);
        if (m != null && !m.isEmpty() && m.get(0).length() >= 4) renderPhrase(m.get(0));
    }

    @Override public void onEvent(int eventType, Bundle params) {}

    private void setStatus(String text, int color) {
        if (status != null) { status.setText(text); status.setTextColor(color); }
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(12);
        return b;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Cypher Brain live", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Estado del modo de escucha de Cypher Brain");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification buildNotification(String state) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("Cypher Brain")
                .setContentText(state)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String state) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(state));
    }

    @Override
    public void onDestroy() {
        listening = false;
        main.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
