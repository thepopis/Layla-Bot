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
import com.layla.bot.service.SiguienteChatUseCase;

public class LaylaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LaylaBot";

    private static LaylaAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ChatReader chatReader;
    private ChatWriter chatWriter;
    private BotState state;
    private LecturaUseCase lecturaUseCase;
    private SiguienteChatUseCase siguienteChatUseCase;

    private final Runnable leer = () -> leerPantalla();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        state = new BotState(this);
        chatReader = new ChatReader();
        chatWriter = new ChatWriter(this, handler);
        lecturaUseCase = new LecturaUseCase(new ChatParser(), state);
        siguienteChatUseCase = new SiguienteChatUseCase(new ChatParser(), state);
        state.marcarServicioActivo(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (state != null) state.marcarServicioActivo(false);
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
        if (instance == null || instance.state == null) return "Servicio no conectado aún.";
        return instance.state.informe();
    }

    public static void programarPruebaEscritura() {
        final LaylaAccessibilityService s = instance;
        if (s == null || s.state == null) return;
        s.state.setPruebaEstado("Esperando " + (Config.DELAY_PRUEBA_MS / 1000)
                + " segundos... ve a Layla y abre un chat.");
        s.handler.postDelayed(
                () -> s.chatWriter.escribirYEnviar("prueba del bot",
                        resultado -> s.state.setPruebaEstado(resultado)),
                Config.DELAY_PRUEBA_MS);
    }

    public static void programarPruebaSiguienteChat() {
        final LaylaAccessibilityService s = instance;
        if (s == null || s.state == null) return;
        s.state.setPruebaEstado("Esperando " + (Config.DELAY_PRUEBA_MS / 1000)
                + " segundos... ve a Layla y abre un chat.");
        s.handler.postDelayed(() -> {
            try {
                AccessibilityNodeInfo root = s.getRootInActiveWindow();
                if (root == null) {
                    s.state.setPruebaEstado("Falló: no hay ventana activa.");
                    return;
                }
                try {
                    String conversacion = s.chatReader.leerConversacion(root);
                    boolean debe = s.siguienteChatUseCase.debeSaltar(conversacion);
                    if (!debe) {
                        s.state.setPruebaEstado("No salta: el último mensaje no cumple la condición.");
                        return;
                    }
                    s.chatWriter.pulsarSiguienteChat(
                            resultado -> s.state.setPruebaEstado(resultado));
                } finally {
                    NodeUtils.recycle(root);
                }
            } catch (Exception e) {
                s.state.setPruebaEstado("Excepción: " + e);
            }
        }, Config.DELAY_PRUEBA_MS);
    }

    private void leerPantalla() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return;
            if (!Config.LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) return;

            state.setPantalla(chatReader.volcarPantalla(root));
            String conversacion = chatReader.leerConversacion(root);
            lecturaUseCase.procesar(conversacion);
        } catch (Exception e) {
            Log.e(TAG, "Error al leer", e);
            state.setEstado("Error al leer: " + e);
        } finally {
            NodeUtils.recycle(root);
        }
    }
}