package com.layla.bot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.layla.bot.data.GeminiRepository;
import com.layla.bot.service.BotState;

public class MainActivity extends AppCompatActivity {
    private TextView salida;
    private BotState state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        state = new BotState(this);
        SharedPreferences prefs = getSharedPreferences("layla_prefs", Context.MODE_PRIVATE);

        salida = findViewById(R.id.salida);
        Button actualizar = findViewById(R.id.btn_actualizar);
        Button copiar = findViewById(R.id.btn_copiar);
        Button ajustes = findViewById(R.id.btn_ajustes);
        Button prueba = findViewById(R.id.btn_prueba);
        Button siguiente = findViewById(R.id.btn_siguiente);
        EditText etKey = findViewById(R.id.et_api_key);
        Button guardarKey = findViewById(R.id.btn_guardar_key);
        Button probarIa = findViewById(R.id.btn_probar_ia);

        actualizar.setOnClickListener(v -> salida.setText(state.informe()));

        copiar.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("layla", state.informe()));
                Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
            }
        });

        ajustes.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

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

        guardarKey.setOnClickListener(v -> {
            String key = etKey.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "Pega primero la clave", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("gemini_key", key).apply();
            etKey.setText("");
            Toast.makeText(this, "Clave guardada", Toast.LENGTH_SHORT).show();
        });

        probarIa.setOnClickListener(v -> {
            String key = prefs.getString("gemini_key", "");
            if (key.isEmpty()) {
                Toast.makeText(this, "Guarda primero la clave", Toast.LENGTH_SHORT).show();
                return;
            }
            salida.setText("Probando Gemini...");
            new Thread(() -> {
                String resultado;
                try {
                    resultado = new GeminiRepository()
                            .generarRespuesta(key, "Saluda en una frase corta en español.");
                } catch (Exception e) {
                    resultado = "Error: " + e.getMessage();
                }
                final String texto = resultado;
                runOnUiThread(() -> salida.setText(texto));
            }).start();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        salida.setText(state.informe());
    }
}