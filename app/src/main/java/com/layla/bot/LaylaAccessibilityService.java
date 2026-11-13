package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Path;
import android.graphics.Rect;
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
    private static volatile String siguienteEstado = "(sin probar)";
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
                + "\nPRUEBA SIGUIENTE CHAT: " + siguienteEstado
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
        s.handler.postDelayed(() -> s.escribirPrueba("hola cariño como estás"), 6000);
    }

    public static void programarPruebaSiguiente() {
        final LaylaAccessibilityService s = instance;
        if (s == null) {
            siguienteEstado = "⚠️ El servicio no está activo.";
            return;
        }
        siguienteEstado = "Esperando 6 segundos... ve a Layla y abre un chat.";
        s.handler.postDelayed(() -> s.pulsarSiguiente(), 6000);
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

    // ---------- Prueba: click + pegar + enviar ----------

    private void escribirPrueba(String texto) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            if (root == null || !LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) {
                pruebaEstado = "Falló: a los 6 segundos no estabas en Layla.";
                return;
            }

            AccessibilityNodeInfo caja = buscarPorId(root, "et_input_msg");
            if (caja == null) {
                pruebaEstado = "Falló: no encuentro la caja de texto (abre un chat).";
                return;
            }

            try {
                if (!caja.isEditable()) {
                    pruebaEstado = "Falló: el nodo encontrado no es editable.";
                    return;
                }

                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("layla", texto));
                }

                boolean click = caja.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                pruebaEstado = "Click=" + click + ", esperando 400 ms...";
                handler.postDelayed(() -> pegar(texto, click), 400);
            } finally {
                recycleNode(caja);
            }
        } finally {
            recycleNode(root);
        }
    }

    private void pegar(String texto, boolean click) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            if (root == null || !LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) {
                pruebaEstado = "Falló: saliste de Layla antes de pegar.";
                return;
            }

            AccessibilityNodeInfo caja = buscarPorId(root, "et_input_msg");
            if (caja == null) {
                pruebaEstado = "Falló: la caja de texto desapareció.";
                return;
            }

            try {
                caja.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                boolean paste = caja.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                boolean setText = false;
                if (!paste) {
                    Bundle args = new Bundle();
                    args.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
                    setText = caja.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                }
                pruebaEstado = "Click=" + click + ", paste=" + paste + ", setText=" + setText
                        + ". Esperando 800 ms para que aparezca el botón de enviar...";
            } finally {
                recycleNode(caja);
            }

            handler.postDelayed(() -> enviarMensaje(), 800);
        } finally {
            recycleNode(root);
        }
    }

    private void enviarMensaje() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            if (root == null) {
                pruebaEstado = "No hay ventana activa al buscar iv_send.";
                return;
            }

            AccessibilityNodeInfo boton = buscarPorId(root, "iv_send");
            if (boton == null) {
                pruebaEstado = "No apareció iv_send. ¿El pegado falló?"
                        + "\n--- PANEL DE CHAT ---\n" + volcarPanel(root);
                return;
            }

            try {
                // Vía 1: click directo
                boolean clickDirecto = boton.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                // Vía 2: click en el padre
                boolean clickPadre = false;
                if (!clickDirecto) {
                    AccessibilityNodeInfo padre = boton.getParent();
                    if (padre != null) {
                        clickPadre = padre.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        recycleNode(padre);
                    }
                }

                if (clickDirecto || clickPadre) {
                    pruebaEstado = "iv_send: clickDirecto=" + clickDirecto
                            + ", clickPadre=" + clickPadre;
                    return;
                }

                // Vía 3: toque por coordenadas
                Rect rect = new Rect();
                boton.getBoundsInScreen(rect);
                if (rect.width() <= 0 || rect.height() <= 0) {
                    pruebaEstado = "iv_send encontrado, pero con bounds inválidos.";
                    return;
                }

                Path path = new Path();
                path.moveTo(rect.centerX(), rect.centerY());
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0L, 100L))
                        .build();
                boolean ok = dispatchGesture(gesture, null, null);
                pruebaEstado = "iv_send en (" + rect.centerX() + "," + rect.centerY() + "). "
                        + "clickDirecto=false, clickPadre=false, tapGestual=" + ok;
            } finally {
                recycleNode(boton);
            }
        } catch (Exception e) {
            pruebaEstado = "Excepción enviando: " + e;
        } finally {
            recycleNode(root);
            handler.removeCallbacks(leer);
            handler.postDelayed(leer, 1500);
        }
    }

    // ---------- Prueba: botón de siguiente chat ----------

    private String apodoActual(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo n = buscarPorId(root, "tv_nickname");
        if (n == null) return "(sin apodo)";
        try {
            CharSequence t = n.getText();
            return t == null ? "(vacío)" : t.toString();
        } finally {
            recycleNode(n);
        }
    }

    private void pulsarSiguiente() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            if (root == null || !LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) {
                siguienteEstado = "Falló: a los 6 segundos no estabas en Layla.";
                return;
            }
            AccessibilityNodeInfo boton = buscarPorId(root, "ll_next_unread");
            if (boton == null) {
                siguienteEstado = "No veo ll_next_unread en esta pantalla.";
                return;
            }
            final String antes = apodoActual(root);
            try {
                boolean ok = boton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                siguienteEstado = "Click en ll_next_unread=" + ok
                        + ". Chat antes: " + antes + ". Verificando...";
            } finally {
                recycleNode(boton);
            }
            handler.postDelayed(() -> verificarSalto(antes), 1500);
        } finally {
            recycleNode(root);
        }
    }

    private void verificarSalto(String antes) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            if (root == null) {
                siguienteEstado = "No hay ventana activa para verificar.";
                return;
            }
            String ahora = apodoActual(root);
            siguienteEstado = antes.equals(ahora)
                    ? "El chat NO cambió (sigue: " + ahora + "). Este botón no salta de chat."
                    : "SALTÓ de '" + antes + "' a '" + ahora + "'.";
        } finally {
            recycleNode(root);
            handler.removeCallbacks(leer);
            handler.postDelayed(leer, 1500);
        }
    }

    // ---------- Utilidades ----------

    private static AccessibilityNodeInfo buscarPorId(AccessibilityNodeInfo root, String corto) {
        List<AccessibilityNodeInfo> lista =
                root.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/" + corto);
        if (lista == null || lista.isEmpty()) return null;
        for (int i = 1; i < lista.size(); i++) recycleNode(lista.get(i));
        return lista.get(0);
    }

    private String volcarPanel(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo panel = buscarPorId(root, "ll_chat_panel");
        if (panel == null) return "(ll_chat_panel no encontrado)";
        try {
            StringBuilder sb = new StringBuilder();
            volcar(panel, 0, sb);
            return sb.toString();
        } finally {
            recycleNode(panel);
        }
    }

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