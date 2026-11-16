package com.layla.bot.service;

import com.layla.bot.accessibility.ChatReader;
import com.layla.bot.accessibility.ChatWriter;
import com.layla.bot.data.GeminiRepository;
import com.layla.bot.domain.PromptBuilder;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResponderUseCase {

    public interface Callback {
        void onResultado(String mensaje);
    }

    private final GeminiRepository geminiRepository;
    private final PromptBuilder promptBuilder;
    private final ChatReader chatReader;
    private final ChatWriter chatWriter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ResponderUseCase(GeminiRepository geminiRepository,
                            PromptBuilder promptBuilder,
                            ChatReader chatReader,
                            ChatWriter chatWriter) {
        this.geminiRepository = geminiRepository;
        this.promptBuilder = promptBuilder;
        this.chatReader = chatReader;
        this.chatWriter = chatWriter;
    }

    public void responder(AccessibilityNodeInfo root, String apiKey,
                          String nombreUsuario, String conversacion,
                          String ultimoMensaje, Callback cb) {
        executor.execute(() -> {
            try {
                String prompt = promptBuilder.construir(nombreUsuario, conversacion, ultimoMensaje);
                String respuesta = geminiRepository.generarRespuesta(apiKey, prompt);
                if (respuesta == null || respuesta.trim().isEmpty()) {
                    cb.onResultado("Gemini devolvió vacío.");
                    return;
                }
                chatWriter.escribirYEnviar(respuesta.trim(),
                        resultado -> cb.onResultado("Respuesta enviada: " + resultado));
            } catch (Exception e) {
                cb.onResultado("Error IA: " + e.getMessage());
            }
        });
    }
}