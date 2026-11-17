package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;

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
    /** Mantener pulsada la burbuja este tiempo abre el panel. */
    private static final long PULSACION_LARGA_MS = 700;

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

    private long primerEventoPendiente = 0;
    private int eventos = 0;
    private int lecturas = 0;

    private volatile boolean respondiendo = false;
    private long inicioRespuesta = 0;
    private String ultimaClave = null;
    private String ultimaAccion = "-";

    // Burbuja flotante
    private WindowManager windowManager;
    private TextView burbuja;
    private WindowManager.LayoutParams burbujaParams;

    /** Si cambia el interruptor (desde la burbuja o desde la app), actualiza la burbuja. */
    private final SharedPreferences.OnSharedPreferenceChangeListener alCambiarPrefs =
            (sp, clave) -> {
                if ("auto_activo".equals(clave)) {
                    // Al encender, puede responder lo que haya pendiente en el chat abierto.
                    if (sp.getBoolean("auto_activo", false)) ultimaClave = null;
                    actualizarBurbuja();
                }
            };

    private final Runnable leer = () -> leerPantalla();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = getSharedPreferences("layla_prefs", Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(alCambiarPrefs);

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

        crearBurbuja();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        quitarBurbuja();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        quitarBurbuja();
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

    /** Si el bot está encendido y EL escribió último, pide la respuesta a la IA. */
    private void intentarResponder(AccessibilityNodeInfo root, String conversacion) {
        if (!prefs.getBoolean("auto_activo", false)) return;
        if (conversacion == null || conversacion.isEmpty()) return;

        if (respondiendo) {
            // Salvavidas: si lleva demasiado tiempo, liberar para no quedarse bloqueado.
            if (SystemClock.uptimeMillis() - inicioRespuesta < MAX_RESPONDIENDO_MS) return;
            respondiendo = false;
            actualizarBurbuja();
        }

        List<Mensaje> mensajes = chatParser.parsear(conversacion);
        if (mensajes.isEmpty()) return;
        Mensaje ultimo = mensajes.get(mensajes.size() - 1);
        if (!ultimo.esEntrante()) return; // el último mensaje es tuyo: nada que responder

        // Evita responder dos veces al mismo mensaje (incluso si el envío falló).
        String clave = mensajes.size() + "|" + ultimo.getTexto();
        if (clave.equals(ultimaClave)) return;

        String cuenta = prefs.getString("cf_account", "");
        String token = prefs.getString("cf_token", "");
        String gemini = prefs.getString("gemini_key", "");
        boolean hayCloudflare = !cuenta.isEmpty() && !token.isEmpty();
        if (!hayCloudflare && gemini.isEmpty()) {
            ultimaAccion = "faltan las claves de la IA";
            return;
        }

        ultimaClave = clave;
        respondiendo = true;
        inicioRespuesta = SystemClock.uptimeMillis();
        ultimaAccion = "generando respuesta...";
        actualizarBurbuja();

        String nombre = leerNombre(root);
        responderUseCase.responder(cuenta, token, gemini, nombre, conversacion,
                ultimo.getTexto(),
                () -> prefs.getBoolean("auto_activo", false),
                resultado -> {
                    respondiendo = false;
                    ultimaAccion = resultado;
                    actualizarBurbuja();
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

    // ------------------------------------------------------------------
    // Burbuja flotante
    // ------------------------------------------------------------------

    private void crearBurbuja() {
        if (burbuja != null) return;
        try {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            int tam = dp(54);

            burbuja = new TextView(this);
            burbuja.setGravity(Gravity.CENTER);
            burbuja.setTextColor(Color.WHITE);
            burbuja.setTextSize(12);
            burbuja.setTypeface(Typeface.DEFAULT_BOLD);
            burbuja.setContentDescription("Botón del bot de Layla");

            burbujaParams = new WindowManager.LayoutParams(
                    tam, tam,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            burbujaParams.gravity = Gravity.TOP | Gravity.START;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            burbujaParams.x = prefs.getInt("bubble_x", dm.widthPixels - tam - dp(8));
            burbujaParams.y = prefs.getInt("bubble_y", dm.heightPixels / 3);

            burbuja.setOnTouchListener(new View.OnTouchListener() {
                private int inicioX;
                private int inicioY;
                private float toqueX;
                private float toqueY;
                private boolean arrastrando;
                private long inicioToque;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            inicioX = burbujaParams.x;
                            inicioY = burbujaParams.y;
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
                                burbujaParams.x = inicioX + (int) dx;
                                burbujaParams.y = inicioY + (int) dy;
                                windowManager.updateViewLayout(v, burbujaParams);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (arrastrando) {
                                prefs.edit()
                                        .putInt("bubble_x", burbujaParams.x)
                                        .putInt("bubble_y", burbujaParams.y)
                                        .apply();
                            } else if (SystemClock.uptimeMillis() - inicioToque >= PULSACION_LARGA_MS) {
                                abrirPanel();
                            } else {
                                alternarBot();
                            }
                            return true;

                        default:
                            return false;
                    }
                }
            });

            windowManager.addView(burbuja, burbujaParams);
            actualizarBurbuja();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo crear la burbuja", e);
            burbuja = null;
        }
    }

    private void quitarBurbuja() {
        if (burbuja != null && windowManager != null) {
            try {
                windowManager.removeView(burbuja);
            } catch (Exception e) {
                Log.w(TAG, "No se pudo quitar la burbuja", e);
            }
        }
        burbuja = null;
    }

    /** Verde ON = respondiendo solo · ámbar ··· = generando · gris OFF = en pausa. */
    private void actualizarBurbuja() {
        if (burbuja == null) return;
        boolean activo = prefs.getBoolean("auto_activo", false);
        int color;
        String texto;
        if (!activo) {
            color = 0xFF6B7785;
            texto = "OFF";
        } else if (respondiendo) {
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

    private void alternarBot() {
        boolean nuevo = !prefs.getBoolean("auto_activo", false);
        prefs.edit().putBoolean("auto_activo", nuevo).apply();
        Toast.makeText(this, nuevo ? "Bot ACTIVADO" : "Bot en pausa", Toast.LENGTH_SHORT).show();
        actualizarBurbuja();
    }

    private void abrirPanel() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo abrir el panel", e);
            Toast.makeText(this, "No pude abrir el panel. Ábrelo desde el icono de la app.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }
}