from flask import Flask, render_template, request, g
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


# Mitigacion SQLi (CWE-89):
#  - El termino de busqueda viaja como parametro ligado (?), nunca concatenado.
#  - sort_by / sort_dir NO se interpolan: se usan solo para elegir un fragmento
#    fijo desde una allowlist. La entrada del usuario jamas llega al texto SQL.
_COLUMNAS_ORDEN = {
    'nombre': 'peliculas.nombre',
    'fecha': 'funciones.fecha_hora',
}


def buscar_funciones(query, sort_by='nombre', sort_dir='ASC'):
    db = get_db()
    orden_columna = _COLUMNAS_ORDEN.get(sort_by, 'peliculas.nombre')
    orden_sentido = 'DESC' if str(sort_dir).upper() == 'DESC' else 'ASC'
    sql = (
        "SELECT peliculas.nombre as pelicula, funciones.fecha_hora, "
        "(funciones.asientos_totales - funciones.asientos_ocupados) as disponibles "
        "FROM funciones "
        "JOIN peliculas ON funciones.pelicula_id = peliculas.id "
        "WHERE peliculas.nombre LIKE ? "
        f"ORDER BY {orden_columna} {orden_sentido}"
    )
    return db.execute(sql, (f"%{query}%",)).fetchall()


@app.route('/')
def index():
    query = request.args.get('buscar', '')
    sort_by = request.args.get('ordenar_por', 'nombre')
    sort_dir = request.args.get('sentido', 'ASC')
    resultados = []
    if query:
        resultados = buscar_funciones(query, sort_by, sort_dir)
    return render_template('index.html',
                           resultados=resultados,
                           query=query,
                           sort_by=sort_by,
                           sort_dir=sort_dir)


if __name__ == '__main__':
    import os
    host=os.environ.get('FLASK_HOST', '0.0.0.0')
    debug = os.environ.get('DEBUG_MODE', 'true').lower() in ('true', '1', 'yes')
    app.run(debug=debug,host=host)
