package com.layla.bot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

        actualizar.setOnClickListener(v -> {
            LaylaAccessibilityService.requestRefresh();
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> salida.setText(LaylaAccessibilityService.lastDump), 600);
        });

        copiar.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("layla", LaylaAccessibilityService.lastDump));
                Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
            }
        });

        ajustes.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        salida.setText(LaylaAccessibilityService.lastDump);
    }
}