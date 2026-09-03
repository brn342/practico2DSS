package com.cinebuscador.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "funciones")
public class Funcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "pelicula_id", nullable = false)
    private Integer peliculaId;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "asientos_totales", nullable = false)
    private Integer asientosTotales;

    @Column(name = "asientos_ocupados", nullable = false)
    private Integer asientosOcupados;

    public Funcion() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPeliculaId() { return peliculaId; }
    public void setPeliculaId(Integer peliculaId) { this.peliculaId = peliculaId; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public Integer getAsientosTotales() { return asientosTotales; }
    public void setAsientosTotales(Integer asientosTotales) { this.asientosTotales = asientosTotales; }

    public Integer getAsientosOcupados() { return asientosOcupados; }
    public void setAsientosOcupados(Integer asientosOcupados) { this.asientosOcupados = asientosOcupados; }


    public Integer getDisponibles() {
        return asientosTotales - asientosOcupados;
    }

    @Column(name = "nombre_funcion", nullable = true)
    private String nombreFuncion;

    public String getNombreFuncion() { return nombreFuncion; }
    public void setNombreFuncion(String nombreFuncion) { this.nombreFuncion = nombreFuncion; }
}
