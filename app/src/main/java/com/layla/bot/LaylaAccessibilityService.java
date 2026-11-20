package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
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
import com.layla.bot.service.AutoResponderUseCase;
import com.layla.bot.service.BotState;
import com.layla.bot.service.DatasetRecorder;
import com.layla.bot.service.LecturaUseCase;
import com.layla.bot.service.ResponderUseCase;
import com.layla.bot.service.SiguienteChatUseCase;
import com.layla.bot.ui.BubbleOverlay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LaylaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LaylaBot";
    private static final long MAX_ESPERA_MS = 3000;

    private static LaylaAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ChatReader chatReader;
    private ChatWriter chatWriter;
    private ChatParser chatParser;
    private BotState state;
    private LecturaUseCase lecturaUseCase;
    private SiguienteChatUseCase siguienteChatUseCase;
    private ResponderUseCase responderUseCase;
    private DatasetRecorder datasetRecorder;
    private AutoResponderUseCase autoResponderUseCase;
    private IaRepository iaRepository;
    private BubbleOverlay bubbleOverlay;

    private long primerEventoPendiente = 0;
    private int eventos = 0;
    private int lecturas = 0;
    private String ultimaAccion = "-";

    private final SharedPreferences.OnSharedPreferenceChangeListener alCambiarPrefs =
            (sp, clave) -> {
                if ("auto_activo".equals(clave)) {
                    if (sp.getBoolean("auto_activo", false) && autoResponderUseCase != null) {
                        autoResponderUseCase.reset();
                    }
                    actualizarBurbuja();
                }
            };

    private final Runnable leer = () -> leerPantalla();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            instance = this;
            prefs = getSharedPreferences("layla_prefs", Context.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(alCambiarPrefs);

            state = new BotState(this);
            chatReader = new ChatReader();
            chatWriter = new ChatWriter(this, handler);
            chatParser = new ChatParser();
            lecturaUseCase = new LecturaUseCase(chatParser, state);
            siguienteChatUseCase = new SiguienteChatUseCase(new ChatParser(), state);
            iaRepository = new IaRepository();
            responderUseCase = new ResponderUseCase(
                    iaRepository, new PromptBuilder(), chatWriter, handler);
            datasetRecorder = new DatasetRecorder(this);
            autoResponderUseCase = new AutoResponderUseCase(
                    chatReader, chatWriter, chatParser, responderUseCase,
                    datasetRecorder, iaRepository);

            bubbleOverlay = new BubbleOverlay(this, prefs, activo -> {
                if (activo && autoResponderUseCase != null) autoResponderUseCase.reset();
                actualizarBurbuja();
            });

            eventos = 0;
            lecturas = 0;
            ultimaAccion = "-";
            state.marcarServicioActivo(true);
            state.setDiagnostico("esperando eventos de Layla");

            // Pequeña espera para que WindowManager esté listo antes de añadir la vista
            handler.postDelayed(() -> {
                if (bubbleOverlay != null) bubbleOverlay.crear();
            }, 500);

            Log.d(TAG, "onServiceConnected completado");
        } catch (Exception e) {
            Log.e(TAG, "Error fatal en onServiceConnected", e);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (bubbleOverlay != null) bubbleOverlay.quitar();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubbleOverlay != null) bubbleOverlay.quitar();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(alCambiarPrefs);
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

        long espera = (ahora - primerEventoPendiente >= MAX_ESPERA_MS)
                ? 0
                : Config.DEBOUNCE_MS;

        handler.removeCallbacks(leer);
        handler.postDelayed(leer, espera);
    }

    @Override
    public void onInterrupt() {}

    // ---------- API estática para MainActivity ----------

    public static String informe() {
        if (instance == null || instance.state == null) return "Servicio no conectado aún.";
        return instance.state.informe();
    }

    public static void programarPruebaEscritura() {
        final LaylaAccessibilityService s = instance;
        if (s == null || s.state == null) {
            Log.w(TAG, "programarPruebaEscritura: servicio no activo");
            return;
        }
        s.state.setPruebaEstado("Esperando " + (Config.DELAY_PRUEBA_MS / 1000)
                + " segundos... ve a Layla y abre un chat.");
        s.handler.postDelayed(
                () -> s.chatWriter.escribirYEnviar("prueba del bot",
                        resultado -> s.state.setPruebaEstado(resultado)),
                Config.DELAY_PRUEBA_MS);
    }

    public static void programarPruebaSiguienteChat() {
        final LaylaAccessibilityService s = instance;
        if (s == null || s.state == null) {
            Log.w(TAG, "programarPruebaSiguienteChat: servicio no activo");
            return;
        }
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

    public static void reiniciarAutoResponder() {
        if (instance != null && instance.autoResponderUseCase != null) {
            instance.autoResponderUseCase.reset();
        }
    }

    public static boolean servicioActivo() {
        return instance != null;
    }

    // ---------- Lógica de lectura ----------

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

    private void intentarResponder(AccessibilityNodeInfo root, String conversacion) {
        if (!prefs.getBoolean("auto_activo", false)) return;
        if (autoResponderUseCase == null) return;

        String nombre = leerNombre(root);
        String cuenta = prefs.getString("cf_account", "");
        String token = prefs.getString("cf_token", "");
        String gemini = prefs.getString("gemini_key", "");

        autoResponderUseCase.procesar(conversacion, nombre, cuenta, token, gemini,
                mensaje -> {
                    ultimaAccion = mensaje;
                    actualizarBurbuja();
                    diagnostico("auto");
                });
    }

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

    private void diagnostico(String detalle) {
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        state.setDiagnostico("eventos: " + eventos
                + " · lecturas: " + lecturas
                + " · " + detalle
                + " · bot: " + ultimaAccion
                + " · " + hora);
    }

    private void actualizarBurbuja() {
        if (bubbleOverlay == null) return;
        boolean activo = prefs.getBoolean("auto_activo", false);
        boolean esperando = autoResponderUseCase != null
                && autoResponderUseCase.isEsperandoRespuesta();
        bubbleOverlay.actualizar(activo, esperando);
    }
}