package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class LaylaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LaylaBot";
    private static final String LAYLA_PKG = "com.layla.chat";

    private static volatile String estado = "Aún no se ha leído ninguna pantalla.";
    private static volatile String pruebaEstado = "(sin probar)";
    private static volatile String ultimoEntrante = "(ninguno)";
    private static volatile String conversacion = "";
    private static volatile String pantalla = "";

    private static LaylaAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable leer = () -> leerPantalla();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        estado = "Servicio conectado. Abre un chat en Layla.";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!LAYLA_PKG.equals(event.getPackageName().toString())) return;
        handler.removeCallbacks(leer);
        handler.postDelayed(leer, 800);
    }

    @Override
    public void onInterrupt() {
    }

    // ---------- Lo que usa la pantalla de la app ----------

    public static String informe() {
        String aviso = (instance == null)
                ? "⚠️ El servicio de accesibilidad no está activo. Actívalo en Ajustes.\n\n"
                : "";
        return aviso
                + "ESTADO: " + estado
                + "\nPRUEBA DE ESCRITURA: " + pruebaEstado
                + "\n\nÚLTIMO MENSAJE ENTRANTE:\n" + ultimoEntrante
                + "\n\nCONVERSACIÓN VISIBLE:\n" + conversacion
                + "\n--- PANTALLA ---\n" + pantalla;
    }

    public static void programarPruebaEscritura() {
        final LaylaAccessibilityService s = instance;
        if (s == null) {
            pruebaEstado = "⚠️ El servicio no está activo.";
            return;
        }
        pruebaEstado = "Esperando 6 segundos... ve a Layla y abre un chat.";
        s.handler.postDelayed(() -> s.escribirPrueba("prueba del bot"), 6000);
    }

    // ---------- Lectura del chat ----------

    private void leerPantalla() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return;
            if (!LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) return;

            StringBuilder sb = new StringBuilder();
            volcar(root, 0, sb);
            pantalla = sb.toString();
            analizarChat(root);
        } catch (Exception e) {
            Log.e(TAG, "Error al leer", e);
            estado = "Error al leer: " + e;
        } finally {
            recycleNode(root);
        }
    }

    private void analizarChat(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> listas =
                root.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/rvChattingList");
        if (listas == null || listas.isEmpty()) {
            estado = "Estás en Layla, pero no veo una conversación abierta.";
            return;
        }

        AccessibilityNodeInfo lista = listas.get(0);
        try {
            StringBuilder sb = new StringBuilder();
            String ultimoTexto = null;
            String ultimoQuien = "";

            for (int i = 0; i < lista.getChildCount(); i++) {
                AccessibilityNodeInfo fila = null;
                try {
                    fila = lista.getChild(i);
                    if (fila == null) continue;
                    String texto = textoDe(fila, "tv_content");
                    if (texto == null || texto.trim().isEmpty()) continue;

                    String quien;
                    if (tiene(fila, "iv_read_status")) quien = "YO";
                    else if (tiene(fila, "iv_translate")) quien = "ELLOS";
                    else quien = "?";

                    ultimoTexto = texto.trim();
                    ultimoQuien = quien;
                    sb.append(quien).append(": ").append(ultimoTexto).append("\n");
                } finally {
                    recycleNode(fila);
                }
            }

            conversacion = sb.toString();
            if (ultimoTexto == null) {
                estado = "Veo el chat, pero sin mensajes de texto.";
                return;
            }
            estado = "El último mensaje visible es de: " + ultimoQuien;
            ultimoEntrante = ultimoQuien.equals("ELLOS")
                    ? ultimoTexto
                    : "(el último mensaje no es del otro, no hay nada que responder)";
        } finally {
            for (AccessibilityNodeInfo nodo : listas) recycleNode(nodo);
        }
    }

    // ---------- Prueba de escritura (4 alternativas) ----------

    private void escribirPrueba(String texto) {
        final AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) {
            pruebaEstado = "Falló: a los 6 segundos no estabas en Layla.";
            recycleNode(root);
            return;
        }

        final List<AccessibilityNodeInfo> cajas =
                root.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/et_input_msg");
        if (cajas == null || cajas.isEmpty()) {
            pruebaEstado = "Falló: no encuentro la caja de texto (abre un chat).";
            recycleNode(root);
            return;
        }

        final AccessibilityNodeInfo caja = cajas.get(0);
        if (!caja.isEditable()) {
            pruebaEstado = "Falló: el nodo encontrado no es editable.";
            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
            return;
        }

        // Primero cargar el portapapeles ANTES de cualquier acción
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("layla", texto));
        }

        // Probar todas las alternativas en secuencia, la que funcione primero gana
        StringBuilder diag = new StringBuilder();

        // --- Alternativa 1: SET_TEXT sin click previo, con selección completa ---
        Bundle args1 = new Bundle();
        args1.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
        args1.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
        args1.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, texto.length());
        boolean a1 = caja.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args1);
        String t1 = leerTexto(caja);
        diag.append("A1_setText=").append(a1).append("/").append(t1);
        if (t1 != null && t1.contains("prueba")) {
            pruebaEstado = diag.toString();
            finalizarPrueba(cajas, root);
            return;
        }

        // --- Alternativa 2: click + SET_TEXT ---
        boolean click = caja.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Bundle args2 = new Bundle();
        args2.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
        boolean a2 = caja.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args2);
        String t2 = leerTexto(caja);
        diag.append(" A2_click=").append(click).append(" setText=").append(a2)
            .append("/").append(t2);
        if (t2 != null && t2.contains("prueba")) {
            pruebaEstado = diag.toString();
            finalizarPrueba(cajas, root);
            return;
        }

        // --- Alternativa 3: click + PASTE ---
        boolean a3 = caja.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        String t3 = leerTexto(caja);
        diag.append(" A3_paste=").append(a3).append("/").append(t3);
        if (t3 != null && t3.contains("prueba")) {
            pruebaEstado = diag.toString();
            finalizarPrueba(cajas, root);
            return;
        }

        // --- Alternativa 4: SET_TEXT en el padre (ll_input_msg) ---
        AccessibilityNodeInfo padre = caja.getParent();
        if (padre != null) {
            Bundle args4 = new Bundle();
            args4.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
            boolean a4 = padre.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args4);
            String t4 = leerTexto(caja);
            diag.append(" A4_padre=").append(a4).append("/").append(t4);
            recycleNode(padre);
        }

        pruebaEstado = "NINGUNA FUNCIONÓ. " + diag.toString();
        finalizarPrueba(cajas, root);
    }

    private static String leerTexto(AccessibilityNodeInfo n) {
        if (n == null) return "null";
        CharSequence t = n.getText();
        return t == null ? "null" : "\"" + t.toString() + "\"";
    }

    private void finalizarPrueba(List<AccessibilityNodeInfo> cajas, AccessibilityNodeInfo root) {
        for (AccessibilityNodeInfo n : cajas) recycleNode(n);
        recycleNode(root);
        handler.removeCallbacks(leer);
        handler.postDelayed(leer, 1500);
    }

    // ---------- Utilidades ----------

    private static boolean esId(AccessibilityNodeInfo n, String corto) {
        String id = n.getViewIdResourceName();
        return id != null && id.endsWith("/" + corto);
    }

    private static boolean tiene(AccessibilityNodeInfo n, String corto) {
        if (n == null) return false;
        if (esId(n, corto)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = n.getChild(i);
                if (tiene(child, corto)) return true;
            } finally {
                recycleNode(child);
            }
        }
        return false;
    }

    private static String textoDe(AccessibilityNodeInfo n, String corto) {
        if (n == null) return null;
        if (esId(n, corto) && n.getText() != null) return n.getText().toString();
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = n.getChild(i);
                String t = textoDe(child, corto);
                if (t != null) return t;
            } finally {
                recycleNode(child);
            }
        }
        return null;
    }

    private void volcar(AccessibilityNodeInfo n, int nivel, StringBuilder sb) {
        if (n == null || sb.length() > 20000) return;
        CharSequence texto = n.getText();
        CharSequence desc = n.getContentDescription();
        String id = n.getViewIdResourceName();

        if (texto != null || desc != null || id != null || n.isClickable() || n.isEditable()) {
            for (int i = 0; i < nivel && i < 6; i++) sb.append(" ");
            String cls = n.getClassName() == null ? "" : n.getClassName().toString();
            sb.append(cls.substring(cls.lastIndexOf('.') + 1));
            if (id != null) {
                int s = id.indexOf('/');
                sb.append(" id=").append(s >= 0 ? id.substring(s + 1) : id);
            }
            if (texto != null) sb.append(" texto=\"").append(texto).append("\"");
            if (desc != null) sb.append(" desc=\"").append(desc).append("\"");
            if (n.isClickable()) sb.append(" [clic]");
            if (n.isEditable()) sb.append(" [editable]");
            sb.append("\n");
        }

        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = n.getChild(i);
                volcar(child, nivel + 1, sb);
            } finally {
                recycleNode(child);
            }
        }
    }

    private static void recycleNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try { node.recycle(); } catch (Exception ignored) {}
        }
    }
}