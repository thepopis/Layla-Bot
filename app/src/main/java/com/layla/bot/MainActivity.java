package com.layla.bot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.layla.bot.data.IaRepository;
import com.layla.bot.service.BotState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView salida, aviso, tvServicio, tvEstado, tvUltimo,
            tvResultadoIa, tvIaEstado;
    private LinearLayout llChat;
    private BotState state;
    private SharedPreferences prefs;

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

        prueba.setOnClickListener(v -> {
            LaylaAccessibilityService.programarPruebaEscritura();
            Toast.makeText(this, "Ve a Layla y abre un chat. Escribo en 6 segundos.",
                    Toast.LENGTH_LONG).show();
        });

        siguiente.setOnClickListener(v -> {
            LaylaAccessibilityService.programarPruebaSiguienteChat();
            Toast.makeText(this, "Ve a Layla y abre un chat. Salto en 6 segundos.",
                    Toast.LENGTH_LONG).show();
        });

        detalles.setOnClickListener(v -> {
            boolean visible = salida.getVisibility() == View.VISIBLE;
            salida.setVisibility(visible ? View.GONE : View.VISIBLE);
            detalles.setText(visible ? "DETALLES TÉCNICOS  ▾" : "DETALLES TÉCNICOS  ▴");
        });

        guardarKey.setOnClickListener(v -> guardarClaves(etCfAccount, etCfToken, etKey));
        probarIa.setOnClickListener(v -> probarIa());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mostrarInforme(state.informe());
        actualizarEstadoIa();
    }

    // ---------- Guardado de claves ----------
    private void guardarClaves(EditText etCuenta, EditText etToken, EditText etKey) {
        String cuenta = etCuenta.getText().toString().trim();
        String token  = etToken.getText().toString().trim();
        String gemini = etKey.getText().toString().trim();
        if (cuenta.isEmpty() && token.isEmpty() && gemini.isEmpty()) {
            Toast.makeText(this, "Pega primero algún dato", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        if (!cuenta.isEmpty()) ed.putString("cf_account", cuenta);
        if (!token.isEmpty())  ed.putString("cf_token", token);
        if (!gemini.isEmpty()) ed.putString("gemini_key", gemini);
        ed.apply();
        etCuenta.setText("");
        etToken.setText("");
        etKey.setText("");
        actualizarEstadoIa();
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
    }

    // ---------- Prueba de IA ----------
    private void probarIa() {
        String cuenta = prefs.getString("cf_account", "");
        String token  = prefs.getString("cf_token", "");
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
    }

    // ---------- Estado de claves ----------
    private void actualizarEstadoIa() {
        boolean cf = !prefs.getString("cf_account", "").isEmpty()
                && !prefs.getString("cf_token", "").isEmpty();
        boolean gem = !prefs.getString("gemini_key", "").isEmpty();
        tvIaEstado.setText("Cloudflare: " + (cf ? "guardado ✓" : "falta")
                + "   ·   Gemini: " + (gem ? "guardado ✓" : "no (opcional)"));
    }

    // ---------- Informe / chat ----------
    private void mostrarInforme(String informe) {
        if (informe == null) informe = "";
        salida.setText(informe);

        boolean inactivo = informe.contains("no está activo")
                || informe.contains("Servicio desconectado");
        aviso.setVisibility(inactivo ? View.VISIBLE : View.GONE);
        tvServicio.setText(inactivo ? "Servicio desconectado" : "Servicio conectado");
        tvServicio.setTextColor(inactivo ? 0xFFC0392B : 0xFF1E8E5A);

        tvEstado.setText(valorSeccion(informe, "ESTADO:", "ÚLTIMO MENSAJE ENTRANTE:"));
        tvUltimo.setText(valorSeccion(informe, "ÚLTIMO MENSAJE ENTRANTE:", "CONVERSACIÓN VISIBLE:"));
        String conversacion = seccion(informe, "CONVERSACIÓN VISIBLE:", "--- PANTALLA ---");

        llChat.removeAllViews();
        boolean hay = false;
        for (String linea : conversacion.split("\n")) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;
            if (linea.startsWith("YO:")) { agregarBurbuja(linea.substring(3).trim(), true);  hay = true; }
            else if (linea.startsWith("EL:")) { agregarBurbuja(linea.substring(2).trim(), false); hay = true; }
            else if (linea.startsWith("?:"))  { agregarBurbuja(linea.substring(2).trim(), false); hay = true; }
        }
        if (!hay) {
            TextView vacio = new TextView(this);
            vacio.setText("Sin mensajes visibles");
            vacio.setTextColor(0xFF6B7785);
            vacio.setTextSize(13);
            llChat.addView(vacio);
        }
    }

    private String valorSeccion(String t, String ini, String fin) {
        String s = seccion(t, ini, fin);
        return s.isEmpty() ? "—" : s;
    }

    private String seccion(String texto, String inicio, String fin) {
        int i = texto.indexOf(inicio);
        if (i < 0) return "";
        int desde = i + inicio.length();
        int hasta = texto.indexOf(fin, desde);
        if (hasta < 0) hasta = texto.length();
        return texto.substring(desde, hasta).trim();
    }

    /** Burbuja sin depender de drawables: usa GradientDrawable en código. */
    private void agregarBurbuja(String texto, boolean mio) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(14);
        t.setTextColor(mio ? Color.WHITE : 0xFF1F2933);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setMaxWidth(dp(260));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(mio ? 0xFF2A6FB0 : 0xFFF1F3F6);
        if (!mio) bg.setStroke(dp(1), 0xFFD9DEE5);
        t.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mio ? Gravity.END : Gravity.START;
        lp.topMargin = dp(6);
        llChat.addView(t, lp);
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }
}