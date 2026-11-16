package com.layla.bot.data;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiRepository {

    private static final String TAG = "GeminiRepo";
    // Si Google lo retira otra vez, el error te dirá el nombre nuevo. Cámbialo aquí.
    private static final String MODELO = "gemini-3.8-flash";
    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODELO + ":generateContent";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final int MAX_INTENTOS = 3;
    private static final long ESPERA_BASE_MS = 3000;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build();

    /** Error HTTP con su código, para decidir si vale la pena reintentar. */
    private static class ErrorHttp extends IOException {
        final int codigo;

        ErrorHttp(int codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        boolean reintentable() {
            return codigo == 503 || codigo == 500 || codigo == 429;
        }
    }

    /** Llamar desde un hilo secundario (usa pausas entre reintentos). */
    public String generarRespuesta(String apiKey, String prompt) throws IOException {
        IOException ultimoError = null;

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return llamar(apiKey, prompt);
            } catch (ErrorHttp e) {
                ultimoError = e;
                if (!e.reintentable()) throw e;
                Log.w(TAG, "Intento " + intento + " falló con HTTP " + e.codigo);
            } catch (InterruptedIOException e) {
                ultimoError = new IOException(
                        "Sin respuesta de Gemini a tiempo (conexión lenta o modelo ocupado)", e);
                Log.w(TAG, "Intento " + intento + " falló por timeout");
            }

            if (intento < MAX_INTENTOS) {
                try {
                    Thread.sleep(ESPERA_BASE_MS * intento);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Cancelado", ie);
                }
            }
        }
        throw new IOException("Gemini falló tras " + MAX_INTENTOS + " intentos: "
                + (ultimoError != null ? ultimoError.getMessage() : ""), ultimoError);
    }

    private String llamar(String apiKey, String prompt) throws IOException {
        String cuerpo;
        try {
            JSONObject part = new JSONObject().put("text", prompt);
            JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
            cuerpo = new JSONObject().put("contents", new JSONArray().put(content)).toString();
        } catch (JSONException e) {
            throw new IOException("Error construyendo JSON", e);
        }

        Request request = new Request.Builder()
                .url(URL)
                .addHeader("x-goog-api-key", apiKey)
                .post(RequestBody.create(cuerpo, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                Log.e(TAG, "HTTP " + response.code() + ": " + raw);
                throw new ErrorHttp(response.code(), "HTTP " + response.code() + " " + raw);
            }
            try {
                return new JSONObject(raw)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");
            } catch (JSONException e) {
                throw new IOException("Respuesta inesperada: " + raw, e);
            }
        }
    }
}