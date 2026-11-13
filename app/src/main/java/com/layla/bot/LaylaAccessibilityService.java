package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
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

    // ---------- Prueba de escritura (click + paste + detectar botón enviar) ----------

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

        // 1. Cargar el portapapeles
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("layla", texto));
        }

        // 2. Click en la caja para abrir el teclado y darle foco real
        boolean click = caja.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        pruebaEstado = "Click=" + click + ", esperando 400ms...";

        // 3. Esperar y pegar
        handler.postDelayed(() -> {
            try {
                boolean paste = caja.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                pruebaEstado = "Click=" + click + ", paste=" + paste
                        + ". Esperando 500ms para verificar...";

                // 4. Verificar: buscar el botón de enviar en el árbol
                handler.postDelayed(() -> verificarYBuscarEnviar(cajas, root), 500);
            } catch (Exception e) {
                pruebaEstado = "Excepción en paste: " + e;
                for (AccessibilityNodeInfo n : cajas) recycleNode(n);
                recycleNode(root);
            }
        }, 400);
    }

    private void verificarYBuscarEnviar(List<AccessibilityNodeInfo> cajas, AccessibilityNodeInfo root) {
        try {
            AccessibilityNodeInfo raizAhora = getRootInActiveWindow();
            if (raizAhora == null) {
                pruebaEstado = "No hay ventana activa para verificar.";
                for (AccessibilityNodeInfo n : cajas) recycleNode(n);
                recycleNode(root);
                return;
            }

            // Volver a leer la caja refrescada
            List<AccessibilityNodeInfo> cajasAhora =
                    raizAhora.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/et_input_msg");
            String textoCaja = "null";
            if (cajasAhora != null && !cajasAhora.isEmpty()) {
                CharSequence t = cajasAhora.get(0).getText();
                textoCaja = t == null ? "null" : "\"" + t.toString() + "\"";
                for (AccessibilityNodeInfo n : cajasAhora) recycleNode(n);
            }

            // Buscar el panel de chat y volcarlo para detectar el botón de enviar
            List<AccessibilityNodeInfo> paneles =
                    raizAhora.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/ll_chat_panel");
            StringBuilder sb = new StringBuilder();
            if (paneles != null && !paneles.isEmpty()) {
                volcar(paneles.get(0), 0, sb);
                for (AccessibilityNodeInfo n : paneles) recycleNode(n);
            } else {
                sb.append("(ll_chat_panel no encontrado)");
            }

            pruebaEstado = "Click+paste OK. Texto caja ahora: " + textoCaja
                    + "\n--- PANEL DE CHAT ---\n" + sb;

            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
            recycleNode(raizAhora);

            handler.removeCallbacks(leer);
            handler.postDelayed(leer, 1500);
        } catch (Exception e) {
            pruebaEstado = "Excepción verificando: " + e;
            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
        }
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