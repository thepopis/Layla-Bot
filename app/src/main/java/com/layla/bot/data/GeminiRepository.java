package com.layla.bot.data;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build();

    public String generarRespuesta(String apiKey, String prompt) throws IOException {
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
                throw new IOException("HTTP " + response.code() + " " + raw);
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