package com.layla.bot.service;

import com.layla.bot.domain.ChatParser;
import com.layla.bot.domain.model.Mensaje;

/**
 * Decide si hay que saltar al siguiente chat.
 * Regla: si el último mensaje de la conversación no es de ELLOS, saltamos.
 */
public class SiguienteChatUseCase {

    private final ChatParser chatParser;
    private final BotState state;

    public SiguienteChatUseCase(ChatParser chatParser, BotState state) {
        this.chatParser = chatParser;
        this.state = state;
    }

    /**
     * Devuelve true si el bot debe pulsar "siguiente chat".
     * Es decir: hay conversación abierta pero el último mensaje NO es de ELLOS.
     */
    public boolean debeSaltar(String conversacionVolcado) {
        if (conversacionVolcado == null || conversacionVolcado.isEmpty()) {
            state.setEstado("No hay conversación abierta, no se puede saltar.");
            return false;
        }

        Mensaje ultimo = chatParser.ultimoEntrante(conversacionVolcado);
        if (ultimo == null) {
            state.setEstado("El último mensaje no es del otro. Saltando al siguiente...");
            return true;
        }

        // Comprobación extra: ¿el último mensaje de la lista es de ELLOS?
        java.util.List<Mensaje> todos = chatParser.parsear(conversacionVolcado);
        if (!todos.isEmpty() && !todos.get(todos.size() - 1).esEntrante()) {
            state.setEstado("El último mensaje es mío. Saltando al siguiente...");
            return true;
        }

        state.setEstado("El último mensaje es de ELLOS. No se salta.");
        return false;
    }
}