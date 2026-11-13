package com.layla.bot;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class LaylaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LaylaBotService";

    public static volatile String lastDump = "Aún no se ha leído ninguna pantalla.";

    private static LaylaAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPkg = "";

    private final Runnable leer = new Runnable() {
        @Override
        public void run() {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("APP: ").append(lastPkg).append("\n");
                dump(root, 0, sb);
                lastDump = sb.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error al leer pantalla", e);
            } finally {
                recycleNode(root);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Servicio conectado");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public static void requestRefresh() {
        if (instance != null) {
            instance.handler.removeCallbacks(instance.leer);
            instance.handler.post(instance.leer);
        } else {
            lastDump = "⚠️ El servicio de accesibilidad no está activo. Actívalo en Ajustes.";
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;

        lastPkg = pkg;
        handler.removeCallbacks(leer);
        handler.postDelayed(leer, 800);
    }

    private void dump(AccessibilityNodeInfo n, int depth, StringBuilder sb) {
        if (n == null || sb.length() > 8000) return;

        try {
            if (n.isVisibleToUser()) {
                CharSequence text = n.getText();
                CharSequence desc = n.getContentDescription();
                String id = n.getViewIdResourceName();

                if (text != null || desc != null || n.isClickable() || n.isEditable()) {
                    for (int i = 0; i < depth && i < 6; i++) sb.append(" ");
                    String cls = n.getClassName() == null ? "" : n.getClassName().toString();
                    sb.append(cls.substring(cls.lastIndexOf('.') + 1));
                    if (id != null) {
                        int slash = id.indexOf('/');
                        sb.append(" id=").append(slash >= 0 ? id.substring(slash + 1) : id);
                    }
                    if (text != null) sb.append(" texto=\"").append(text).append("\"");
                    if (desc != null) sb.append(" desc=\"").append(desc).append("\"");
                    if (n.isClickable()) sb.append(" [clic]");
                    if (n.isEditable()) sb.append(" [editable]");
                    sb.append("\n");
                }
            }

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = n.getChild(i);
                    dump(child, depth + 1, sb);
                } finally {
                    recycleNode(child);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en dump", e);
        }
    }

    private static void recycleNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try { node.recycle(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onInterrupt() {
    }
}