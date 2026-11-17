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
            "IMPORTANTE: Solo devuelve el texto de tu respuesta. " +
            "No incluyas el prompt, ni el historial, ni etiquetas como 'Respuesta:'.";

    /** Devuelve solo el system prompt. Se envía como systemInstruction a Gemini. */
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** Construye el mensaje del usuario (historial + último mensaje). */
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
        sb.append("Responde a ese mensaje como Layla. Solo el texto de la respuesta.");
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