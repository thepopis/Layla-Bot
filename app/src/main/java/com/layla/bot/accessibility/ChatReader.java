package com.layla.bot.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatReader {

    private static final String LAYLA_PKG = "com.layla.chat";

    /** Devuelve "YO: ...\nEL: ...\n" o null si no hay chat abierto. */
    public String leerConversacion(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> listas =
                root.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/rvChattingList");
        if (listas == null || listas.isEmpty()) return null;

        AccessibilityNodeInfo lista = listas.get(0);
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lista.getChildCount(); i++) {
                AccessibilityNodeInfo fila = null;
                try {
                    fila = lista.getChild(i);
                    if (fila == null) continue;
                    String texto = NodeUtils.textoDe(fila, "tv_content");
                    if (texto == null || texto.trim().isEmpty()) continue;

                    String quien;
                    if (NodeUtils.tiene(fila, "iv_read_status")) quien = "YO";
                    else if (NodeUtils.tiene(fila, "iv_translate")) quien = "EL";
                    else quien = "?";

                    String limpio = texto.trim().replaceAll("\\s+", " ");
                    sb.append(quien).append(": ").append(limpio).append("\n");
                } finally {
                    NodeUtils.recycle(fila);
                }
            }
            return sb.toString();
        } finally {
            NodeUtils.recycleAll(listas);
        }
    }

    /**
     * Hace scroll hacia arriba varias veces para leer todos los mensajes del chat,
     * y devuelve el historial completo ordenado de más antiguo a más reciente.
     *
     * IMPORTANTE: este método hace Thread.sleep() internamente, así que DEBE
     * llamarse desde un hilo secundario, nunca desde el hilo principal.
     */
    public String leerHistorialCompleto(AccessibilityService service,
                                         AccessibilityNodeInfo root,
                                         int maxScrolls) {
        if (root == null) return null;

        List<String> todosLosMensajes = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        List<AccessibilityNodeInfo> listas =
                root.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/rvChattingList");
        if (listas == null || listas.isEmpty()) return null;

        AccessibilityNodeInfo lista = listas.get(0);
        try {
            // 1. Leer lo visible actualmente
            leerMensajesVisibles(lista, todosLosMensajes, vistos);

            // 2. Hacer scroll hacia arriba varias veces para leer más
            for (int i = 0; i < maxScrolls; i++) {
                boolean scrolled = lista.performAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                if (!scrolled) break;

                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                List<AccessibilityNodeInfo> listas2 = root
                        .findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/rvChattingList");
                if (listas2 == null || listas2.isEmpty()) break;
                AccessibilityNodeInfo lista2 = listas2.get(0);
                try {
                    leerMensajesVisibles(lista2, todosLosMensajes, vistos);
                } finally {
                    if (lista2 != lista) NodeUtils.recycle(lista2);
                    NodeUtils.recycle(listas2.size() > 1 ? listas2.get(1) : null);
                }
            }
        } finally {
            NodeUtils.recycleAll(listas);
        }

        // 3. Volver abajo del todo
        List<AccessibilityNodeInfo> listasFin = root
                .findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/rvChattingList");
        if (listasFin != null && !listasFin.isEmpty()) {
            listasFin.get(0).performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            NodeUtils.recycleAll(listasFin);
        }

        // 4. Construir el string (orden: más antiguo primero)
        StringBuilder sb = new StringBuilder();
        for (String m : todosLosMensajes) {
            sb.append(m).append("\n");
        }
        return sb.toString().trim();
    }

    private void leerMensajesVisibles(AccessibilityNodeInfo lista,
                                       List<String> acumulados,
                                       Set<String> vistos) {
        for (int i = 0; i < lista.getChildCount(); i++) {
            AccessibilityNodeInfo fila = null;
            try {
                fila = lista.getChild(i);
                if (fila == null) continue;
                String texto = NodeUtils.textoDe(fila, "tv_content");
                if (texto == null || texto.trim().isEmpty()) continue;

                String quien;
                if (NodeUtils.tiene(fila, "iv_read_status")) quien = "YO";
                else if (NodeUtils.tiene(fila, "iv_translate")) quien = "EL";
                else quien = "?";

                String limpio = texto.trim().replaceAll("\\s+", " ");
                String clave = quien + "|" + limpio;
                if (vistos.contains(clave)) continue;
                vistos.add(clave);
                acumulados.add(quien + ": " + limpio);
            } finally {
                NodeUtils.recycle(fila);
            }
        }
    }

    /** Nombre del chico (puede ser vacío si no se encuentra). */
    public String leerNombreUsuario(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodos =
                root.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/tv_nickname");
        if (nodos == null || nodos.isEmpty()) return "";
        try {
            CharSequence t = nodos.get(0).getText();
            return t != null ? t.toString().trim() : "";
        } finally {
            NodeUtils.recycleAll(nodos);
        }
    }

    /** Volcado completo de la pantalla (para diagnóstico). */
    public String volcarPantalla(AccessibilityNodeInfo root) {
        StringBuilder sb = new StringBuilder();
        NodeUtils.volcar(root, 0, sb);
        return sb.toString();
    }
}