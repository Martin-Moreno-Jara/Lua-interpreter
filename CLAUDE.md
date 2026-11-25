# CLAUDE.md — Intérprete de Lua 5.4 (ANTLR + Visitor)

Este archivo es la **fuente de verdad** del proyecto. Coordina el trabajo de los
3 integrantes: alcance, arquitectura, contrato entre módulos, dueños de cada
archivo y flujo de trabajo. Si el código y este archivo no coinciden, se corrige
uno de los dos en el mismo PR. Ninguno de los dos se ignora.

`scope.md` tiene el análisis detallado de funcionalidades y `guide.md` la puesta
en marcha de ANTLR. Si hay conflicto, manda este archivo.

---

## 1. Qué es el proyecto

Un intérprete de **Lua 5.4** escrito en **Java** para la materia Lenguajes de
Programación 2026-2 (UNAL):

- **Análisis léxico y sintáctico:** ANTLR 4.13.2, con `grammar/LuaLexer.g4` y
  `grammar/LuaParser.g4`.
- **Ejecución:** un **tree-walking interpreter** que recorre el árbol de
  sintaxis con el **patrón Visitor**. Extiende `LuaParserBaseVisitor`, que se
  genera con `-visitor` en `build.sh`.

### Alcance

**El alcance está definido exclusivamente por los 6 casos de prueba**
`test_cases/01.in` … `06.in`. El objetivo es que la salida de cada uno sea
idéntica byte a byte a su `.expected`, que se generó con `lua5.4` real.

- Lo que los casos no usan está **fuera de alcance**. Eso incluye `string.*`,
  `os.*`, la mayoría de `math.*`, `__tostring`, `__gc`, los mensajes de error
  exactos para errores de tipo que ningún caso imprime, los niveles de
  `error(msg, level)` distintos de 1, y el rendimiento.
- No se agrega funcionalidad "por si acaso". Si algo nuevo parece necesario,
  primero se comprueba que algún caso lo use.

### Evaluación

- La entrega se sube a **UNCode** como zip (`package.sh` genera
  `dist/lua-interpreter.zip`). **Se espera que el juez automático marque _Wrong
  Answer_.** Cada grupo puede implementar un intérprete o un traductor, así que
  la calificación es **manual**.
- Por eso el zip solo necesita **compilar con Java 8 y ejecutarse**. La
  verificación real de correctitud es `./run_tests.sh` contra los `.expected`.

---

## 2. Restricciones duras

1. **Java 8.** El juez compila con JDK 1.8, así que no se usa `var`, ni `switch`
   con flechas, ni records, ni `List.of`, ni hilos virtuales. `package.sh` lo
   verifica con `javac --release 8`.
2. **Paquete por defecto y `src/` plano.** `package.sh` compila `src/*.java` sin
   subcarpetas, así que no se crean paquetes ni subdirectorios en `src/`.
3. **Sin dependencias** además del runtime de ANTLR.
4. **Bytes, no caracteres.** Toda la E/S va en **ISO-8859-1**: un `char` de Java
   equivale a un byte de Lua. Esto aplica a la lectura del fuente y a
   `System.out`. Para eso, `Main` envuelve stdout en
   `PrintStream(..., "ISO-8859-1")`.
5. **Toda la salida va a stdout.** `run_tests.sh` compara stdout y stderr
   combinados.
6. **Nunca escribir barra invertida seguida de `u` en el código Java**, ni
   siquiera en comentarios. Java lo procesa como escape Unicode antes de
   compilar y falla. En el código se escribe `'\\' + "u"` o se describe con
   palabras.

## 3. Comandos

```bash
./build.sh            # genera gen/ desde la gramática y compila a out/
./run_tests.sh        # ejecuta los 6 casos y hace diff contra .expected
./run_tests.sh -b     # recompila y luego ejecuta los casos
./package.sh          # zip de entrega y verificación con Java 8
java -cp "out:lib/antlr-4.13.2-complete.jar" Main test_cases/03.in
```

### Pruebas por sección (JUnit 5): la verificación central del avance

```bash
./run_unit_tests.sh           # 6 casos completos + 39 secciones (45 pruebas)
./run_unit_tests.sh -b        # recompila el intérprete antes
./run_unit_tests.sh -c 03     # solo el caso 03 y sus secciones
./run_unit_tests.sh -o B      # solo las secciones donde B es dueño
```

- **Requisito:** `lib/junit-platform-console-standalone-1.11.4.jar`. Si falta,
  el script muestra el comando `curl` para descargarlo, porque `.gitignore`
  excluye los `*.jar`.
- **Cómo funciona:** `tests/LuaTestCasesTest.java` ejecuta `java Main <archivo>`
  como **subproceso**, con un timeout de 10 s, y compara stdout y stderr byte a
  byte con la salida de `lua5.4`. Si falla, muestra las líneas que difieren.
- **Secciones:** `tests/sections.txt` define las secciones, con caso, dueños y
  líneas. Cada una es un fragmento **independiente** (`tests/sections/<id>.lua`)
  que incluye el código inicial del que depende y conserva los números de línea
  del original. Su `.expected` se generó con `lua5.4`.
- **Si cambia `tests/sections.txt`:** se regenera con
  `python3 tests/gen_sections.py`, que requiere `lua5.4`. El script también
  verifica que las secciones de cada caso, concatenadas, reproducen el
  `.expected` completo.
- **Nunca se editan a mano** ni `tests/sections/*` ni los `.expected`.
- `run_tests.sh` se mantiene como verificación rápida de los 6 casos completos.

### Notas

- `run_tests.sh` pasa la **ruta** del archivo, así que los errores llevan el
  prefijo `test_cases/01.in:49:` igual que en `.expected`.
- `package.sh` ejecuta los casos **por stdin**. Hoy considera que un caso pasa
  si la salida es `syntax errors: 0`, y **hay que cambiarlo** cuando `Main`
  empiece a interpretar (tarea de C, ver §7). Con stdin el nombre del chunk es
  `stdin`, como en `lua5.4`. Eso es aceptable porque la calificación es manual.

---

## 4. Arquitectura

```
fuente (ISO-8859-1)
   └─► LuaLexer ─► LuaParser ─► árbol (start_)
                                  └─► Evaluator (Visitor)      [A]
                                        ├─► Ops / LuaTable / LuaValue  [B]
                                        └─► funciones de Stdlib        [C]
```

| Archivo (`src/`)                                              | Dueño | Responsabilidad                                                           |
| ------------------------------------------------------------- | ----- | ------------------------------------------------------------------------- |
| `grammar/*.g4`                                                | A     | Gramática. Cambiarla requiere avisar, porque cambia las clases generadas. |
| `Interpreter.java`                                            | A     | API pública: `load`, línea y chunk actuales.                              |
| `Evaluator.java`                                              | A     | Visitor sobre el árbol: sentencias y expresiones.                         |
| `LuaClosure.java`                                             | A     | Función definida en Lua: parámetros, varargs y upvalues.                  |
| `Scope.java` y señales de control                             | A     | Entornos léxicos, `break`, `return`, `goto`.                              |
| `LuaValue.java` y subclases                                   | B     | `LuaNil`, `LuaBoolean`, `LuaInteger`, `LuaFloat`, `LuaString`.            |
| `LuaTable.java`                                               | B     | Parte array más parte hash, borde de `#`, modo débil.                     |
| `LuaFunction.java`, `LuaError.java`                           | B     | Clases base del contrato.                                                 |
| `Ops.java`                                                    | B     | Semántica de operadores, coerciones y metamétodos.                        |
| `Literals.java`                                               | B     | Decodificación de números y strings literales.                            |
| `NumberFormat.java`                                           | B     | Formato de números (`%.14g` de Lua).                                      |
| `Main.java`                                                   | C     | Punto de entrada.                                                         |
| `Stdlib.java`                                                 | C     | Funciones globales y librerías `io`, `math`, `table`, `coroutine`.        |
| `LuaCoroutine.java`, `LuaUserdata.java`                       | C     | Tipos `thread` y `userdata`.                                              |
| `build.sh`, `run_tests.sh`, `package.sh`, `run_unit_tests.sh` | C     | Scripts.                                                                  |
| `tests/`                                                      | C     | Pruebas JUnit, manifiesto de secciones y fragmentos generados.            |
| `LuaLexerBase.java`, `LuaParserBase.java`                     | —     | Copiados de grammars-v4. No se tocan.                                     |

**Regla de propiedad:** cada integrante modifica solo sus archivos. Si necesita
un cambio en un archivo ajeno, lo pide al dueño o lo propone en un PR que el
dueño revisa.

---

## 5. Contrato común (Fase 0)

Esto es lo único que comparten los tres módulos. Las firmas son
**obligatorias**: cambiarlas requiere un PR que actualice esta sección y avise a
los otros dos. Lo que no aparece aquí es detalle interno de cada dueño.

### 5.1 Valores

```java
public abstract class LuaValue {
    public static final LuaValue[] NONE = new LuaValue[0];
    public abstract String typeName();       // "nil","boolean","number","string","table","function","thread","userdata"
    public boolean isTruthy() { return true; }   // solo NIL y FALSE devuelven false
    public static LuaValue of(long v);       // LuaInteger
    public static LuaValue of(double v);     // LuaFloat
    public static LuaValue of(String s);     // LuaString (ISO-8859-1)
    public static LuaValue of(boolean b);    // LuaBoolean.TRUE / FALSE
}
```

- **`nil`:** se usa siempre `LuaNil.NIL`, y **nunca** el `null` de Java como
  valor de Lua. Ningún método del contrato recibe ni devuelve `null`, salvo
  `LuaTable.next` y `getMetatable`, donde está indicado.
- **Booleanos:** `LuaBoolean.TRUE` y `LuaBoolean.FALSE` son únicos.
- **Números:** `LuaInteger` envuelve un `long` y `LuaFloat` un `double`. Son
  clases distintas.
- **`equals` y `hashCode`:** strings y números se comparan por valor
  (`LuaInteger(2)` es igual a `LuaFloat(2.0)` como clave, porque `rawset`
  normaliza). Tablas, funciones, corrutinas y userdata se comparan por
  referencia.

### 5.2 Funciones y convención de llamada

```java
public abstract class LuaFunction extends LuaValue {
    public abstract LuaValue[] call(LuaValue[] args);
    public static LuaValue arg(LuaValue[] args, int i);   // args[i] o NIL si no se pasó
}
```

- **Resultados:** siempre un arreglo. Si no hay resultados, es `LuaValue.NONE`,
  nunca `null`.
- **Argumentos que faltan:** valen `nil`. Los completa **quien recibe**: la
  closure o la función nativa.
- **Llamadas con `obj:m(x)`:** el evaluador pasa `obj` como `args[0]`.
- **Implementaciones:** `LuaClosure` (de A) y las funciones nativas (de C, como
  subclases anónimas) heredan de `LuaFunction`.

### 5.3 Errores

```java
public class LuaError extends RuntimeException {
    public final LuaValue value;          // string, tabla o cualquier valor
    public boolean positioned;            // true si el mensaje ya tiene "chunk:línea: "
    public LuaError(LuaValue value);
    public LuaError(String message);      // error de runtime sin posición
}
```

- **Todo error de Lua es un `LuaError`.** Nunca se deja escapar una
  `NullPointerException`, `ClassCastException` o similar, porque `pcall` no las
  atraparía.
- **`error("msg")`** (de C) antepone
  `Interpreter.currentChunk() + ":" + Interpreter.currentLine() + ": "`. Así se
  obtiene `test_cases/01.in:49: Este es un error...`.
- **`error(tabla)`** no lleva prefijo.
- **Errores de runtime de `Ops`** (de B), como una suma con `nil`: B los lanza
  sin posición. El evaluador (A) los captura, les antepone la posición **una
  sola vez** y marca `positioned`.
- **Errores no capturados:** `Main` imprime `lua: <mensaje>` y termina. No
  ocurre en ningún caso de prueba.

### 5.4 Operadores (`Ops`), la frontera entre A y B

```java
public final class Ops {
    public static LuaValue arith(String op, LuaValue a, LuaValue b); // "+","-","*","/","//","%","^","&","|","~","<<",">>"
    public static LuaValue unm(LuaValue a);
    public static LuaValue bnot(LuaValue a);
    public static LuaValue concat(LuaValue a, LuaValue b);
    public static LuaValue len(LuaValue a);                            // incluye __len
    public static boolean  eq(LuaValue a, LuaValue b);                 // incluye __eq
    public static boolean  lt(LuaValue a, LuaValue b);                 // incluye __lt
    public static boolean  le(LuaValue a, LuaValue b);                 // incluye __le
    public static LuaValue index(LuaValue t, LuaValue k);              // incluye la cadena __index
    public static void     setIndex(LuaValue t, LuaValue k, LuaValue v); // incluye __newindex
    public static LuaValue[] call(LuaValue f, LuaValue[] args);       // incluye __call
    public static String   tostring(LuaValue v);                       // formato de print/tostring
}
```

- **El evaluador nunca opera valores directamente:** siempre pasa por `Ops`. Los
  metamétodos viven **solo** dentro de `Ops`.
- **`>`, `>=` y `~=`:** el evaluador los traduce a `lt` y `le` con los operandos
  invertidos, y a `!eq`.
- **`and`, `or` y `not`:** los resuelve A, por el cortocircuito. No están en
  `Ops`.
- **`tostring`:** el formato de tablas y funciones es `table: 0x%08x` con
  `System.identityHashCode`. Ningún caso lo imprime.

### 5.5 Tablas

```java
public class LuaTable extends LuaValue {
    public LuaValue   rawget(LuaValue k);              // NIL si no existe
    public void       rawset(LuaValue k, LuaValue v);  // v == NIL borra; normaliza 2.0 -> 2
    public void       appendArray(LuaValue v);         // entrada posicional del constructor (admite NIL)
    public LuaValue[] next(LuaValue k);                // {clave, valor} siguiente; null al terminar
    public long       length();                        // borde, sin __len
    public LuaTable   getMetatable();                  // null si no tiene
    public void       setMetatable(LuaTable mt);       // lee __mode para el modo débil
}
```

- **Constructores de tabla:** A inserta las entradas posicionales con
  `appendArray`, **incluidos los `nil`**, en orden. Esto es lo que hace que
  `#{10,20,30,nil,50}` dé 5 y `#{nil,20,30}` dé 3.
- **`rawget` y `rawset`** ignoran los metamétodos.

### 5.6 Intérprete y biblioteca estándar

```java
public class Interpreter {
    public static LuaFunction load(String code, String chunkName, LuaTable env);
    public static int    currentLine();    // ThreadLocal: línea de la llamada o sentencia en curso
    public static String currentChunk();   // ThreadLocal: nombre del chunk en curso
}
public class Stdlib {
    public static LuaTable createGlobals();   // _G con todo lo que usan los casos; _ENV apunta aquí
}
```

- **`currentLine` y `currentChunk`** son `ThreadLocal`, porque cada corrutina
  corre en su propio hilo.
- **Cuándo se actualiza la línea:** A la actualiza al evaluar cada sentencia y
  **cada llamada a función**, con la línea del token de la llamada. Así un
  `error(...)` reporta su propia línea.
- **Nombre del chunk:** es la ruta recibida en `args[0]`, o `stdin` si se lee de
  stdin. Para `load` es `[string "..."]`, como en Lua; ningún caso lo imprime.

### 5.7 Literales, la otra frontera entre A y B

```java
public final class Literals {
    public static LuaValue parseNumber(String tokenText);  // INT, HEX, FLOAT, HEX_FLOAT -> LuaInteger o LuaFloat
    public static String   parseString(String tokenText);  // NORMALSTRING, CHARSTRING, LONGSTRING (con comillas o corchetes) -> contenido decodificado
}
```

El evaluador le pasa a `Literals` el texto **completo** del token, tal como lo
entrega ANTLR (`ctx.getText()`), y recibe el valor ya decodificado.

---

## 6. Decisiones de semántica ya tomadas

- **Formato de números (B):** los enteros se imprimen tal cual. Los flotantes
  usan el equivalente de `%.14g` de C, añadiendo `.0` si el resultado parece
  entero, y escriben `inf`, `-inf` o `nan`. `String.format("%.14g")` de Java
  **no** es equivalente, así que hay que implementarlo.
- **Aritmética:** `/` y `^` siempre devuelven float. `//` y `%` redondean hacia
  −∞. Int con int da int, y si hay algún float el resultado es float.
- **Coerción string→número** en aritmética, admitiendo espacios (`" -10 " + 0`
  da `-10`). **No** aplica a operaciones de bits: `"10" & 5` es un error.
- **Operaciones de bits:** aceptan floats con valor entero exacto. Los
  desplazamientos son lógicos, un desplazamiento negativo va en la dirección
  contraria, y uno de 64 o más da 0.
- **Cada iteración de un bucle crea un local nuevo**, así que las closures
  capturan valores distintos.
- **`<close>`:** se implementa con `try/finally` en el bloque. Si sale por
  error, `__close(valor, errorObj)` recibe el valor del error.
- **`goto`:** el bloque ejecuta sus sentencias por índice y salta al índice de
  la etiqueta.
- **Corrutinas (C):** un hilo de plataforma por corrutina, con traspaso síncrono
  (`SynchronousQueue` o semáforos). Los hilos son _daemon_ para no bloquear la
  salida del programa.
- **Tablas débiles (B y C):** con `__mode`, la tabla guarda `WeakReference`.
  `collectgarbage("collect")` llama a `System.gc()` varias veces y purga las
  entradas muertas. **Riesgo:** ninguna estructura interna del intérprete (por
  ejemplo, un arreglo temporal de resultados) puede retener la clave.

---

## 7. Roles y tareas

Los nombres se asignan después. Integrantes: Martin Moreno Jara, Camilo Londoño
Moreno y Juan David Ardila Díaz.

### Persona A: Evaluador (Visitor). Asignado a: Martin

- [x] `Evaluator` extiende `LuaParserBaseVisitor`. `Interpreter.load` parsea el
      código y devuelve el chunk como `LuaFunction`.
- [x] Scopes léxicos y shadowing (`local x = x`), upvalues, un local nuevo por
      iteración y `_ENV` como globales.
- [x] Asignación múltiple: primero se evalúan las tablas y claves del lado
      izquierdo, luego el lado derecho, y por último se asigna
      (`i, t[i] = i+1, 20`).
- [x] `do`, `while`, `repeat`/`until` (la condición ve los locales del cuerpo),
      `if`/`elseif`/`else`, `for` numérico (int y float, paso negativo), `for`
      genérico, `break`, `return`, `goto` y `::label::`.
- [x] Funciones: `function a.b:c()`, `local function` recursiva, varargs,
      closures y funciones anónimas invocadas al instante.
- [x] Llamadas `f"s"`, `f{...}`, `o:m()` encadenadas, `(print or io.write)(…)`.
- [x] Multirresultados: solo se expande la última expresión de una lista, y
      `(f())` se trunca a un valor. Aplica en argumentos, constructores,
      `return` y `local`.
- [x] Constructores de tabla con `[e]=`, `name=` y posicionales vía
      `appendArray`.
- [x] `<const>` y `<close>` (incluido el caso de error).
- [x] Mantener `currentLine` y `currentChunk`, y anteponer la posición a los
      errores de runtime.

### Persona B: Valores, tablas y operadores. Asignado a: Camilo

- [ ] Clases de `LuaValue`, con `equals` y `hashCode` según §5.1.
- [ ] `Literals`: escapes `\n \t \0 \xXX \ddd \u{XXX}` (a UTF-8) y `\z`; strings
      largos `[==[ ]==]` (sin el primer salto de línea y sin procesar escapes);
      números decimales, hexadecimales, con exponente y hex float.
- [ ] `NumberFormat`: el `%.14g` de Lua.
- [ ] `Ops`: aritmética, operaciones de bits, coerciones, comparaciones (NaN,
      `10 == 10.0`, orden lexicográfico de strings), concatenación y longitud.
- [ ] Todos los metamétodos:
      `__add __sub __mul __div __mod __pow __unm     __idiv __band __bor __bxor __bnot __shl __shr __concat __len __eq __lt     __le __index __newindex __call`,
      con `__index` y `__newindex` como función o como tabla, y cadenas de
      `__index`.
- [ ] `LuaTable`: parte array más hash, normalización de claves float, borde de
      `#` igual a Lua real, `next` y modo débil.

### Persona C: Biblioteca estándar, errores y concurrencia. Asignado a: David

- [ ] `Main`: lee el archivo o stdin en ISO-8859-1, configura stdout en
      ISO-8859-1, ejecuta e imprime los errores no capturados.
- [ ] `print`, `type`, `tostring`, `setmetatable`, `rawset`, `pairs`, `ipairs`,
      `table.pack` (con `n`), `table.unpack(t, i, j)`, `io.write`, `io.stdin`
      (userdata) y `math.pi`.
- [ ] `error` (con prefijo de posición), `pcall` y `xpcall` con manejador.
- [ ] `load(string)` usando `Interpreter.load` con las globales compartidas.
- [ ] `coroutine.create`, `resume` y `yield`, con el error
      `cannot resume dead coroutine`.
- [ ] `collectgarbage("collect")` coordinado con el modo débil de B.
- [ ] Actualizar `package.sh` para que compare contra `.expected` en vez de
      `syntax errors: 0`.

### Fase 0: todos

- [x] Crear los archivos del contrato (§5) con las firmas exactas y cuerpos
      `throw new UnsupportedOperationException("TODO <A|B|C>")`, de forma que
      `./build.sh` compile con Java 8.
  - Las clases de valor (`LuaNil`, `LuaBoolean`, `LuaInteger`, `LuaFloat`,
    `LuaString`), `LuaError` y `LuaFunction` ya están **completas**, porque son
    triviales y las usan los tres.
  - Para encontrar lo pendiente de cada uno: `grep -rn "TODO A" src/` (o
    `TODO B`, `TODO C`).
  - `Main` sigue imprimiendo `syntax errors: N` hasta que C lo conecte con
    `Interpreter` y `Stdlib`. Así `package.sh` sigue en verde mientras tanto.
- [ ] Hacer merge a `main` (hito H0).

---

## 8. Matriz caso → dueño

| Caso | Contenido                                                                                                                                           | Dueños principales |
| ---- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| `01` | Tipos y claves float (B, C); scopes y closures (A); `pcall` y `xpcall` (C); metamétodos (B); tabla débil y GC (B, C); `<close>` (A); corrutinas (C) | Todos              |
| `02` | Literales (B); asignación múltiple, bloques, `if`/`while`/`repeat`/`goto`/`for` (A); `<const>` y `<close>` con error (A, C); `load` y `_ENV` (C, A) | A                  |
| `03` | Aritmética, operaciones de bits, coerciones, relacionales, concatenación y `#` (B); lógicos y precedencia (A)                                       | B                  |
| `04` | Constructores (A, B); llamadas, métodos, varargs, multirresultados (A); `table.pack` (C)                                                            | A                  |
| `05` | Integración: IIFE, `<<`, clave calculada, `__call`, `__add`, `__index`, `table.unpack`                                                              | Todos              |
| `06` | Integración: cadena de `__index`, `__unm`, `__concat`, `__len`, `ipairs`, claves booleanas y de tabla                                               | Todos              |

### Hitos

1. **H0:** el contrato compila en `main`.
2. **H1:** `print` con expresiones aritméticas funciona y el caso `03` pasa.
3. **H2:** los casos `04` y `02` pasan.
4. **H3:** los casos `01`, `05` y `06` pasan.
5. **H4:** `package.sh` en verde y zip entregado.

---

## 9. Flujo de trabajo con git

- **Una rama por persona:** `a/<tema>`, `b/<tema>` o `c/<tema>`. Nunca se hacen
  commits directos a `main`.
- **Integración por PR a `main`**, con dos condiciones:
  1. `./build.sh` compila.
  2. `./run_unit_tests.sh` no empeora: ninguna prueba (caso o sección) que
     pasaba deja de pasar. En el PR se indica qué secciones nuevas pasan.
- **PRs pequeños y frecuentes.** Hay que integrar a diario para que las piezas
  se encuentren pronto.
- **Cambios de contrato:** un PR propio que actualiza §5 de este archivo, con
  aprobación de los otros dos.
- **Los `.expected` no se editan nunca.** Son la salida de `lua5.4` real.

---

## 10. Riesgos principales

1. **El formato `%.14g`** de los floats, como `3.1415926535898`, `21.0` u `8.0`.
2. **El borde de `#`** con agujeros (`{10,20,30,nil,50}` da 5).
3. **La tabla débil**, que debe quedar vacía tras `collectgarbage`, porque
   depende del GC de la JVM.
4. **Los prefijos de error `chunk:línea:`**, que necesitan la línea correcta de
   la llamada a `error`.
5. **El orden de `pairs`.** Los casos no imprimen iteraciones de `pairs` cuyo
   orden importe, pero si aparece una, hay que replicar el orden de Lua.

---

## 11. Instrucciones para Claude (y otros asistentes)

- Antes de editar, identificar **quién es dueño** del archivo (§4). Si la
  persona que pide el cambio no es la dueña, avisarlo.
- **No cambiar firmas del §5** sin actualizar este archivo en el mismo cambio y
  señalarlo explícitamente.
- Todo el código debe compilar con **Java 8** y vivir en `src/` sin paquetes.
- Tras un cambio: `./run_unit_tests.sh -b` (o con `-c`/`-o` para acotar), e
  informar qué casos y secciones pasan y cuáles no, con el diff.
- **No implementar funcionalidad fuera del alcance (§1)** ni modificar los
  `.expected`.
- Marcar las casillas del §7 cuando una tarea quede terminada y verificada.
