package com.layla.bot.service;

import com.layla.bot.domain.ChatParser;
import com.layla.bot.domain.model.Mensaje;

public class LecturaUseCase {

    private final ChatParser chatParser;
    private final BotState state;

    public LecturaUseCase(ChatParser chatParser, BotState state) {
        this.chatParser = chatParser;
        this.state = state;
    }

    /**
     * Recibe el volcado de conversación (o null si no hay chat) y actualiza el estado.
     * Devuelve true si hay un mensaje entrante que responder.
     */
    public boolean procesar(String conversacionVolcado) {
        if (conversacionVolcado == null || conversacionVolcado.isEmpty()) {
            state.setEstado("Estás en Layla, pero no veo una conversación abierta.");
            return false;
        }

        state.setConversacion(conversacionVolcado);

        Mensaje ultimo = chatParser.ultimoEntrante(conversacionVolcado);
        if (ultimo == null) {
            state.setEstado("El último mensaje no es del otro, no hay nada que responder.");
            state.setUltimoEntrante("(ninguno)");
            return false;
        }

        state.setEstado("El último mensaje visible es de: ELLOS");
        state.setUltimoEntrante(ultimo.getTexto());
        return true;
    }
}