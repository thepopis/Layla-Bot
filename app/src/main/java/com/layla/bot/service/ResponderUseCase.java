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

    public void responder(String cfAccount, String cfToken, String geminiKey,
                          String nombreUsuario, String conversacion,
                          String ultimoMensaje, Permiso permiso, Callback cb) {
        executor.execute(() -> {
            String respuesta;
            try {
                String systemPrompt = promptBuilder.getSystemPrompt();
                String mensaje = promptBuilder.construir(nombreUsuario, conversacion, ultimoMensaje);
                respuesta = ia.generar(cfAccount, cfToken, geminiKey, systemPrompt, mensaje);
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

    private String limpiar(String respuesta) {
        if (respuesta == null) return "";
        String t = respuesta.trim();

        int bloque = t.indexOf("```");
        if (bloque >= 0) {
            String antes = t.substring(0, bloque).trim();
            if (!antes.isEmpty()) {
                t = antes;
            } else {
                String resto = t.substring(bloque + 3);
                int salto = resto.indexOf('\n');
                if (salto >= 0) resto = resto.substring(salto + 1);
                int cierre = resto.indexOf("```");
                if (cierre >= 0) resto = resto.substring(0, cierre);
                t = resto.trim();
            }
        }

        int parrafo = t.indexOf("\n\n");
        if (parrafo > 0) t = t.substring(0, parrafo).trim();

        if (t.startsWith("Layla:")) t = t.substring(6).trim();

        boolean comillasRectas = t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"");
        boolean comillasCurvas = t.length() >= 2 && t.startsWith("“") && t.endsWith("”");
        if (comillasRectas || comillasCurvas) {
            t = t.substring(1, t.length() - 1).trim();
        }

        if (t.length() > LARGO_MAXIMO) {
            t = t.substring(0, LARGO_MAXIMO);
            int espacio = t.lastIndexOf(' ');
            if (espacio > LARGO_MAXIMO / 2) t = t.substring(0, espacio);
            t = t.trim();
        }
        return t;
    }
}