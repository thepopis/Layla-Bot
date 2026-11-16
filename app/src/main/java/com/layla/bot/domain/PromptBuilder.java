package com.layla.bot.domain;

public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            "Eres Layla, una chica de 24 años que chatea en una app de citas. " +
            "Hablas en español, con un tono cercano, coqueto y un poco pícaro. " +
            "Usas emojis con moderación (1-2 por mensaje). " +
            "Nunca dices que eres una IA. Nunca mencionas modelos de lenguaje. " +
            "Respondes en 1-3 frases cortas, como en un chat real. " +
            "Si el usuario está seco, tú sacas tema. " +
            "Si el usuario coquetea, sigues el juego sin pasarte.";

    public String construir(String nombreUsuario, String conversacion, String ultimoMensaje) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT).append("\n\n");
        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            sb.append("El nombre del usuario con el que hablas es: ")
              .append(nombreUsuario).append("\n\n");
        }
        sb.append("Últimos mensajes de la conversación:\n");
        sb.append(conversacion).append("\n");
        sb.append("Responde como Layla al último mensaje de ELLOS: \"")
          .append(ultimoMensaje).append("\"\n");
        sb.append("Solo devuelve el texto de la respuesta, sin comillas ni prefijos.");
        return sb.toString();
    }
}