package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
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

    // ---------- Prueba de escritura (dispatchGesture + menú Pegar) ----------

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

        // 1. Cargar el portapapeles ANTES de cualquier gesto
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("layla", texto));
        }

        // 2. Obtener coordenadas del EditText en pantalla
        Rect rect = new Rect();
        caja.getBoundsInScreen(rect);
        if (rect.width() <= 0 || rect.height() <= 0) {
            pruebaEstado = "Falló: bounds de la caja inválidos (" + rect + ")";
            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
            return;
        }

        int cx = rect.centerX();
        int cy = rect.centerY();
        pruebaEstado = "Long-press en (" + cx + "," + cy + ")... esperando menú.";

        // 3. dispatchGesture con long-press (duración 700ms)
        Path path = new Path();
        path.moveTo(cx, cy);
        android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 700L);
        android.accessibilityservice.GestureDescription gesture =
                new android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(stroke)
                        .build();

        boolean dispatched = dispatchGesture(gesture,
                new android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(android.accessibilityservice.GestureDescription g) {
                        super.onCompleted(g);
                        handler.postDelayed(() -> {
                            try {
                                pulsarPegar(cajas, root);
                            } catch (Exception e) {
                                pruebaEstado = "Excepción buscando Pegar: " + e;
                            }
                        }, 800);
                    }

                    @Override
                    public void onCancelled(android.accessibilityservice.GestureDescription g) {
                        super.onCancelled(g);
                        pruebaEstado = "Falló: gesto cancelado por el sistema.";
                        for (AccessibilityNodeInfo n : cajas) recycleNode(n);
                        recycleNode(root);
                    }
                }, null);

        if (!dispatched) {
            pruebaEstado = "Falló: dispatchGesture devolvió false.";
            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
        }
    }

    private void pulsarPegar(List<AccessibilityNodeInfo> cajas, AccessibilityNodeInfo root) {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) {
            pruebaEstado = "Falló: no hay ventana activa al buscar Pegar.";
            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
            return;
        }

        try {
            AccessibilityNodeInfo botonPegar = buscarPorTexto(raiz, "Pegar", 0);
            if (botonPegar == null) {
                botonPegar = buscarPorTexto(raiz, "Paste", 0);
            }
            if (botonPegar == null) {
                pruebaEstado = "Menú apareció pero no encontré 'Pegar'. Volcado parcial:\n"
                        + volcarMenu(raiz);
                for (AccessibilityNodeInfo n : cajas) recycleNode(n);
                recycleNode(root);
                recycleNode(raiz);
                return;
            }

            AccessibilityNodeInfo clickable = buscarPadreClickable(botonPegar);
            boolean ok = (clickable != null ? clickable : botonPegar)
                    .performAction(AccessibilityNodeInfo.ACTION_CLICK);

            String textoFinal = leerTexto(cajas.get(0));
            pruebaEstado = "Long-press OK, Pegar clickeado=" + ok + ". Texto final: " + textoFinal;

            if (clickable != null && clickable != botonPegar) recycleNode(clickable);
            recycleNode(botonPegar);

            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
            recycleNode(raiz);

            handler.removeCallbacks(leer);
            handler.postDelayed(leer, 1500);
        } catch (Exception e) {
            pruebaEstado = "Excepción en pulsarPegar: " + e;
            for (AccessibilityNodeInfo n : cajas) recycleNode(n);
            recycleNode(root);
            recycleNode(raiz);
        }
    }

    private AccessibilityNodeInfo buscarPorTexto(AccessibilityNodeInfo n, String buscado, int depth) {
        if (n == null || depth > 30) return null;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        if ((t != null && t.toString().equalsIgnoreCase(buscado))
                || (d != null && d.toString().equalsIgnoreCase(buscado))) {
            return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = n.getChild(i);
                AccessibilityNodeInfo found = buscarPorTexto(child, buscado, depth + 1);
                if (found != null) {
                    if (found != child) recycleNode(child);
                    return found;
                }
            } finally {
                if (child != null) {
                    // Solo reciclar si no se devolvió
                }
            }
        }
        return null;
    }

    private AccessibilityNodeInfo buscarPadreClickable(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        int limit = 5;
        while (cur != null && limit-- > 0) {
            if (cur.isClickable()) return cur;
            AccessibilityNodeInfo p = cur.getParent();
            if (cur != n) recycleNode(cur);
            cur = p;
        }
        return null;
    }

    private String volcarMenu(AccessibilityNodeInfo n) {
        StringBuilder sb = new StringBuilder();
        volcar(n, 0, sb);
        if (sb.length() > 2000) return sb.substring(0, 2000) + "...";
        return sb.toString();
    }

    private static String leerTexto(AccessibilityNodeInfo n) {
        if (n == null) return "null";
        CharSequence t = n.getText();
        return t == null ? "null" : "\"" + t.toString() + "\"";
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