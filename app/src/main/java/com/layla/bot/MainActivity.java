package com.layla.bot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.layla.bot.data.IaRepository;
import com.layla.bot.service.BotState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView salida;
    private TextView aviso;
    private TextView tvServicio;
    private TextView tvEstado;
    private TextView tvUltimo;
    private TextView tvResultadoIa;
    private TextView tvIaEstado;
    private LinearLayout llChat;
    private SwitchCompat swAuto;
    private BotState state;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String ultimoInforme = null;

    /** Refresca el panel solo, cada 2 segundos, mientras la app está abierta. */
    private final Runnable refrescar = new Runnable() {
        @Override
        public void run() {
            mostrarInforme(state.informe());
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        state = new BotState(this);
        prefs = getSharedPreferences("layla_prefs", Context.MODE_PRIVATE);

        salida = findViewById(R.id.salida);
        aviso = findViewById(R.id.aviso);
        tvServicio = findViewById(R.id.tv_servicio);
        tvEstado = findViewById(R.id.tv_estado);
        tvUltimo = findViewById(R.id.tv_ultimo);
        tvResultadoIa = findViewById(R.id.tv_resultado_ia);
        tvIaEstado = findViewById(R.id.tv_ia_estado);
        llChat = findViewById(R.id.ll_chat);
        TextView tvFecha = findViewById(R.id.tv_fecha);
        TextView detalles = findViewById(R.id.tv_detalles);

        Button actualizar = findViewById(R.id.btn_actualizar);
        Button copiar = findViewById(R.id.btn_copiar);
        Button ajustes = findViewById(R.id.btn_ajustes);
        Button prueba = findViewById(R.id.btn_prueba);
        Button siguiente = findViewById(R.id.btn_siguiente);
        EditText etCfAccount = findViewById(R.id.et_cf_account);
        EditText etCfToken = findViewById(R.id.et_cf_token);
        EditText etKey = findViewById(R.id.et_api_key);
        Button guardarKey = findViewById(R.id.btn_guardar_key);
        Button probarIa = findViewById(R.id.btn_probar_ia);

        tvFecha.setText(new SimpleDateFormat("d MMM yyyy", new Locale("es")).format(new Date()));
        actualizarEstadoIa();
        agregarInterruptorAuto();

        actualizar.setOnClickListener(v -> mostrarInforme(state.informe()));

        copiar.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("layla", state.informe()));
                Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
            }
        });

        View.OnClickListener abrirAjustes = v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        ajustes.setOnClickListener(abrirAjustes);
        aviso.setOnClickListener(abrirAjustes);

        if (prueba != null) {
            prueba.setOnClickListener(v -> {
                LaylaAccessibilityService.programarPruebaEscritura();
                Toast.makeText(this, "Ve a Layla y abre un chat. Escribo en 6 segundos.",
                        Toast.LENGTH_LONG).show();
            });
        }

        if (siguiente != null) {
            siguiente.setOnClickListener(v -> {
                LaylaAccessibilityService.programarPruebaSiguienteChat();
                Toast.makeText(this, "Ve a Layla y abre un chat. Salto en 6 segundos.",
                        Toast.LENGTH_LONG).show();
            });
        }

        detalles.setOnClickListener(v -> {
            boolean visible = salida.getVisibility() == View.VISIBLE;
            salida.setVisibility(visible ? View.GONE : View.VISIBLE);
            detalles.setText(visible ? "DETALLES TÉCNICOS  ▾" : "DETALLES TÉCNICOS  ▴");
        });

        // Guarda solo los campos que hayas llenado; los vacíos no borran lo anterior.
        guardarKey.setOnClickListener(v -> {
            String cuenta = etCfAccount.getText().toString().trim();
            String token = etCfToken.getText().toString().trim();
            String gemini = etKey.getText().toString().trim();
            if (cuenta.isEmpty() && token.isEmpty() && gemini.isEmpty()) {
                Toast.makeText(this, "Pega primero algún dato", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences.Editor ed = prefs.edit();
            if (!cuenta.isEmpty()) ed.putString("cf_account", cuenta);
            if (!token.isEmpty()) ed.putString("cf_token", token);
            if (!gemini.isEmpty()) ed.putString("gemini_key", gemini);
            ed.apply();
            etCfAccount.setText("");
            etCfToken.setText("");
            etKey.setText("");
            actualizarEstadoIa();
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
        });

        probarIa.setOnClickListener(v -> {
            String cuenta = prefs.getString("cf_account", "");
            String token = prefs.getString("cf_token", "");
            String gemini = prefs.getString("gemini_key", "");
            if (cuenta.isEmpty() && token.isEmpty() && gemini.isEmpty()) {
                Toast.makeText(this, "Guarda primero las claves", Toast.LENGTH_SHORT).show();
                return;
            }
            tvResultadoIa.setText("Probando... (puede tardar si la conexión está lenta)");
            new Thread(() -> {
                IaRepository ia = new IaRepository();
                String resultado;
                try {
                    String r = ia.generar(cuenta, token, gemini,
                            "Saluda en una frase corta en español.");
                    resultado = "[" + ia.getUltimoProveedor() + "] " + r.trim();
                } catch (Exception e) {
                    resultado = "Error: " + e.getMessage();
                }
                final String texto = resultado;
                runOnUiThread(() -> tvResultadoIa.setText(texto));
            }).start();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refrescar);
        actualizarEstadoIa();
        // Por si se cambió desde la burbuja flotante mientras la app estaba en segundo plano
        if (swAuto != null) swAuto.setChecked(prefs.getBoolean("auto_activo", false));
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refrescar);
    }

    /** Interruptor "Respuesta automática con IA" (arranca apagado). */
    private void agregarInterruptorAuto() {
        swAuto = new SwitchCompat(this);
        swAuto.setText("Respuesta automática con IA");
        swAuto.setTextColor(0xFF1F2933);
        swAuto.setTextSize(15);
        swAuto.setPadding(dp(4), dp(14), dp(4), dp(4));
        swAuto.setChecked(prefs.getBoolean("auto_activo", false));
        swAuto.setOnCheckedChangeListener((boton, activo) -> {
            // Si el valor ya coincide, es solo una sincronización: no hacer nada.
            if (activo == prefs.getBoolean("auto_activo", false)) return;
            prefs.edit().putBoolean("auto_activo", activo).apply();
            Toast.makeText(this,
                    activo ? "Respuesta automática ACTIVADA" : "Respuesta automática apagada",
                    Toast.LENGTH_SHORT).show();
        });
        ViewGroup contenido = (ViewGroup) aviso.getParent();
        contenido.addView(swAuto, contenido.indexOfChild(aviso));
    }

    /** Muestra qué claves están guardadas (sin revelarlas). */
    private void actualizarEstadoIa() {
        boolean cf = !prefs.getString("cf_account", "").isEmpty()
                && !prefs.getString("cf_token", "").isEmpty();
        boolean gem = !prefs.getString("gemini_key", "").isEmpty();
        tvIaEstado.setText("Cloudflare: " + (cf ? "guardado ✓" : "falta")
                + "   ·   Gemini: " + (gem ? "guardado ✓" : "no (opcional)"));
    }

    /** Reparte el informe de BotState en las tarjetas del panel. */
    private void mostrarInforme(String informe) {
        if (informe == null) informe = "";
        if (informe.equals(ultimoInforme)) return; // nada cambió, no redibujar
        ultimoInforme = informe;

        salida.setText(informe);

        boolean inactivo = informe.contains("no está activo")
                || informe.contains("Servicio desconectado");
        aviso.setVisibility(inactivo ? View.VISIBLE : View.GONE);
        tvServicio.setText(inactivo ? "Servicio desconectado" : "Servicio conectado");
        tvServicio.setTextColor(inactivo ? 0xFFC0392B : 0xFF1E8E5A);

        String estado = seccion(informe, "ESTADO:", "ÚLTIMO MENSAJE ENTRANTE:");
        String ultimo = seccion(informe, "ÚLTIMO MENSAJE ENTRANTE:", "CONVERSACIÓN VISIBLE:");
        String conversacion = seccion(informe, "CONVERSACIÓN VISIBLE:", "--- PANTALLA ---");

        tvEstado.setText(estado.isEmpty() ? "—" : estado);
        tvUltimo.setText(ultimo.isEmpty() ? "—" : ultimo);

        llChat.removeAllViews();
        boolean hayMensajes = false;
        for (String linea : conversacion.split("\n")) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            // Separa la etiqueta (YO, EL, ?) del texto del mensaje.
            String etiqueta = "";
            String cuerpo = linea;
            int dosPuntos = linea.indexOf(':');
            if (dosPuntos >= 0 && dosPuntos <= 6) {
                etiqueta = linea.substring(0, dosPuntos).trim().toUpperCase(Locale.ROOT);
                cuerpo = linea.substring(dosPuntos + 1).trim();
            }
            if (cuerpo.isEmpty()) continue;

            agregarBurbuja(cuerpo, etiqueta.equals("YO"));
            hayMensajes = true;
        }
        if (!hayMensajes) {
            TextView vacio = new TextView(this);
            vacio.setText("Sin mensajes visibles");
            vacio.setTextColor(0xFF6B7785);
            vacio.setTextSize(13);
            llChat.addView(vacio);
        }
    }

    /** Texto entre dos marcadores (sin incluirlos). Vacío si no se encuentra. */
    private String seccion(String texto, String inicio, String fin) {
        int i = texto.indexOf(inicio);
        if (i < 0) return "";
        int desde = i + inicio.length();
        int hasta = texto.indexOf(fin, desde);
        if (hasta < 0) hasta = texto.length();
        return texto.substring(desde, hasta).trim();
    }

    /** Burbuja de chat dibujada por código (no depende de archivos drawable). */
    private void agregarBurbuja(String texto, boolean mio) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(14);
        t.setTextColor(mio ? Color.WHITE : 0xFF1F2933);

        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(mio ? 0xFF4C8DF6 : 0xFFFFFFFF);
        float grande = dp(18);
        float chico = dp(4);
        if (mio) {
            fondo.setCornerRadii(new float[]{grande, grande, grande, grande,
                    chico, chico, grande, grande});
        } else {
            fondo.setCornerRadii(new float[]{grande, grande, grande, grande,
                    grande, grande, chico, chico});
            fondo.setStroke(dp(1), 0xFFE1E7EE);
        }
        t.setBackground(fondo);

        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setMaxWidth(dp(260));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mio ? Gravity.END : Gravity.START;
        lp.topMargin = dp(6);
        llChat.addView(lp == null ? t : t, lp);
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }
}