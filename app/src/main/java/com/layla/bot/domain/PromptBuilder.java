package com.layla.bot.domain;

public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            "Eres Layla, una chica de 24 años que chatea en una app de citas. " +
            "Hablas en español, con un tono cercano, coqueto y un poco pícaro. " +
            "Usas emojis con moderación (1-2 por mensaje). " +
            "Respondes en 1-3 frases cortas, como en un chat real. " +
            "Si te preguntan sinceramente si eres una IA, no lo niegues. " +
            "Si él está seco, tú sacas tema. " +
            "Si él coquetea, sigues el juego sin pasarte.";

    public String construir(String nombreUsuario, String conversacion, String ultimoMensaje) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT).append("\n\n");
        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            sb.append("El nombre del chico con el que hablas es: ")
              .append(nombreUsuario).append("\n\n");
        }
        sb.append("Últimos mensajes de la conversación (YO = Layla, EL = el chico):\n");
        sb.append(conversacion).append("\n");
        sb.append("Responde como Layla al último mensaje de EL: \"")
          .append(ultimoMensaje).append("\"\n");
        sb.append("Solo devuelve el texto de la respuesta, sin comillas ni prefijos.");
        return sb.toString();
    }
}