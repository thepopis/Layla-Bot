package com.layla.bot.domain;

import com.layla.bot.domain.model.Mensaje;
import java.util.ArrayList;
import java.util.List;

public class ChatParser {

    /**
     * Recibe el texto de "CONVERSACIÓN VISIBLE" y devuelve la lista de mensajes.
     * Formato esperado por línea: "YO: texto" o "ELLOS: texto"
     */
    public List<Mensaje> parsear(String volcado) {
        List<Mensaje> resultado = new ArrayList<>();
        if (volcado == null || volcado.isEmpty()) return resultado;

        for (String linea : volcado.split("\n")) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            int idx = linea.indexOf(':');
            if (idx <= 0) continue;

            String prefijo = linea.substring(0, idx).trim();
            String texto = linea.substring(idx + 1).trim();
            if (texto.isEmpty()) continue;

            Mensaje.Autor autor;
            if (prefijo.equalsIgnoreCase("YO")) autor = Mensaje.Autor.YO;
            else if (prefijo.equalsIgnoreCase("ELLOS")) autor = Mensaje.Autor.ELLOS;
            else autor = Mensaje.Autor.DESCONOCIDO;

            resultado.add(new Mensaje(autor, texto));
        }
        return resultado;
    }

    /** Devuelve el último mensaje entrante, o null si no hay. */
    public Mensaje ultimoEntrante(String volcado) {
        List<Mensaje> mensajes = parsear(volcado);
        for (int i = mensajes.size() - 1; i >= 0; i--) {
            if (mensajes.get(i).esEntrante()) return mensajes.get(i);
        }
        return null;
    }
}