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

public class CloudflareRepository {

    private static final String TAG = "CloudflareRepo";
    private static final String BASE =
            "https://api.cloudflare.com/client/v4/accounts/";
    // Se prueban en este orden. El 70B responde mejor en español; el 8B gasta menos cupo.
    private static final String[] MODELOS = {
            "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
            "@cf/meta/llama-3.1-8b-instruct"
    };
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Instrucción fija para que el modelo devuelva solo el texto a enviar. */
    private static final String INSTRUCCION =
            "Responde únicamente con el texto del mensaje que se enviará al chat: "
            + "una o dos frases cortas, sin comillas, sin markdown, sin bloques de código, "
            + "sin explicaciones y sin repetir el mensaje.";

    private static final int RONDAS = 2;
    private static final long ESPERA_ENTRE_RONDAS_MS = 3000;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build();

    /** Error HTTP con su código, para decidir si se prueba otro modelo. */
    private static class ErrorHttp extends IOException {
        final int codigo;

        ErrorHttp(int codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        /** Saturado, error temporal, cupo agotado o modelo inexistente: probar otro. */
        boolean probarOtro() {
            return codigo == 503 || codigo == 500 || codigo == 502
                    || codigo == 429 || codigo == 404;
        }
    }

    /** Llamar desde un hilo secundario (usa pausas entre rondas). */
    public String generarRespuesta(String accountId, String token, String prompt)
            throws IOException {
        IOException ultimoError = null;

        for (int ronda = 1; ronda <= RONDAS; ronda++) {
            for (String modelo : MODELOS) {
                try {
                    return llamar(modelo, accountId, token, prompt);
                } catch (ErrorHttp e) {
                    ultimoError = e;
                    Log.w(TAG, modelo + " falló con HTTP " + e.codigo);
                    if (!e.probarOtro()) throw e; // token malo, sin permiso, etc.
                } catch (InterruptedIOException e) {
                    ultimoError = new IOException(
                            "Sin respuesta de Cloudflare a tiempo (conexión lenta)", e);
                    Log.w(TAG, modelo + " falló por timeout");
                }
            }
            if (ronda < RONDAS) {
                try {
                    Thread.sleep(ESPERA_ENTRE_RONDAS_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Cancelado", ie);
                }
            }
        }
        throw new IOException("Cloudflare no respondió con ningún modelo: "
                + (ultimoError != null ? ultimoError.getMessage() : ""), ultimoError);
    }

    private String llamar(String modelo, String accountId, String token, String prompt)
            throws IOException {
        String cuerpo;
        try {
            JSONArray mensajes = new JSONArray()
                    .put(new JSONObject().put("role", "system").put("content", INSTRUCCION))
                    .put(new JSONObject().put("role", "user").put("content", prompt));
            cuerpo = new JSONObject()
                    .put("messages", mensajes)
                    .put("max_tokens", 150)
                    .put("temperature", 0.8)
                    .toString();
        } catch (JSONException e) {
            throw new IOException("Error construyendo JSON", e);
        }

        Request request = new Request.Builder()
                .url(BASE + accountId + "/ai/run/" + modelo)
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(cuerpo, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                Log.e(TAG, modelo + " HTTP " + response.code() + ": " + raw);
                throw new ErrorHttp(response.code(),
                        modelo + " -> HTTP " + response.code() + " " + raw);
            }
            try {
                JSONObject result = new JSONObject(raw).optJSONObject("result");
                if (result == null) {
                    throw new IOException("Respuesta inesperada: " + raw);
                }
                String texto = result.isNull("response") ? "" : result.optString("response", "");
                if (texto.trim().isEmpty()) {
                    // Formato alternativo (estilo OpenAI) que usan algunos modelos
                    JSONArray choices = result.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        texto = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .optString("content", "");
                    }
                }
                if (texto.trim().isEmpty()) {
                    throw new IOException("Respuesta vacía: " + raw);
                }
                return texto;
            } catch (JSONException e) {
                throw new IOException("Respuesta inesperada: " + raw, e);
            }
        }
    }
}