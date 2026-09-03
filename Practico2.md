# Práctico 2 — Mitigación de Vulnerabilidades de CWE

Repositorio base: https://github.com/ucudal/2026-Desarrollo-Seguro
Rama de mitigaciones: `practico-2` (la rama `main` no se modifica).

| # | Ejercicio | Vulnerabilidad | CWE |
|---|-----------|----------------|-----|
| 1 | CineBuscador (Flask) | Inyección SQL | CWE-89 |
| 2 | CineBuscador + edición (Flask) | Cross-Site Scripting almacenado | CWE-79 |
| 3 | CineBuscador afiches (Spring Boot) | Carga de archivos sin restricción + Path Traversal | CWE-434 / CWE-22 |
| 4 | CineBuscador funciones (Spring Boot) | Server-Side Template Injection (SpEL) → RCE | CWE-94 / CWE-917 |
| 5 | Login CineBuscador (Spring Boot) | Almacenamiento inseguro de credenciales | CWE-312 / CWE-321 / CWE-327 |

Cada sección incluye: descripción del hallazgo, PoC paso a paso, la mitigación aplicada y la verificación (antes/después).

---

## Ejercicio 1 — Inyección SQL (CWE-89)

### 1.1 Hallazgo

Archivo: `Ejercicio1/app.py`, función `buscar_funciones()`.

El SQL se arma por interpolación de strings con los parámetros de la request:

```python
sql = f"...WHERE peliculas.nombre LIKE '%{query}%' " \
      f"ORDER BY {'peliculas.nombre' if sort_by == 'nombre' else 'funciones.fecha_hora'} " \
      f"{sort_dir}"
return db.execute(sql).fetchall()
```

Puntos de inyección:

- **`buscar`** (`query`): se concatena dentro de `LIKE '%...%'`. Permite cerrar la
  comilla y añadir `UNION SELECT ...` para leer cualquier tabla/metadato de la base.
- **`sentido`** (`sort_dir`): se concatena crudo al final del `ORDER BY`. Permite
  inyección en la cláusula `ORDER BY` (oráculo booleano / time-based).

`sqlite3.Connection.execute()` ejecuta una sola sentencia (no hay *stacked queries*),
pero `UNION SELECT` y la inyección en `ORDER BY` son suficientes para exfiltrar datos.

### 1.2 PoC

Requisitos: `cd Ejercicio1 && python init_db.py && python app.py`
(app en `http://127.0.0.1:5000`; en macOS el puerto 5000 lo ocupa AirPlay: usar
otro puerto, p. ej. `flask --app app run -p 5051`).

**PoC A — Exfiltrar toda la tabla `peliculas` con UNION**

Ingresar en el buscador (parámetro `buscar`):

```
zzz%' UNION SELECT group_concat(nombre||' / '||director,' | '), '', 1 FROM peliculas -- 
```

Petición equivalente:

```
GET /?buscar=zzz%25'%20UNION%20SELECT%20group_concat(nombre||'%20/%20'||director,'%20|%20'),%20'',%201%20FROM%20peliculas%20--%20
```

SQL resultante:

```sql
... WHERE peliculas.nombre LIKE '%zzz%' UNION SELECT group_concat(nombre||' / '||director,' | '), '', 1 FROM peliculas -- %' ORDER BY ...
```

Resultado: la primera columna de la tabla HTML muestra todos los directores:
`Dune: Parte Dos / Denis Villeneuve | Oppenheimer / Christopher Nolan | ...`

**PoC B — Enumerar el esquema (`sqlite_master`)**

```
zzz%' UNION SELECT name, sql, 1 FROM sqlite_master WHERE type='table' -- 
```

Resultado: `funciones`, `peliculas`, `sqlite_sequence` (más su DDL en la 2ª columna).
Con `sqlite_version()` se obtiene además la versión del motor (`3.50.4`).

**PoC C — Inyección en `ORDER BY` vía `sentido` (oráculo time-based)**

```
GET /?buscar=a&sentido=ASC, CASE WHEN (SELECT COUNT(*) FROM peliculas)=10
       THEN (SELECT count(*) FROM peliculas t1,peliculas t2,peliculas t3,
             peliculas t4,peliculas t5,peliculas t6,peliculas t7)
       ELSE 1 END
```

Con la condición verdadera el producto cartesiano dispara ~90 ms de latencia;
con `=999` (falsa) responde en ~1 ms. Es un oráculo booleano para extraer datos
bit a bit sin ver la salida.

### 1.3 Mitigación

- El término de búsqueda se pasa como **parámetro ligado** (`?`), nunca concatenado;
  el patrón `%...%` se arma sobre el valor del bind, no sobre el texto SQL.
- `ordenar_por` y `sentido` **no se interpolan**: se usan solo como clave para elegir
  un fragmento fijo desde una *allowlist* (`_COLUMNAS_ORDEN` y `'ASC'/'DESC'`).
  Cualquier valor fuera de la lista cae al valor por defecto.

```python
_COLUMNAS_ORDEN = {'nombre': 'peliculas.nombre', 'fecha': 'funciones.fecha_hora'}

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
```

### 1.4 Verificación

| Caso | Antes | Después |
|------|-------|---------|
| Búsqueda normal `buscar=Dune` | OK | OK (sin cambios) |
| Orden `nombre`/`fecha`, `ASC`/`DESC` | OK | OK (sin cambios) |
| PoC A (UNION directores) | Exfiltra la tabla | "No se encontraron funciones" (se busca literal) |
| PoC B (`sqlite_master`) | Lista tablas y DDL | "No se encontraron funciones" |
| PoC C (`sentido` time-based) | ~90 ms vs ~1 ms (oráculo) | ~1 ms constante, sin oráculo |
| `buscar=O'Brien` (comilla literal) | 500 / error SQL | HTTP 200, resultado vacío correcto |

Sin errores `OperationalError` en el log tras el fix.
