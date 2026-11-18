package com.layla.bot.accessibility;

public final class Config {
    private Config() {}

    public static final String LAYLA_PKG = "com.layla.chat";
    public static final long DEBOUNCE_MS = 800;
    public static final long DELAY_CLICK_A_PASTE_MS = 400;
    public static final long DELAY_PASTE_A_ENVIAR_MS = 800;
    public static final long DELAY_PRUEBA_MS = 6000;
    public static final long TIMEOUT_LECTURA_MS = 5000;
    public static final int LIMITE_VOLCADO_CHARS = 20000;

    /** Tiempo que se espera a que el usuario responda antes de saltar al siguiente chat. */
    public static final long DELAY_ESPERA_RESPUESTA_MS = 15000;

    /** Tiempo máximo que el bot puede estar "respondiendo" antes de liberarse. */
    public static final long MAX_RESPONDIENDO_MS = 90000;
}