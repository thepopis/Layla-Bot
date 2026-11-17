package com.layla.bot.service;

import android.os.Handler;

import com.layla.bot.accessibility.ChatWriter;
import com.layla.bot.data.IaRepository;
import com.layla.bot.domain.PromptBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResponderUseCase {

    private static final int LARGO_MAXIMO = 300;

    public interface Callback {
        void onResultado(String mensaje);
    }

    /** Se consulta justo antes de enviar: si devuelve false (bot en pausa), no se envía. */
    public interface Permiso {
        boolean puedeEnviar();
    }

    private final IaRepository ia;
    private final PromptBuilder promptBuilder;
    private final ChatWriter chatWriter;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ResponderUseCase(IaRepository ia,
                            PromptBuilder promptBuilder,
                            ChatWriter chatWriter,
                            Handler mainHandler) {
        this.ia = ia;
        this.promptBuilder = promptBuilder;
        this.chatWriter = chatWriter;
        this.mainHandler = mainHandler;
    }

    /**
     * Pide la respuesta a la IA en un hilo aparte y luego la escribe y la envía
     * en el hilo principal (donde deben ejecutarse las acciones de accesibilidad).
     */
    public void responder(String cfAccount, String cfToken, String geminiKey,
                          String nombreUsuario, String conversacion,
                          String ultimoMensaje, Permiso permiso, Callback cb) {
        executor.execute(() -> {
            String respuesta;
            try {
                String prompt = promptBuilder.construir(nombreUsuario, conversacion, ultimoMensaje);
                respuesta = ia.generar(cfAccount, cfToken, geminiKey, prompt);
            } catch (Exception e) {
                final String error = "Error IA: " + e.getMessage();
                mainHandler.post(() -> cb.onResultado(error));
                return;
            }

            final String texto = limpiar(respuesta);
            final String proveedor = ia.getUltimoProveedor();
            if (texto.isEmpty()) {
                mainHandler.post(() -> cb.onResultado("La IA devolvió una respuesta vacía."));
                return;
            }

            mainHandler.post(() -> {
                if (!permiso.puedeEnviar()) {
                    cb.onResultado("En pausa: no se envió la respuesta.");
                    return;
                }
                chatWriter.escribirYEnviar(texto,
                        resultado -> cb.onResultado("Respondió " + proveedor + ": " + resultado));
            });
        });
    }

    /** Deja solo el texto del mensaje: sin bloques de código, comillas ni texto de más. */
    private String limpiar(String respuesta) {
        if (respuesta == null) return "";
        String t = respuesta.trim();

        // Si el modelo metió bloques de código, quedarse con el contenido útil.
        int bloque = t.indexOf("```");
        if (bloque >= 0) {
            String antes = t.substring(0, bloque).trim();
            if (!antes.isEmpty()) {
                t = antes;
            } else {
                String resto = t.substring(bloque + 3);
                int salto = resto.indexOf('\n');
                if (salto >= 0) resto = resto.substring(salto + 1); // quita la palabra "markdown"
                int cierre = resto.indexOf("```");
                if (cierre >= 0) resto = resto.substring(0, cierre);
                t = resto.trim();
            }
        }

        // Solo el primer párrafo: a veces el modelo sigue escribiendo de más.
        int parrafo = t.indexOf("\n\n");
        if (parrafo > 0) t = t.substring(0, parrafo).trim();

        if (t.startsWith("Layla:")) t = t.substring(6).trim();

        boolean comillasRectas = t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"");
        boolean comillasCurvas = t.length() >= 2 && t.startsWith("“") && t.endsWith("”");
        if (comillasRectas || comillasCurvas) {
            t = t.substring(1, t.length() - 1).trim();
        }

        // Límite de largo, cortando en un espacio para no partir palabras.
        if (t.length() > LARGO_MAXIMO) {
            t = t.substring(0, LARGO_MAXIMO);
            int espacio = t.lastIndexOf(' ');
            if (espacio > LARGO_MAXIMO / 2) t = t.substring(0, espacio);
            t = t.trim();
        }
        return t;
    }
}