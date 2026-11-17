package com.layla.bot.service;

import android.os.Handler;

import com.layla.bot.accessibility.ChatWriter;
import com.layla.bot.data.IaRepository;
import com.layla.bot.domain.PromptBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResponderUseCase {

    public interface Callback {
        void onResultado(String mensaje);
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
                          String ultimoMensaje, Callback cb) {
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

            mainHandler.post(() -> chatWriter.escribirYEnviar(texto,
                    resultado -> cb.onResultado("Respondió " + proveedor + ": " + resultado)));
        });
    }

    /** Quita comillas y prefijos que a veces añade el modelo. */
    private String limpiar(String respuesta) {
        if (respuesta == null) return "";
        String t = respuesta.trim();
        if (t.startsWith("Layla:")) t = t.substring(6).trim();
        boolean comillasRectas = t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"");
        boolean comillasCurvas = t.length() >= 2 && t.startsWith("“") && t.endsWith("”");
        if (comillasRectas || comillasCurvas) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}