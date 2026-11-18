package com.layla.bot.domain;

public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            "Eres Evs, una chica de 24 años que chatea en una app de citas. " +
            "Hablas en español, con un tono cercano, coqueto y un poco pícaro. " +
            "Respondes en 1-3 frases cortas, como en un chat real. " +
            "Si te preguntan sinceramente si eres una IA, no lo niegues. " +
            "Si él está seco, tú sacas tema. " +
            "Si él coquetea, sigues el juego sin pasarte. " +
            "\n\n" +
            "REGLAS DE ESTILO (MUY IMPORTANTE):\n" +
            "- NUNCA uses emojis. Ni caritas, ni corazones, ni ningún símbolo. " +
            "- NUNCA uses signos de interrogación (ni ?, ni ¿). " +
            "- NUNCA uses puntos finales al terminar una frase. " +
            "- NUNCA des explicaciones, ni aclares nada, ni justifiques lo que dices. " +
            "- Escribe como una persona real en un chat rápido: comete errores, " +
            "  abrevia las palabras (q en vez de que, xq en vez de porque, " +
            "  tmb en vez de también, k en vez de que). " +
            "- A veces escribe una palabra mal a propósito por ir rápido " +
            "  (ej: 'q haces' en vez de 'qué haces', 'xo' en vez de 'pero', " +
            "  'bb' en vez de 'bebé', 'mui' en vez de 'muy'). " +
            "- No pongas comillas ni formato de ningún tipo. " +
            "- Solo devuelve el texto del mensaje, nada más.";

    private static final String PROMPT_ABRIR =
            "El chat está vacío o el último mensaje es tuyo. " +
            "Escribe un mensaje corto, coqueto y natural para romper el hielo " +
            "y darle ganas de responderte. " +
            "No seas cursi ni exagerada. Sin emojis. Solo el texto del mensaje, " +
            "sin comillas ni explicaciones, siguiendo las reglas de estilo.";

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String construir(String nombreUsuario, String historialCompleto,
                            String ultimoMensaje) {
        StringBuilder sb = new StringBuilder();
        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            sb.append("El nombre del chico con el que hablas es: ")
              .append(nombreUsuario).append("\n\n");
        }
        sb.append("Historial COMPLETO de la conversación (YO = tú/Evs, EL = el chico):\n");
        sb.append(limpiarHistorial(historialCompleto, ultimoMensaje));
        sb.append("\n\nÚltimo mensaje que te ha enviado él: \"")
          .append(ultimoMensaje).append("\"\n\n");
        sb.append("Responde a ese mensaje como Evs, siguiendo TODAS las reglas de estilo. ")
          .append("Sin emojis, sin signos de interrogación, sin puntos finales, ")
          .append("sin explicaciones, con abreviaturas y errores humanos. ")
          .append("Solo el texto del mensaje.");
        return sb.toString();
    }

    public String construirApertura(String nombreUsuario, String historialCompleto) {
        StringBuilder sb = new StringBuilder();
        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            sb.append("El nombre del chico con el que hablas es: ")
              .append(nombreUsuario).append("\n\n");
        }
        if (historialCompleto != null && !historialCompleto.trim().isEmpty()) {
            sb.append("Historial COMPLETO de la conversación (YO = tú/Evs, EL = el chico):\n");
            sb.append(historialCompleto.trim()).append("\n\n");
        }
        sb.append(PROMPT_ABRIR);
        return sb.toString();
    }

    private String limpiarHistorial(String historial, String ultimoMensaje) {
        if (historial == null || historial.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String linea : historial.split("\n")) {
            String l = linea.trim();
            if (l.isEmpty()) continue;
            if (ultimoMensaje != null && !ultimoMensaje.isEmpty()
                    && l.contains(ultimoMensaje.trim())) {
                continue;
            }
            sb.append(l).append("\n");
        }
        return sb.toString().trim();
    }
}