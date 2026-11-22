# Intérprete de Lua

Implementación de un intérprete para el lenguaje de programación Lua usando
ANTRL. Trabajo para la materia de lenguajes de programación 2026-2 Universidad
Nacional de Colombia.

## Integrantes

- Martin Moreno Jara - <mamorenoj@unal.edu.co>
- Camilo Londoño Moreno - <calondonom@unal.edu.co>
- Juan David Ardila Diaz - <juardilad@unal.edu.co>

## Ejecución

1. Si no cuenta con el jar de antlr `antlr-4.13.2-complete.jar` en la carpeta lib, descargarlo e incluirlo ahí.
2. Ejecutar el script `build.sh` para convertir los archivos de gramática en código de Java, y compilar dicho código
3. Ejecutar el script `run_tests.sh` para constatar que el análisis léxico y sintáctico se hace adecuadamente para los casos de prueba considerados.
4. Para generar el zip de entrega a UNCode, ejecutar `package.sh`. Crea `dist/lua-interpreter.zip` con las carpetas `src` y `lib`, y verifica que compile con Java 8.
  
## Observaciones

### Gramática

- La gramática fue sacada de: [Gramática de Lua](https://github.com/antlr/grammars-v4/tree/master/lua).
  
- Los casos de prueba están escritos en Lua 5.4. Por lo tanto la gramática también adapta esta versión. De ahí que haya algunas características que no se incluyen con respecto al manual de referencia oficial de Lua 5.5 con el cual se trabajó a lo largo del semestre, como:
  - las derivaciones `global`en `stat` no existen
  - Los `attnamelist` solo admiten el atributo como posfijo, no como prefijo.
  - El Name opcional en la derivación de varargparam no existe.
- Los operadores binarios se descomponen en varias reglas para implementar precedencia.
  - Hay una mejora que se puede hacer a los operadores bitwise, porque en la gramática actual no implementan la precedencia que sí implementa Lua real.
- La regla general es asociatividad por la izquieda pero algunos, como el exponente, requieren asociatividad por derecha.
- La forma en la que se escribieron prefixexp, var y functioncall es porque antlr no puede trabajar con gramáticas con recursividad indirecta por la izquierda.
- Las clases `LuaLexerBase.java` y `LuaParserBase.java` también se sacarón del repositorio de gramáticas de antlr.

## Pendientes

Por ahora solo están el análisis léxico y sintáctico. El detalle de cada punto está en `scope.md`.

### Gramática

- [ ] Corregir la precedencia de los operadores bitwise para que coincida con Lua 5.4.

### Intérprete (Visitor sobre el árbol de sintaxis)

- [ ] Representación de valores: `nil`, booleanos, enteros (`long`) y flotantes (`double`), strings como bytes, funciones, tablas, corrutinas y userdata.
- [ ] Tablas con parte de arreglo y parte hash, para que `#` dé el mismo borde que Lua real (`{10,20,30,nil,50}` → 5).
- [ ] Normalizar claves flotantes con valor entero (`a[2.0]` es `a[2]`).
- [ ] Sentencias: asignación múltiple, `local` con `<const>` y `<close>`, `do`, `while`, `repeat`/`until`, `if`, `for` numérico y genérico, `goto`/etiquetas, `break` y `return`.
- [ ] Funciones: closures con upvalues (variable nueva en cada iteración), varargs `...`, métodos con `:`, y expansión de múltiples resultados solo en la última posición.
- [ ] Orden de evaluación en asignaciones (`i, t[i] = i+1, 20`).
- [ ] Operadores aritméticos (`//` y `%` hacia −∞), bitwise (sin coerción de strings), comparación, concatenación, `and`/`or` con cortocircuito.
- [ ] Coerción de strings a números en aritmética (`" -10 " + 0`).
- [ ] Formato de números al imprimir (`%.14g` y sufijo `.0`).
- [ ] `_ENV` como tabla de globales.

### Metatablas

- [ ] Metamétodos aritméticos y bitwise (`__add`, `__band`, ...).
- [ ] `__index` y `__newindex` como función y como tabla, con cadenas.
- [ ] `__concat`, `__len`, `__eq`, `__lt`, `__le`, `__call`, `__close` y `__mode`.

### Errores

- [ ] `error`, `pcall` y `xpcall` con valores string o tabla.
- [ ] Mensajes con prefijo `archivo:línea:` (p. ej. `test_cases/01.in:49: ...`).
- [ ] Variables `<close>` que se cierran al salir del bloque normalmente o por error.

### Corrutinas y recolección de basura

- [ ] `coroutine.create`, `resume` y `yield`, incluyendo el error `cannot resume dead coroutine`.
- [ ] Tablas débiles (`__mode="kv"`) vacías después de `collectgarbage("collect")`.

### Biblioteca estándar

- [ ] `print`, `type`, `tostring`, `pcall`, `xpcall`, `error`, `setmetatable`, `rawset`, `pairs`, `ipairs`, `collectgarbage` y `load`.
- [ ] `coroutine.create`, `coroutine.resume`, `coroutine.yield`, `table.pack`, `table.unpack`, `io.write`, `io.stdin` y `math.pi`.

### Pruebas y entrega

- [ ] Pasar todos los casos de `test_cases/` con `run_tests.sh` (salida idéntica a `lua5.4`).
- [ ] Generar el zip con `package.sh` y verificar que compile con Java 8.
