package com.layla.bot.domain.model;

public class Mensaje {
    public enum Autor { YO, ELLOS, DESCONOCIDO }

    private final Autor autor;
    private final String texto;

    public Mensaje(Autor autor, String texto) {
        this.autor = autor;
        this.texto = texto;
    }

    public Autor getAutor() { return autor; }
    public String getTexto() { return texto; }

    public boolean esEntrante() {
        return autor == Autor.ELLOS;
    }
}