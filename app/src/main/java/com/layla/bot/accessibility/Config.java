package com.layla.bot.accessibility;

public final class Config {
    private Config() {}

    public static final String LAYLA_PKG = "com.layla.chat";

    /** Milisegundos de debounce tras un evento de accesibilidad. */
    public static final long DEBOUNCE_MS = 800;

    /** Milisegundos de espera tras el click en la caja antes de pegar. */
    public static final long DELAY_CLICK_A_PASTE_MS = 400;

    /** Milisegundos de espera tras el paste antes de buscar el botón enviar. */
    public static final long DELAY_PASTE_A_ENVIAR_MS = 800;

    /** Milisegundos de espera antes de la prueba de escritura (dar tiempo a cambiar de app). */
    public static final long DELAY_PRUEBA_MS = 6000;

    /** Tiempo máximo (ms) que puede tardar una lectura antes de descartarse. */
    public static final long TIMEOUT_LECTURA_MS = 5000;

    /** Límite de caracteres del volcado de pantalla (para no saturar Logcat/UI). */
    public static final int LIMITE_VOLCADO_CHARS = 20000;
}