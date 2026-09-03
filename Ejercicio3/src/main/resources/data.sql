CREATE TABLE peliculas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    genero VARCHAR(100),
    director VARCHAR(255),
    descripcion VARCHAR(1000),
    afiche_path VARCHAR(500)   
);


CREATE TABLE funciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pelicula_id BIGINT NOT NULL,
    fecha_hora TIMESTAMP NOT NULL,
    asientos_totales INT NOT NULL,
    asientos_ocupados INT DEFAULT 0,
    CONSTRAINT fk_funciones_peliculas FOREIGN KEY (pelicula_id) REFERENCES peliculas(id) ON DELETE CASCADE
);

INSERT INTO peliculas (nombre, genero, director, descripcion, afiche_path) VALUES
('Inception', 'Ciencia Ficcion', 'Christopher Nolan', 'Un ladron que robando secretos corporativos a traves de los suenos es asignado con la tarea de plantar una idea en la mente de un CEO.', NULL),
('The Matrix', 'Ciencia Ficcion', 'Lana Wachowski', 'Un programador descubre que el mundo es una simulacion y se une a la rebellion contra las maquinas.', NULL),
('Interstellar', 'Aventura', 'Christopher Nolan', 'Un grupo de astronautas busca un nuevo hogar para la humanidad a traves de un agujero de gusano.', NULL),
('Parasite', 'Drama', 'Bong Joon-ho', 'La familia Kim, pobre y desempleada, se infiltra gradualmente en la vida de los ricos Park.', NULL),
('Avengers: Endgame', 'Accion', 'Anthony y Joe Russo', 'Los Vengadores restantes deben reunir fuerzas para revertir las acciones de Thanos y restaurar el equilibrio.', NULL);

INSERT INTO funciones (pelicula_id, fecha_hora, asientos_totales, asientos_ocupados) VALUES
(1, '2025-08-15 14:30:00', 100, 87),
(1, '2025-08-15 18:00:00', 100, 62),
(2, '2025-08-15 16:00:00', 120, 110),
(3, '2025-08-16 20:00:00', 100, 45),
(4, '2025-08-16 19:30:00', 80, 78),
(5, '2025-08-17 15:00:00', 150, 140);
