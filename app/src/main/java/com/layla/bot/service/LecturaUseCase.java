package com.layla.bot.service;

import com.layla.bot.domain.ChatParser;
import com.layla.bot.domain.model.Mensaje;

import java.util.List;

public class LecturaUseCase {

    private final ChatParser chatParser;
    private final BotState state;

    public LecturaUseCase(ChatParser chatParser, BotState state) {
        this.chatParser = chatParser;
        this.state = state;
    }

    /**
     * Recibe el volcado de conversación (o null si no hay chat) y actualiza el estado.
     * Devuelve true si el ÚLTIMO mensaje de la conversación es de EL (hay algo que responder).
     */
    public boolean procesar(String conversacionVolcado) {
        if (conversacionVolcado == null || conversacionVolcado.isEmpty()) {
            state.setEstado("Estás en Layla, pero no veo una conversación abierta.");
            return false;
        }

        state.setConversacion(conversacionVolcado);

        List<Mensaje> mensajes = chatParser.parsear(conversacionVolcado);
        if (mensajes.isEmpty()) {
            state.setEstado("Hay una conversación, pero no pude leer ningún mensaje.");
            state.setUltimoEntrante("(ninguno)");
            return false;
        }

        Mensaje ultimo = mensajes.get(mensajes.size() - 1);
        if (!ultimo.esEntrante()) {
            Mensaje ultimoEntrante = chatParser.ultimoEntrante(conversacionVolcado);
            state.setEstado("El último mensaje no es de EL (es tuyo o no se pudo identificar). "
                    + "No hay nada que responder.");
            state.setUltimoEntrante(
                    ultimoEntrante != null ? ultimoEntrante.getTexto() : "(ninguno)");
            return false;
        }

        state.setEstado("El último mensaje es de EL: hay algo que responder.");
        state.setUltimoEntrante(ultimo.getTexto());
        return true;
    }
}