package com.layla.bot.data;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiRepository {

    private static final String TAG = "GeminiRepo";
    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    /**
     * @param apiKey        Clave de Gemini
     * @param systemPrompt  El system prompt (rol de Layla)
     * @param mensaje       El mensaje del usuario (historial + último mensaje)
     */
    public String generarRespuesta(String apiKey, String systemPrompt, String mensaje)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            // 1. systemInstruction: el rol de Layla
            JSONObject sysInstr = new JSONObject();
            JSONArray sysParts = new JSONArray();
            sysParts.put(new JSONObject().put("text", systemPrompt));
            sysInstr.put("parts", sysParts);
            body.put("systemInstruction", sysInstr);

            // 2. contents: el mensaje del usuario
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("role", "user");
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", mensaje));
            content.put("parts", parts);
            contents.put(content);
            body.put("contents", contents);

            // 3. Configuración de generación
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.9);
            genConfig.put("maxOutputTokens", 200);
            body.put("generationConfig", genConfig);

        } catch (Exception e) {
            throw new IOException("Error construyendo JSON", e);
        }

        Request request = new Request.Builder()
                .url(URL)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "HTTP " + response.code() + ": " + err);
                throw new IOException("HTTP " + response.code() + ": " + err);
            }
            String raw = response.body() != null ? response.body().string() : "";
            JSONObject json = new JSONObject(raw);
            String texto = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");
            return texto.trim();
        } catch (Exception e) {
            throw new IOException("Error parseando respuesta", e);
        }
    }
}