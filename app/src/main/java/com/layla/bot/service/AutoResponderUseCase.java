package com.layla.bot.service;

import com.layla.bot.accessibility.ChatReader;
import com.layla.bot.accessibility.ChatWriter;
import com.layla.bot.data.IaRepository;
import com.layla.bot.domain.ChatParser;
import com.layla.bot.domain.model.Mensaje;

import java.util.List;

public class AutoResponderUseCase {

    public interface Callback {
        void onEstado(String mensaje);
    }

    /** Tiempo mínimo entre dos intentos de apertura en el mismo chat. */
    private static final long INTERVALO_APERTURA_MS = 60000;

    private final ChatReader chatReader;
    private final ChatWriter chatWriter;
    private final ChatParser chatParser;
    private final ResponderUseCase responderUseCase;
    private final DatasetRecorder datasetRecorder;
    private final IaRepository iaRepository;

    private volatile boolean esperandoRespuesta = false;
    private volatile long inicioEspera = 0;
    private volatile String ultimaClave = null;
    private volatile String ultimoMensajeRespondido = null;

    private volatile String chatActual = null;
    private volatile long ultimaAperturaMs = 0;
    private volatile boolean aperturaEnviadaParaChatActual = false;

    public AutoResponderUseCase(ChatReader chatReader,
                                ChatWriter chatWriter,
                                ChatParser chatParser,
                                ResponderUseCase responderUseCase,
                                DatasetRecorder datasetRecorder,
                                IaRepository iaRepository) {
        this.chatReader = chatReader;
        this.chatWriter = chatWriter;
        this.chatParser = chatParser;
        this.responderUseCase = responderUseCase;
        this.datasetRecorder = datasetRecorder;
        this.iaRepository = iaRepository;
    }

    public void procesar(String conversacion, String nombreUsuario,
                         String cfAccount, String cfToken, String geminiKey,
                         Callback cb) {
        if (conversacion == null) conversacion = "";

        String chatId = nombreUsuario != null ? nombreUsuario : "";
        if (!chatId.equals(chatActual)) {
            chatActual = chatId;
            aperturaEnviadaParaChatActual = false;
            ultimaAperturaMs = 0;
            ultimaClave = null;
            ultimoMensajeRespondido = null;
            esperandoRespuesta = false;
            cb.onEstado("Nuevo chat detectado: " + chatId);
        }

        List<Mensaje> mensajes = chatParser.parsear(conversacion);

        // ---- 1. Estamos esperando respuesta ----
        if (esperandoRespuesta) {
            Mensaje ultimo = mensajes.isEmpty() ? null : mensajes.get(mensajes.size() - 1);
            boolean respondio = ultimo != null && ultimo.esEntrante()
                    && !ultimo.getTexto().equals(ultimoMensajeRespondido);

            if (respondio) {
                esperandoRespuesta = false;
                cb.onEstado("El usuario respondió. Procesando...");
            } else if (System.currentTimeMillis() - inicioEspera > 15000) {
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

        // ---- 2. Sin mensajes o último es mío → apertura proactiva ----
        Mensaje ultimo = mensajes.isEmpty() ? null : mensajes.get(mensajes.size() - 1);
        if (ultimo == null || !ultimo.esEntrante()) {
            if (aperturaEnviadaParaChatActual) {
                cb.onEstado("Apertura ya enviada en este chat. Esperando al usuario...");
                return;
            }
            if (System.currentTimeMillis() - ultimaAperturaMs < INTERVALO_APERTURA_MS) {
                cb.onEstado("Esperando antes de otra apertura...");
                return;
            }

            aperturaEnviadaParaChatActual = true;
            ultimaAperturaMs = System.currentTimeMillis();
            cb.onEstado("Generando mensaje de apertura...");

            responderUseCase.abrirConversacion(cfAccount, cfToken, geminiKey,
                    nombreUsuario, conversacion,
                    () -> true,
                    resultado -> {
                        cb.onEstado("Apertura: " + resultado);
                        String proveedor = iaRepository.getUltimoProveedor();
                        datasetRecorder.guardar("(apertura)", resultado, nombreUsuario, proveedor);
                        esperandoRespuesta = true;
                        inicioEspera = System.currentTimeMillis();
                    });
            return;
        }

        // ---- 3. Último mensaje es de ELLOS → responder ----
        String clave = mensajes.size() + "|" + ultimo.getTexto();
        if (clave.equals(ultimaClave)) {
            cb.onEstado("Ya respondí a este mensaje.");
            return;
        }

        ultimaClave = clave;
        ultimoMensajeRespondido = ultimo.getTexto();
        cb.onEstado("Generando respuesta para: " + ultimo.getTexto());

        responderUseCase.responder(cfAccount, cfToken, geminiKey, nombreUsuario,
                conversacion, ultimo.getTexto(),
                () -> true,
                resultado -> {
                    cb.onEstado("IA respondió: " + resultado);
                    String proveedor = iaRepository.getUltimoProveedor();
                    datasetRecorder.guardar(ultimo.getTexto(), resultado, nombreUsuario, proveedor);
                    esperandoRespuesta = true;
                    inicioEspera = System.currentTimeMillis();
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
        chatActual = null;
        ultimaAperturaMs = 0;
        aperturaEnviadaParaChatActual = false;
    }
}