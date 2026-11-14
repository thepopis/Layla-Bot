package com.layla.bot.accessibility;

import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public final class NodeUtils {

    private NodeUtils() {}

    public static boolean esId(AccessibilityNodeInfo n, String corto) {
        if (n == null) return false;
        String id = n.getViewIdResourceName();
        return id != null && id.endsWith("/" + corto);
    }

    public static boolean tiene(AccessibilityNodeInfo n, String corto) {
        if (n == null) return false;
        if (esId(n, corto)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = n.getChild(i);
                if (tiene(child, corto)) return true;
            } finally {
                recycle(child);
            }
        }
        return false;
    }

    public static String textoDe(AccessibilityNodeInfo n, String corto) {
        if (n == null) return null;
        if (esId(n, corto) && n.getText() != null) return n.getText().toString();
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = n.getChild(i);
                String t = textoDe(child, corto);
                if (t != null) return t;
            } finally {
                recycle(child);
            }
        }
        return null;
    }

    public static void volcar(AccessibilityNodeInfo n, int nivel, StringBuilder sb) {
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
                recycle(child);
            }
        }
    }

    public static void recycle(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try { node.recycle(); } catch (Exception ignored) {}
        }
    }

    public static void recycleAll(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) return;
        for (AccessibilityNodeInfo n : nodes) recycle(n);
    }
}