package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.layla.bot.accessibility.ChatReader;
import com.layla.bot.accessibility.ChatWriter;
import com.layla.bot.accessibility.Config;
import com.layla.bot.accessibility.NodeUtils;
import com.layla.bot.domain.ChatParser;
import com.layla.bot.service.BotState;
import com.layla.bot.service.LecturaUseCase;

public class LaylaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LaylaBot";

    private static LaylaAccessibilityService instance;
    private static final BotState STATE = new BotState();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ChatReader chatReader = new ChatReader();
    private final ChatWriter chatWriter = new ChatWriter(this, handler);
    private final LecturaUseCase lecturaUseCase =
            new LecturaUseCase(new ChatParser(), STATE);
    private final Runnable leer = () -> leerPantalla();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        STATE.marcarServicioActivo(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        STATE.marcarServicioActivo(false);
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!Config.LAYLA_PKG.equals(event.getPackageName().toString())) return;
        handler.removeCallbacks(leer);
        handler.postDelayed(leer, Config.DEBOUNCE_MS);
    }

    @Override
    public void onInterrupt() {}

    public static String informe() {
        return STATE.informe();
    }

    public static void programarPruebaEscritura() {
        final LaylaAccessibilityService s = instance;
        if (s == null) {
            STATE.setPruebaEstado("⚠️ El servicio no está activo.");
            return;
        }
        STATE.setPruebaEstado("Esperando " + (Config.DELAY_PRUEBA_MS / 1000)
                + " segundos... ve a Layla y abre un chat.");
        s.handler.postDelayed(
                () -> s.chatWriter.escribirYEnviar("prueba del bot",
                        resultado -> STATE.setPruebaEstado(resultado)),
                Config.DELAY_PRUEBA_MS);
    }

    private void leerPantalla() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return;
            if (!Config.LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) return;

            STATE.setPantalla(chatReader.volcarPantalla(root));
            String conversacion = chatReader.leerConversacion(root);
            lecturaUseCase.procesar(conversacion);
        } catch (Exception e) {
            Log.e(TAG, "Error al leer", e);
            STATE.setEstado("Error al leer: " + e);
        } finally {
            NodeUtils.recycle(root);
        }
    }
}