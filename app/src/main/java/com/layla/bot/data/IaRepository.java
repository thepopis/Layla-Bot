package com.layla.bot.data;

import java.io.IOException;

public class IaRepository {

    private final CloudflareRepository cloudflare = new CloudflareRepository();
    private final GeminiRepository gemini = new GeminiRepository();
    private volatile String ultimoProveedor = "";

    /** Nombre del proveedor que respondió la última vez. */
    public String getUltimoProveedor() {
        return ultimoProveedor;
    }

    /** Llamar desde un hilo secundario. */
    public String generar(String cfAccount, String cfToken, String geminiKey, String prompt)
            throws IOException {
        boolean hayCloudflare = !vacio(cfAccount) && !vacio(cfToken);
        boolean hayGemini = !vacio(geminiKey);
        if (!hayCloudflare && !hayGemini) {
            throw new IOException("No hay ninguna clave guardada");
        }

        StringBuilder errores = new StringBuilder();

        if (hayCloudflare) {
            try {
                String r = cloudflare.generarRespuesta(cfAccount.trim(), cfToken.trim(), prompt);
                ultimoProveedor = "Cloudflare";
                return r;
            } catch (IOException e) {
                errores.append("Cloudflare: ").append(e.getMessage());
            }
        }

        if (hayGemini) {
            try {
                String r = gemini.generarRespuesta(geminiKey.trim(), prompt);
                ultimoProveedor = "Gemini";
                return r;
            } catch (IOException e) {
                if (errores.length() > 0) errores.append("\n\n");
                errores.append("Gemini: ").append(e.getMessage());
            }
        }

        throw new IOException(errores.toString());
    }

    private boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}