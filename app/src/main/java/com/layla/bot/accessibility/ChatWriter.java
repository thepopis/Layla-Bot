package com.layla.bot.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class ChatWriter {

    private static final String LAYLA_PKG = "com.layla.chat";

    public interface Callback {
        void onResultado(String mensaje);
    }

    private final AccessibilityService service;
    private final Handler handler;

    public ChatWriter(AccessibilityService service, Handler handler) {
        this.service = service;
        this.handler = handler;
    }

    public void escribirYEnviar(String texto, Callback cb) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null || !LAYLA_PKG.equals(String.valueOf(root.getPackageName()))) {
            NodeUtils.recycle(root);
            cb.onResultado("Falló: no estás en Layla.");
            return;
        }

        List<AccessibilityNodeInfo> cajas =
                root.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/et_input_msg");
        if (cajas == null || cajas.isEmpty()) {
            NodeUtils.recycle(root);
            cb.onResultado("Falló: no encuentro la caja de texto.");
            return;
        }

        AccessibilityNodeInfo caja = cajas.get(0);
        if (!caja.isEditable()) {
            NodeUtils.recycleAll(cajas);
            NodeUtils.recycle(root);
            cb.onResultado("Falló: el nodo no es editable.");
            return;
        }

        ClipboardManager cm = (ClipboardManager) service.getSystemService(
                AccessibilityService.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("layla", texto));
        }

        boolean click = caja.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        handler.postDelayed(() -> {
            boolean paste = caja.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            handler.postDelayed(() -> {
                boolean enviado = pulsarEnviar();
                NodeUtils.recycleAll(cajas);
                NodeUtils.recycle(root);
                cb.onResultado("click=" + click + ", paste=" + paste + ", enviado=" + enviado);
            }, Config.DELAY_PASTE_A_ENVIAR_MS);
        }, Config.DELAY_CLICK_A_PASTE_MS);
    }

    /** Pulsa el botón "siguiente chat" (ll_next_unread). */
    public void pulsarSiguienteChat(Callback cb) {
        AccessibilityNodeInfo raiz = service.getRootInActiveWindow();
        if (raiz == null) {
            cb.onResultado("Falló: no hay ventana activa.");
            return;
        }

        try {
            List<AccessibilityNodeInfo> botones =
                    raiz.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/ll_next_unread");
            if (botones == null || botones.isEmpty()) {
                cb.onResultado("Falló: no encuentro ll_next_unread (¿no hay chats pendientes?).");
                return;
            }

            AccessibilityNodeInfo boton = botones.get(0);
            try {
                if (boton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    cb.onResultado("OK: siguiente chat pulsado (click directo).");
                    return;
                }

                AccessibilityNodeInfo padre = boton.getParent();
                if (padre != null) {
                    boolean ok = padre.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    NodeUtils.recycle(padre);
                    if (ok) {
                        cb.onResultado("OK: siguiente chat pulsado (click en padre).");
                        return;
                    }
                }

                Rect rect = new Rect();
                boton.getBoundsInScreen(rect);
                if (rect.width() <= 0 || rect.height() <= 0) {
                    cb.onResultado("Falló: bounds de ll_next_unread inválidos.");
                    return;
                }

                Path path = new Path();
                path.moveTo(rect.centerX(), rect.centerY());
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(path, 0L, 100L);
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(stroke).build();
                boolean ok = service.dispatchGesture(gesture, null, null);
                cb.onResultado("OK: siguiente chat pulsado (tap gestual=" + ok + ").");
            } finally {
                NodeUtils.recycleAll(botones);
            }
        } finally {
            NodeUtils.recycle(raiz);
        }
    }

    private boolean pulsarEnviar() {
        AccessibilityNodeInfo raiz = service.getRootInActiveWindow();
        if (raiz == null) return false;

        try {
            List<AccessibilityNodeInfo> botones =
                    raiz.findAccessibilityNodeInfosByViewId(LAYLA_PKG + ":id/iv_send");
            if (botones == null || botones.isEmpty()) return false;

            AccessibilityNodeInfo boton = botones.get(0);
            try {
                if (boton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;

                AccessibilityNodeInfo padre = boton.getParent();
                if (padre != null) {
                    boolean ok = padre.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    NodeUtils.recycle(padre);
                    if (ok) return true;
                }

                Rect rect = new Rect();
                boton.getBoundsInScreen(rect);
                if (rect.width() <= 0 || rect.height() <= 0) return false;

                Path path = new Path();
                path.moveTo(rect.centerX(), rect.centerY());
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(path, 0L, 100L);
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(stroke).build();
                return service.dispatchGesture(gesture, null, null);
            } finally {
                NodeUtils.recycleAll(botones);
            }
        } finally {
            NodeUtils.recycle(raiz);
        }
    }
}