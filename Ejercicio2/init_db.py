import sqlite3
import os

def init_db():
    db_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cine2.db')
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    cursor.executescript('''
        DROP TABLE IF EXISTS funciones;
        DROP TABLE IF EXISTS peliculas;

        CREATE TABLE peliculas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT NOT NULL,
            genero TEXT NOT NULL,
            director TEXT NOT NULL,
            descripcion TEXT
        );

        CREATE TABLE funciones (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pelicula_id INTEGER NOT NULL,
            fecha_hora TEXT NOT NULL,
            asientos_totales INTEGER NOT NULL DEFAULT 100,
            asientos_ocupados INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(pelicula_id) REFERENCES peliculas(id)
        );
    ''')

    cursor.executemany(
        'INSERT INTO peliculas (nombre, genero, director, descripcion) VALUES (?, ?, ?, ?)',
        [
            ('Dune: Parte Dos', 'Ciencia Ficción', 'Denis Villeneuve', 'Paul Atreides se une a los Fremen en una ruta de venganza contra aquellos que destruyeron su familia.'),
            ('Oppenheimer', 'Drama/Biografía', 'Christopher Nolan', 'La historia del físico J. Robert Oppenheimer y su papel en el desarrollo de la bomba atómica.'),
            ('Spider-Man: Across the Spider-Verse', 'Animación/Acción', 'Joaquim Dos Santos', 'Miles Morales viaja a través del multiverso y se enfrenta a un nuevo villano poderoso.'),
            ('La Sociedad de la Nieve', 'Drama/Supervivencia', 'J.A. Bayona', 'El relato real del grupo de supervivientes del accidente aéreo en los Andes de 1972.'),
            ('Poor Things', 'Comedia/Drama', 'Yorgos Lanthimos', 'Bella Baxter, una joven traída de vuelta a la vida por un científico brillante, busca el mundo y su propia libertad.'),
        ]
    )

    funciones = [
        (1, '2026-07-13 14:30:00', 120, 85),
        (1, '2026-07-14 18:00:00', 120, 60),
        (1, '2026-07-15 21:00:00', 120, 110),
        (2, '2026-07-13 16:00:00', 100, 92),
        (2, '2026-07-14 20:30:00', 100, 45),
        (3, '2026-07-13 15:00:00', 90, 30),
        (3, '2026-07-14 17:30:00', 90, 88),
        (4, '2026-07-13 21:30:00', 80, 78),
        (4, '2026-07-16 19:00:00', 80, 20),
        (5, '2026-07-14 16:00:00', 80, 40),
        (5, '2026-07-15 22:00:00', 80, 65),
    ]

    cursor.executemany(
        'INSERT INTO funciones (pelicula_id, fecha_hora, asientos_totales, asientos_ocupados) VALUES (?, ?, ?, ?)',
        funciones
    )

    conn.commit()
    conn.close()
    print(f'Base de datos creada en: {db_path}')


if __name__ == '__main__':
    init_db()
