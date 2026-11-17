package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
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
import com.layla.bot.data.IaRepository;
import com.layla.bot.domain.ChatParser;
import com.layla.bot.domain.PromptBuilder;
import com.layla.bot.domain.model.Mensaje;
import com.layla.bot.service.BotState;
import com.layla.bot.service.LecturaUseCase;
import com.layla.bot.service.ResponderUseCase;
import com.layla.bot.service.SiguienteChatUseCase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LaylaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LaylaBot";

    /** Aunque lleguen eventos sin parar, se lee como mínimo cada tantos milisegundos. */
    private static final long MAX_ESPERA_MS = 3000;
    /** Si una respuesta tarda más que esto, se asume que se colgó y se libera el bot. */
    private static final long MAX_RESPONDIENDO_MS = 90000;

    private static LaylaAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ChatReader chatReader;
    private ChatWriter chatWriter;
    private ChatParser chatParser;
    private BotState state;
    private LecturaUseCase lecturaUseCase;
    private SiguienteChatUseCase siguienteChatUseCase;
    private ResponderUseCase responderUseCase;

    private long primerEventoPendiente = 0;
    private int eventos = 0;
    private int lecturas = 0;

    private volatile boolean respondiendo = false;
    private long inicioRespuesta = 0;
    private String ultimaClave = null;
    private String ultimaAccion = "-";

    private final Runnable leer = () -> leerPantalla();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        state = new BotState(this);
        chatReader = new ChatReader();
        chatWriter = new ChatWriter(this, handler);
        chatParser = new ChatParser();
        lecturaUseCase = new LecturaUseCase(chatParser, state);
        siguienteChatUseCase = new SiguienteChatUseCase(new ChatParser(), state);
        responderUseCase = new ResponderUseCase(
                new IaRepository(), new PromptBuilder(), chatWriter, handler);
        eventos = 0;
        lecturas = 0;
        respondiendo = false;
        ultimaClave = null;
        ultimaAccion = "-";
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

            intentarResponder(root, conversacion);
        } catch (Exception e) {
            Log.e(TAG, "Error al leer", e);
            state.setEstado("Error al leer: " + e);
        } finally {
            NodeUtils.recycle(root);
        }
    }

    /** Si la respuesta automática está encendida y EL escribió último, pide la respuesta a la IA. */
    private void intentarResponder(AccessibilityNodeInfo root, String conversacion) {
        SharedPreferences p = getSharedPreferences("layla_prefs", Context.MODE_PRIVATE);
        if (!p.getBoolean("auto_activo", false)) return;
        if (conversacion == null || conversacion.isEmpty()) return;

        if (respondiendo) {
            // Salvavidas: si lleva demasiado tiempo, liberar para no quedarse bloqueado.
            if (SystemClock.uptimeMillis() - inicioRespuesta < MAX_RESPONDIENDO_MS) return;
            respondiendo = false;
        }

        List<Mensaje> mensajes = chatParser.parsear(conversacion);
        if (mensajes.isEmpty()) return;
        Mensaje ultimo = mensajes.get(mensajes.size() - 1);
        if (!ultimo.esEntrante()) return; // el último mensaje es tuyo: nada que responder

        // Evita responder dos veces al mismo mensaje (incluso si el envío falló).
        String clave = mensajes.size() + "|" + ultimo.getTexto();
        if (clave.equals(ultimaClave)) return;

        String cuenta = p.getString("cf_account", "");
        String token = p.getString("cf_token", "");
        String gemini = p.getString("gemini_key", "");
        boolean hayCloudflare = !cuenta.isEmpty() && !token.isEmpty();
        if (!hayCloudflare && gemini.isEmpty()) {
            ultimaAccion = "faltan las claves de la IA";
            return;
        }

        ultimaClave = clave;
        respondiendo = true;
        inicioRespuesta = SystemClock.uptimeMillis();
        ultimaAccion = "generando respuesta...";

        String nombre = leerNombre(root);
        responderUseCase.responder(cuenta, token, gemini, nombre, conversacion,
                ultimo.getTexto(), resultado -> {
                    respondiendo = false;
                    ultimaAccion = resultado;
                    diagnostico("acción del bot");
                });
    }

    /** Nombre del chico que aparece arriba del chat (vacío si no se encuentra). */
    private String leerNombre(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodos =
                root.findAccessibilityNodeInfosByViewId(Config.LAYLA_PKG + ":id/tv_nickname");
        if (nodos == null || nodos.isEmpty()) return "";
        try {
            CharSequence t = nodos.get(0).getText();
            return t != null ? t.toString().trim() : "";
        } finally {
            NodeUtils.recycleAll(nodos);
        }
    }

    /** Guarda cuántos eventos y lecturas van, qué hizo el bot y la hora, para verlo en el panel. */
    private void diagnostico(String detalle) {
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        state.setDiagnostico("eventos de Layla: " + eventos
                + " · lecturas: " + lecturas
                + " · " + detalle
                + " · bot: " + ultimaAccion
                + " · " + hora);
    }
}