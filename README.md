# Intérprete de Lua

Implementación de un intérprete para el lenguaje de programación Lua usando
ANTRL. Trabajo para la materia de lenguajes de programación 2026-2 Universidad
Nacional de Colombia.

## Integrantes

- Martin Moreno Jara - mamorenoj@unal.edu.co
- Camilo Londono Moreno - calondonom@unal.edu.co
- Juan David Ardila Diaz - juardilad@unal.edu.co


## Ejecución

1. Si no cuenta con el jar de antlr `antlr-4.13.2-complete.jar` en la carpeta lib, descargarlo e incluirlo ahí.
2. Ejecutar el script `build.sh` para convertir los archivos de gramática en código de Java, y compilar dicho código
3. Ejecutar el script `run_test.sh` para constatar que el análisis léxico y sintáctico se hace adecuadamente para los casos de prueba considerados.
  
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

