package com.layla.bot.accessibility;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class ChatReader {

    private static final String LAYLA_PKG = "com.layla.chat";

    /** Devuelve "YO: ...\nELLOS: ...\n" o null si no hay chat abierto. */
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
                    else if (NodeUtils.tiene(fila, "iv_translate")) quien = "ELLOS";
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

    /** Volcado completo de la pantalla (para diagnóstico). */
    public String volcarPantalla(AccessibilityNodeInfo root) {
        StringBuilder sb = new StringBuilder();
        NodeUtils.volcar(root, 0, sb);
        return sb.toString();
    }
}