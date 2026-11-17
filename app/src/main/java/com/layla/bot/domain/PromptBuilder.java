package com.layla.bot.domain;

public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            "Eres Layla, una chica de 24 años que chatea en una app de citas. " +
            "Hablas en español, con un tono cercano, coqueto y un poco pícaro. " +
            "Usas emojis con moderación (1-2 por mensaje). " +
            "Respondes en 1-3 frases cortas, como en un chat real. " +
            "Si te preguntan sinceramente si eres una IA, no lo niegues. " +
            "Si él está seco, tú sacas tema. " +
            "Si él coquetea, sigues el juego sin pasarte. " +
            "\n\n" +
            "REGLAS DE ESTILO (MUY IMPORTANTE):\n" +
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

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String construir(String nombreUsuario, String conversacion, String ultimoMensaje) {
        StringBuilder sb = new StringBuilder();
        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            sb.append("El nombre del chico con el que hablas es: ")
              .append(nombreUsuario).append("\n\n");
        }
        sb.append("Historial reciente:\n");
        sb.append(limpiarHistorial(conversacion, ultimoMensaje));
        sb.append("\nÚltimo mensaje que te ha enviado él: \"")
          .append(ultimoMensaje).append("\"\n\n");
        sb.append("Responde a ese mensaje como Layla, siguiendo TODAS las reglas de estilo. ")
          .append("Sin signos de interrogación, sin puntos finales, sin explicaciones, ")
          .append("con abreviaturas y errores humanos. Solo el texto del mensaje.");
        return sb.toString();
    }

    private String limpiarHistorial(String conversacion, String ultimoMensaje) {
        if (conversacion == null || conversacion.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String linea : conversacion.split("\n")) {
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