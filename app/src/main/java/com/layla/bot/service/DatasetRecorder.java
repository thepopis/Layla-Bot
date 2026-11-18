package com.layla.bot.service;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DatasetRecorder {

    private static final String TAG = "DatasetRecorder";
    private static final String NOMBRE_ARCHIVO = "dataset_layla.jsonl";

    private final File archivo;

    public DatasetRecorder(Context context) {
        this.archivo = new File(context.getFilesDir(), NOMBRE_ARCHIVO);
    }

    public synchronized void guardar(String mensajeEntrante, String respuestaEnviada,
                                     String nombreUsuario, String proveedor) {
        if (mensajeEntrante == null || mensajeEntrante.trim().isEmpty()) return;
        if (respuestaEnviada == null || respuestaEnviada.trim().isEmpty()) return;

        try (FileWriter fw = new FileWriter(archivo, true)) {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()));
            obj.put("usuario", nombreUsuario != null ? nombreUsuario : "");
            obj.put("proveedor", proveedor != null ? proveedor : "");
            obj.put("entrante", mensajeEntrante.trim());
            obj.put("respuesta", respuestaEnviada.trim());
            fw.write(obj.toString());
            fw.write("\n");
            Log.d(TAG, "Par guardado. Total líneas: " + contarLineas());
        } catch (Exception e) {
            Log.e(TAG, "Error guardando par", e);
        }
    }

    public int contarLineas() {
        if (!archivo.exists()) return 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader(archivo))) {
            int n = 0;
            while (br.readLine() != null) n++;
            return n;
        } catch (IOException e) {
            return 0;
        }
    }

    public File getArchivo() {
        return archivo;
    }
}