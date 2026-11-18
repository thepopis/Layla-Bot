package com.layla.bot.service;

import android.os.Handler;
import android.util.Log;

import com.layla.bot.accessibility.ChatReader;
import com.layla.bot.accessibility.ChatWriter;
import com.layla.bot.data.IaRepository;
import com.layla.bot.domain.ChatParser;
import com.layla.bot.domain.PromptBuilder;
import com.layla.bot.domain.model.Mensaje;

import java.util.List;

/**
 * Orquesta el flujo completo:
 * 1. Lee la conversación.
 * 2. Si el último mensaje es de ELLOS, pide respuesta a la IA.
 * 3. Envía la respuesta y guarda el par en el dataset.
 * 4. Espera N segundos a ver si responden.
 * 5. Si no responden, salta al siguiente chat.
 */
public class AutoResponderUseCase {

    private static final String TAG = "AutoResponder";

    public interface Callback {
        void onEstado(String mensaje);
    }

    private final ChatReader chatReader;
    private final ChatWriter chatWriter;
    private final ChatParser chatParser;
    private final ResponderUseCase responderUseCase;
    private final DatasetRecorder datasetRecorder;
    private final Handler handler;
    private final IaRepository iaRepository;

    private volatile boolean esperandoRespuesta = false;
    private volatile long inicioEspera = 0;
    private volatile String ultimaClave = null;
    private volatile String ultimoMensajeRespondido = null;

    public AutoResponderUseCase(ChatReader chatReader,
                                ChatWriter chatWriter,
                                ChatParser chatParser,
                                ResponderUseCase responderUseCase,
                                DatasetRecorder datasetRecorder,
                                Handler handler,
                                IaRepository iaRepository) {
        this.chatReader = chatReader;
        this.chatWriter = chatWriter;
        this.chatParser = chatParser;
        this.responderUseCase = responderUseCase;
        this.datasetRecorder = datasetRecorder;
        this.handler = handler;
        this.iaRepository = iaRepository;
    }

    /**
     * Punto de entrada: se llama cada vez que se lee la pantalla.
     * Recibe la conversación ya leída y el nombre del usuario.
     */
    public void procesar(String conversacion, String nombreUsuario,
                         String cfAccount, String cfToken, String geminiKey,
                         Callback cb) {
        if (conversacion == null || conversacion.isEmpty()) {
            cb.onEstado("No hay conversación abierta.");
            return;
        }

        List<Mensaje> mensajes = chatParser.parsear(conversacion);
        if (mensajes.isEmpty()) {
            cb.onEstado("Conversación vacía.");
            return;
        }

        Mensaje ultimo = mensajes.get(mensajes.size() - 1);

        // Si estamos esperando respuesta y el usuario ya respondió
        if (esperandoRespuesta) {
            if (ultimo.esEntrante() && !ultimo.getTexto().equals(ultimoMensajeRespondido)) {
                // ¡Respondió! Cancelar el salto y procesar nuevo mensaje
                esperandoRespuesta = false;
                cb.onEstado("El usuario respondió. Procesando...");
                // Caer al flujo normal
            } else if (System.currentTimeMillis() - inicioEspera > 15000) {
                // No respondió en 15s → saltar
                esperandoRespuesta = false;
                cb.onEstado("Sin respuesta en 15s. Saltando al siguiente chat...");
                chatWriter.pulsarSiguienteChat(resultado ->
                        cb.onEstado("Salto: " + resultado));
                return;
            } else {
                long restante = 15000 - (System.currentTimeMillis() - inicioEspera);
                cb.onEstado("Esperando respuesta... " + (restante / 1000) + "s");
                return;
            }
        }

        // Si el último mensaje no es de ELLOS, no hay nada que responder
        if (!ultimo.esEntrante()) {
            cb.onEstado("El último mensaje es mío. Esperando al otro...");
            return;
        }

        // Evitar responder dos veces al mismo mensaje
        String clave = mensajes.size() + "|" + ultimo.getTexto();
        if (clave.equals(ultimaClave)) {
            cb.onEstado("Ya respondí a este mensaje.");
            return;
        }

        // Hay mensaje nuevo: responder
        ultimaClave = clave;
        ultimoMensajeRespondido = ultimo.getTexto();
        cb.onEstado("Generando respuesta para: " + ultimo.getTexto());

        responderUseCase.responder(cfAccount, cfToken, geminiKey, nombreUsuario,
                conversacion, ultimo.getTexto(),
                () -> true,
                resultado -> {
                    cb.onEstado("IA respondió: " + resultado);
                    // Guardar en dataset
                    String proveedor = iaRepository.getUltimoProveedor();
                    // El resultado es algo como "Respondió Gemini: click=..., enviado=true"
                    // No tenemos el texto exacto aquí, así que guardamos lo que podemos
                    datasetRecorder.guardar(ultimo.getTexto(), resultado, nombreUsuario, proveedor);

                    // Empezar a esperar respuesta
                    esperandoRespuesta = true;
                    inicioEspera = System.currentTimeMillis();
                    cb.onEstado("Respuesta enviada. Esperando 15s...");
                });
    }

    public boolean isEsperandoRespuesta() {
        return esperandoRespuesta;
    }

    public void reset() {
        esperandoRespuesta = false;
        inicioEspera = 0;
        ultimaClave = null;
        ultimoMensajeRespondido = null;
    }
}