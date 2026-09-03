package com.cinebuscador.model;

import jakarta.persistence.*;

@Entity
@Table(name = "peliculas")
public class Pelicula {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String genero;

    @Column(nullable = false)
    private String director;

    // VULNERABILIDAD: el path se guarda con el nombre original del archivo sin sanitización
    @Column(name = "descripcion", nullable = true)
    private String descripcion;

    @Column(name = "afiche_path", nullable = true)
    private String afichePath;

    public Pelicula() {}

    public Pelicula(String nombre, String genero, String director) {
        this.nombre = nombre;
        this.genero = genero;
        this.director = director;
    }

    // Getters y setters

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getAfichePath() { return afichePath; }
    public void setAfichePath(String afichePath) { this.afichePath = afichePath; }
}
