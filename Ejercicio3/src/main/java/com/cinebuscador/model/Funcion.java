package com.cinebuscador.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "funciones")
public class Funcion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pelicula_id", nullable = false)
    private Pelicula pelicula;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "asientos_totales")
    private Integer asientosTotales = 100;

    @Column(name = "asientos_ocupados")
    private Integer asientosOcupados = 0;

    public Funcion() {}

    public Funcion(Pelicula pelicula, LocalDateTime fechaHora) {
        this.pelicula = pelicula;
        this.fechaHora = fechaHora;
    }

    // Getters y setters

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Pelicula getPelicula() { return pelicula; }
    public void setPelicula(Pelicula pelicula) { this.pelicula = pelicula; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public Integer getAsientosTotales() { return asientosTotales; }
    public void setAsientosTotales(Integer asientosTotales) { this.asientosTotales = asientosTotales; }

    public Integer getAsientosOcupados() { return asientosOcupados; }
    public void setAsientosOcupados(Integer asientosOcupados) { this.asientosOcupados = asientosOcupados; }

    public int getDisponibles() {
        return (asientosTotales != null ? asientosTotales : 0)
             - (asientosOcupados != null ? asientosOcupados : 0);
    }
}
