package com.layla.bot.service;

import android.content.Context;
import android.content.SharedPreferences;

public class BotState {

    private static final String PREFS = "layla_bot_state";
    private static final String K_ACTIVO = "servicio_activo";
    private static final String K_ESTADO = "estado";
    private static final String K_PRUEBA = "prueba";
    private static final String K_ULTIMO = "ultimo_entrante";
    private static final String K_CONV = "conversacion";
    private static final String K_PANTALLA = "pantalla";
    private static final String K_ERROR_BURBUJA = "error_burbuja";
    private static final String K_DIAGNOSTICO = "diagnostico";

    private final SharedPreferences prefs;

    public BotState(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void marcarServicioActivo(boolean activo) {
        prefs.edit()
                .putBoolean(K_ACTIVO, activo)
                .putString(K_ESTADO, activo
                        ? "Servicio conectado. Abre un chat en Layla."
                        : "Servicio desconectado.")
                .apply();
    }

    public void setEstado(String estado) { prefs.edit().putString(K_ESTADO, estado).apply(); }
    public void setPruebaEstado(String p) { prefs.edit().putString(K_PRUEBA, p).apply(); }
    public void setUltimoEntrante(String u) { prefs.edit().putString(K_ULTIMO, u).apply(); }
    public void setConversacion(String c) { prefs.edit().putString(K_CONV, c).apply(); }
    public void setPantalla(String p) { prefs.edit().putString(K_PANTALLA, p).apply(); }
    public void setDiagnostico(String d) { prefs.edit().putString(K_DIAGNOSTICO, d).apply(); }
    
    // NUEVO: Para capturar el error de la burbuja
    public void setErrorBurbuja(String error) { prefs.edit().putString(K_ERROR_BURBUJA, error).apply(); }

    public String informe() {
        String errorBurbuja = prefs.getString(K_ERROR_BURBUJA, "");
        String avisoBurbuja = errorBurbuja.isEmpty() ? "" : "🚨 ERROR EN BURBUJA:\n" + errorBurbuja + "\n\n";

        boolean activo = prefs.getBoolean(K_ACTIVO, false);
        String aviso = activo ? ""
                : "⚠️ El servicio de accesibilidad no está activo. Actívalo en Ajustes.\n\n";

        return avisoBurbuja + aviso
                + "ESTADO: " + prefs.getString(K_ESTADO, "Aún no se ha leído ninguna pantalla.")
                + "\nDIAGNÓSTICO: " + prefs.getString(K_DIAGNOSTICO, "")
                + "\nPRUEBA DE ESCRITURA: " + prefs.getString(K_PRUEBA, "(sin probar)")
                + "\n\nÚLTIMO MENSAJE ENTRANTE:\n" + prefs.getString(K_ULTIMO, "(ninguno)")
                + "\n\nCONVERSACIÓN VISIBLE:\n" + prefs.getString(K_CONV, "")
                + "\n--- PANTALLA ---\n" + prefs.getString(K_PANTALLA, "");
    }
}