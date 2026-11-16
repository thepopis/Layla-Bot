package com.layla.bot.accessibility;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

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

                    sb.append(quien).append(": ").append(texto.trim()).append("\n");
                } finally {
                    NodeUtils.recycle(fila);
                }
            }
            return sb.toString();
        } finally {
            NodeUtils.recycleAll(listas);
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