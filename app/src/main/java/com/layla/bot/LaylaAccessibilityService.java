package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LaylaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LaylaBot";

    /** Aunque lleguen eventos sin parar, se lee como mínimo cada tantos milisegundos. */
    private static final long MAX_ESPERA_MS = 3000;

    private static LaylaAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ChatReader chatReader;
    private ChatWriter chatWriter;
    private BotState state;
    private LecturaUseCase lecturaUseCase;
    private SiguienteChatUseCase siguienteChatUseCase;

    private long primerEventoPendiente = 0;
    private int eventos = 0;
    private int lecturas = 0;

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
        eventos = 0;
        lecturas = 0;
        state.marcarServicioActivo(true);
        state.setDiagnostico("esperando eventos de Layla");
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

        eventos++;
        long ahora = SystemClock.uptimeMillis();
        if (primerEventoPendiente == 0) primerEventoPendiente = ahora;

        // Si ya llevamos mucho esperando, leer ya en vez de seguir aplazando.
        long espera = (ahora - primerEventoPendiente >= MAX_ESPERA_MS)
                ? 0
                : Config.DEBOUNCE_MS;

        handler.removeCallbacks(leer);
        handler.postDelayed(leer, espera);
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
        primerEventoPendiente = 0;
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) {
                diagnostico("sin ventana activa");
                return;
            }
            if (!Config.LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) {
                diagnostico("la ventana activa no es Layla");
                return;
            }

            lecturas++;
            state.setPantalla(chatReader.volcarPantalla(root));
            String conversacion = chatReader.leerConversacion(root);
            diagnostico(conversacion == null
                    ? "lista de chat NO encontrada"
                    : "lista de chat encontrada");
            lecturaUseCase.procesar(conversacion);
        } catch (Exception e) {
            Log.e(TAG, "Error al leer", e);
            state.setEstado("Error al leer: " + e);
        } finally {
            NodeUtils.recycle(root);
        }
    }

    /** Guarda cuántos eventos y lecturas van, y la hora, para verlo en el panel. */
    private void diagnostico(String detalle) {
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        state.setDiagnostico("eventos de Layla: " + eventos
                + " · lecturas: " + lecturas
                + " · " + detalle
                + " · " + hora);
    }
}