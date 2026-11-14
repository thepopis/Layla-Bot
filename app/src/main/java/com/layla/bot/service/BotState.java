package com.layla.bot.service;

public class BotState {

    private volatile String estado = "Aún no se ha leído ninguna pantalla.";
    private volatile String pruebaEstado = "(sin probar)";
    private volatile String ultimoEntrante = "(ninguno)";
    private volatile String conversacion = "";
    private volatile String pantalla = "";
    private volatile boolean servicioActivo = false;

    public synchronized void marcarServicioActivo(boolean activo) {
        this.servicioActivo = activo;
        if (activo) {
            estado = "Servicio conectado. Abre un chat en Layla.";
        }
    }

    public synchronized void setEstado(String estado) { this.estado = estado; }
    public synchronized void setPruebaEstado(String p) { this.pruebaEstado = p; }
    public synchronized void setUltimoEntrante(String u) { this.ultimoEntrante = u; }
    public synchronized void setConversacion(String c) { this.conversacion = c; }
    public synchronized void setPantalla(String p) { this.pantalla = p; }

    public synchronized String informe() {
        String aviso = servicioActivo
                ? ""
                : "⚠️ El servicio de accesibilidad no está activo. Actívalo en Ajustes.\n\n";
        return aviso
                + "ESTADO: " + estado
                + "\nPRUEBA DE ESCRITURA: " + pruebaEstado
                + "\n\nÚLTIMO MENSAJE ENTRANTE:\n" + ultimoEntrante
                + "\n\nCONVERSACIÓN VISIBLE:\n" + conversacion
                + "\n--- PANTALLA ---\n" + pantalla;
    }
}