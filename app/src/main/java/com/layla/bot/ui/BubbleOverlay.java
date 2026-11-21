package com.layla.bot.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.layla.bot.MainActivity;

public class BubbleOverlay {

    private static final String TAG = "BubbleOverlay";
    private static final long PULSACION_LARGA_MS = 700;

    public interface OnToggleListener {
        void onToggle(boolean activo);
    }
    
    // NUEVO: Callback para avisar si falla la creación
    public interface OnErrorListener {
        void onError(String error);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final OnToggleListener listener;
    private final OnErrorListener errorListener;

    private WindowManager windowManager;
    private TextView burbuja;
    private WindowManager.LayoutParams params;

    public BubbleOverlay(Context context, SharedPreferences prefs, 
                         OnToggleListener listener, OnErrorListener errorListener) {
        this.context = context;
        this.prefs = prefs;
        this.listener = listener;
        this.errorListener = errorListener;
    }

    public void crear() {
        if (burbuja != null) return;
        try {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int tam = dp(54);

            burbuja = new TextView(context);
            burbuja.setGravity(Gravity.CENTER);
            burbuja.setTextColor(Color.WHITE);
            burbuja.setTextSize(12);
            burbuja.setTypeface(Typeface.DEFAULT_BOLD);
            burbuja.setContentDescription("Botón del bot de Evs");

            params = new WindowManager.LayoutParams(
                    tam, tam,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            params.x = prefs.getInt("bubble_x", dm.widthPixels - tam - dp(8));
            params.y = prefs.getInt("bubble_y", dm.heightPixels / 3);

            configurarTouch();
            windowManager.addView(burbuja, params);
            actualizar(prefs.getBoolean("auto_activo", false), false);
            
            if (errorListener != null) errorListener.onError(""); // Limpiar error
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.e(TAG, "Error creando burbuja", e);
            if (errorListener != null) errorListener.onError(error);
            burbuja = null;
        }
    }

    public void quitar() {
        if (burbuja != null && windowManager != null) {
            try {
                windowManager.removeView(burbuja);
            } catch (Exception e) {
                Log.w(TAG, "No se pudo quitar la burbuja", e);
            }
        }
        burbuja = null;
    }

    public void actualizar(boolean activo, boolean esperando) {
        if (burbuja == null) return;
        int color;
        String texto;
        if (!activo) {
            color = 0xFF6B7785;
            texto = "OFF";
        } else if (esperando) {
            color = 0xFFF59E0B;
            texto = "···";
        } else {
            color = 0xFF1E8E5A;
            texto = "ON";
        }
        GradientDrawable fondo = new GradientDrawable();
        fondo.setShape(GradientDrawable.OVAL);
        fondo.setColor(color);
        fondo.setStroke(dp(2), 0xFFFFFFFF);
        burbuja.setBackground(fondo);
        burbuja.setText(texto);
        burbuja.setAlpha(0.9f);
    }

    private void configurarTouch() {
        burbuja.setOnTouchListener(new View.OnTouchListener() {
            private int inicioX, inicioY;
            private float toqueX, toqueY;
            private boolean arrastrando;
            private long inicioToque;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        inicioX = params.x;
                        inicioY = params.y;
                        toqueX = e.getRawX();
                        toqueY = e.getRawY();
                        arrastrando = false;
                        inicioToque = SystemClock.uptimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - toqueX;
                        float dy = e.getRawY() - toqueY;
                        if (!arrastrando && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) {
                            arrastrando = true;
                        }
                        if (arrastrando) {
                            params.x = inicioX + (int) dx;
                            params.y = inicioY + (int) dy;
                            windowManager.updateViewLayout(v, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (arrastrando) {
                            prefs.edit()
                                    .putInt("bubble_x", params.x)
                                    .putInt("bubble_y", params.y)
                                    .apply();
                        } else if (SystemClock.uptimeMillis() - inicioToque >= PULSACION_LARGA_MS) {
                            abrirPanel();
                        } else {
                            alternar();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void alternar() {
        boolean nuevo = !prefs.getBoolean("auto_activo", false);
        prefs.edit().putBoolean("auto_activo", nuevo).apply();
        Toast.makeText(context, nuevo ? "Bot ACTIVADO" : "Bot en pausa",
                Toast.LENGTH_SHORT).show();
        if (listener != null) listener.onToggle(nuevo);
    }

    private void abrirPanel() {
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            context.startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo abrir el panel", e);
        }
    }

    private int dp(int valor) {
        return Math.round(valor * context.getResources().getDisplayMetrics().density);
    }
}