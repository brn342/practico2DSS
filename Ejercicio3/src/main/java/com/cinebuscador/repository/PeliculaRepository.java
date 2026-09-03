package com.cinebuscador.repository;

import com.cinebuscador.model.Pelicula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PeliculaRepository extends JpaRepository<Pelicula, Integer> {
    List<Pelicula> findByNombreContainingIgnoreCase(String nombre);

    @Query(value = """
        SELECT p.id, p.nombre as pelicula, f.fecha_hora,
               (f.asientos_totales - f.asientos_ocupados) as disponibles,
               p.descripcion, p.afiche_path
        FROM funciones f JOIN peliculas p ON f.pelicula_id = p.id
        WHERE :buscar IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
        ORDER BY
          CASE WHEN :ordenarPor = 'fecha_hora' THEN f.fecha_hora END ASC,
          CASE WHEN :ordenarPor = 'nombre' OR :ordenarPor IS NULL OR :ordenarPor = '' THEN p.nombre END ASC
        """, nativeQuery = true)
    List<Object[]> searchWithFunciones(@Param("buscar") String buscar,
                                       @Param("ordenarPor") String ordenarPor);

    @Query(value = """
        SELECT p.id, p.nombre as pelicula, f.fecha_hora,
               (f.asientos_totales - f.asientos_ocupados) as disponibles,
               p.descripcion, p.afiche_path
        FROM funciones f JOIN peliculas p ON f.pelicula_id = p.id
        WHERE :buscar IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
        ORDER BY
          CASE WHEN :ordenarPor = 'fecha_hora' THEN f.fecha_hora END ASC,
          CASE WHEN :ordenarPor = 'nombre' OR :ordenarPor IS NULL OR :ordenarPor = '' THEN p.nombre END DESC
        """, nativeQuery = true)
    List<Object[]> searchWithFuncionesDesc(@Param("buscar") String buscar,
                                           @Param("ordenarPor") String ordenarPor);
}
