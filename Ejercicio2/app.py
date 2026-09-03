from flask import Flask, render_template, request, g, redirect, url_for
import sqlite3
from config import Config

app = Flask(__name__)
app.config.from_object(Config)


def get_db():
    if 'db' not in g:
        g.db = sqlite3.connect(app.config['DATABASE'])
        g.db.row_factory = sqlite3.Row
    return g.db


@app.teardown_appcontext
def close_db(exception):
    db = g.pop('db', None)
    if db is not None:
        db.close()


def buscar_funciones(query, sort_by='nombre', sort_dir='ASC'):
    db = get_db()
    sql = (
        f"SELECT peliculas.nombre as pelicula, funciones.fecha_hora, "
        f"(funciones.asientos_totales - funciones.asientos_ocupados) as disponibles, "
        f"peliculas.descripcion as descripcion, peliculas.id as id "
        f"FROM funciones "
        f"JOIN peliculas ON funciones.pelicula_id = peliculas.id "
        f"WHERE peliculas.nombre LIKE '%{query}%' "
        f"ORDER BY {'peliculas.nombre' if sort_by == 'nombre' else 'funciones.fecha_hora'} "
        f"{sort_dir}"
    )
    return db.execute(sql).fetchall()


@app.route('/')
def index():
    query = request.args.get('buscar', '')
    sort_by = request.args.get('ordenar_por', 'nombre')
    sort_dir = request.args.get('sentido', 'ASC')
    resultados = []
    if query:
        resultados = buscar_funciones(query, sort_by, sort_dir)
    return render_template(
        'index.html',
        resultados=resultados,
        query=query,
        sort_by=sort_by,
        sort_dir=sort_dir,
    )


@app.route('/edit/<int:pelicula_id>', methods=['GET'])
def edit_get(pelicula_id):
    db = get_db()
    pelicula = db.execute(
        'SELECT * FROM peliculas WHERE id = ?', (pelicula_id,)
    ).fetchone()
    return render_template('edit.html', pelicula=pelicula)


@app.route('/edit/<int:pelicula_id>', methods=['POST'])
def edit_post(pelicula_id):
    nombre = request.form.get('nombre', '')
    genero = request.form.get('genero', '')
    director = request.form.get('director', '')
    descripcion = request.form.get('descripcion', '')

    db = get_db()
    db.execute(
        '''UPDATE peliculas
           SET nombre=?, genero=?, director=?, descripcion=?
           WHERE id=?''',
        (nombre, genero, director, descripcion, pelicula_id),
    )
    db.commit()
    return redirect(url_for('index'))


if __name__ == '__main__':
    import os
    host=os.environ.get('FLASK_HOST', '0.0.0.0')
    debug = os.environ.get('DEBUG_MODE', 'true').lower() in ('true', '1', 'yes')
    app.run(debug=debug,host=host)
