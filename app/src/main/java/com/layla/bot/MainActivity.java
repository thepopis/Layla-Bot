package com.layla.bot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView salida;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        salida = findViewById(R.id.salida);
        Button actualizar = findViewById(R.id.btn_actualizar);
        Button copiar = findViewById(R.id.btn_copiar);
        Button ajustes = findViewById(R.id.btn_ajustes);
        Button prueba = findViewById(R.id.btn_prueba);
        Button siguiente = findViewById(R.id.btn_siguiente);

        actualizar.setOnClickListener(v -> salida.setText(LaylaAccessibilityService.informe()));

        copiar.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("layla", LaylaAccessibilityService.informe()));
                Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
            }
        });

        ajustes.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        prueba.setOnClickListener(v -> {
            LaylaAccessibilityService.programarPruebaEscritura();
            Toast.makeText(this, "Ve a Layla y abre un chat. Escribo en 6 segundos.",
                    Toast.LENGTH_LONG).show();
        });

        siguiente.setOnClickListener(v -> {
            LaylaAccessibilityService.programarPruebaSiguiente();
            Toast.makeText(this, "Ve a Layla y abre un chat. Pulso en 6 segundos.",
                    Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        salida.setText(LaylaAccessibilityService.informe());
    }
}