CREATE TABLE funciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pelicula_id BIGINT NOT NULL,
    fecha_hora TIMESTAMP NOT NULL,
    asientos_totales INT NOT NULL,
    asientos_ocupados INT DEFAULT 0,
    nombre_funcion VARCHAR(255)
);


INSERT INTO funciones (pelicula_id, fecha_hora, asientos_totales, asientos_ocupados, nombre_funcion) VALUES
(1, '2025-08-15 14:30:00', 100, 87,'Inception'),
(1, '2025-08-15 18:00:00', 100, 62,'Inception'),
(2, '2025-08-15 16:00:00', 120, 110,'The Matrix'),
(3, '2025-08-16 20:00:00', 100, 45,'The Matrix'),
(4, '2025-08-16 19:30:00', 80, 78,'Avengers: Endgame'),
(5, '2025-08-17 15:00:00', 150, 140,'Avengers: Endgame');
