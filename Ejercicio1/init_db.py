import sqlite3
import os

def init_db():
    db_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cine.db')
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    cursor.executescript('''
        DROP TABLE IF EXISTS funciones;
        DROP TABLE IF EXISTS peliculas;

        CREATE TABLE peliculas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT NOT NULL,
            genero TEXT NOT NULL,
            director TEXT NOT NULL
        );

        CREATE TABLE funciones (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pelicula_id INTEGER NOT NULL,
            fecha_hora TEXT NOT NULL,
            asientos_totales INTEGER NOT NULL DEFAULT 100,
            asientos_ocupados INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (pelicula_id) REFERENCES peliculas(id)
        );
    ''')

    cursor.executemany(
        'INSERT INTO peliculas (nombre, genero, director) VALUES (?, ?, ?)',
        [
            ('Dune: Parte Dos', 'Ciencia Ficción', 'Denis Villeneuve'),
            ('Oppenheimer', 'Drama/Biografía', 'Christopher Nolan'),
            ('Spider-Man: Across the Spider-Verse', 'Animación/Acción', 'Joaquim Dos Santos'),
            ('La Sociedad de la Nieve', 'Drama/Supervivencia', 'J.A. Bayona'),
            ('Poor Things', 'Comedia/Drama', 'Yorgos Lanthimos'),
            ('Wonka', 'Fantasía/Musical', 'Paul King'),
            ('Kung Fu Panda 4', 'Animación/Acción', 'Mike Mitchell'),
            ('Godzilla x Kong: El Nuevo Imperio', 'Acción/Ciencia Ficción', 'Adam Wingard'),
            ('Pobres Criaturas', 'Drama/Fantasía', 'Yorgos Lanthimos'),
            ('Transformers One', 'Animación/Acción', 'Josh Cooley'),
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
        (3, '2026-07-15 12:00:00', 90, 55),
        (4, '2026-07-13 21:30:00', 80, 78),
        (4, '2026-07-16 19:00:00', 80, 20),
        (5, '2026-07-14 16:00:00', 80, 40),
        (5, '2026-07-15 22:00:00', 80, 65),
        (6, '2026-07-13 11:00:00', 100, 95),
        (6, '2026-07-14 14:00:00', 100, 70),
        (7, '2026-07-13 10:00:00', 80, 50),
        (7, '2026-07-14 13:00:00', 80, 75),
        (7, '2026-07-15 16:00:00', 80, 10),
        (8, '2026-07-14 22:00:00', 120, 100),
        (8, '2026-07-15 20:00:00', 120, 30),
        (10, '2026-07-13 09:00:00', 90, 88),
        (10, '2026-07-15 11:00:00', 90, 40),
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
