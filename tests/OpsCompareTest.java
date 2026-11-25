import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Los valores esperados se obtuvieron de lua5.4 (secciones 03_3.4.4, 03_3.4.6 y 03_3.4.7, más casos límite).
/** Pruebas de Ops.eq, lt, le, concat y len, sin metamétodos. */
public class OpsCompareTest {

    /** Crea un entero de Lua. */
    private static LuaValue n(long v) { return LuaValue.of(v); }

    /** Crea un número decimal de Lua. */
    private static LuaValue f(double v) { return LuaValue.of(v); }

    /** Crea un string de Lua. */
    private static LuaValue s(String v) { return LuaValue.of(v); }

    /** Arma la tabla igual que lo hará el evaluador con el constructor {a, b, c}. */
    private static LuaTable constructor(LuaValue... values) {
        LuaTable t = new LuaTable();
        for (LuaValue v : values) t.appendArray(v);
        return t;
    }

    /** Resultado de #v como número de Java. */
    private static long len(LuaValue v) { return ((LuaInteger) Ops.len(v)).value; }

    /** Texto de una concatenación a .. b. */
    private static String concat(LuaValue a, LuaValue b) { return ((LuaString) Ops.concat(a, b)).value; }

    // ---- Igualdad ----

    /** Un entero y un decimal son iguales si tienen el mismo valor (10 == 10.0). */
    @Test
    void integerEqualsFloat() {
        assertTrue(Ops.eq(n(10), f(10.0)));
        assertTrue(Ops.eq(f(10.0), n(10)));
        assertFalse(Ops.eq(n(1), f(1.5)));
        assertTrue(Ops.eq(f(0.0), f(-0.0)));
    }

    /** La igualdad entre entero y decimal no pierde precisión con números muy grandes. */
    @Test
    void equalityWithoutPrecisionLoss() {
        long big = (1L << 53) + 1;
        assertFalse(Ops.eq(n(big), f(Math.pow(2, 53))));
        assertTrue(Ops.eq(n(1L << 53), f(Math.pow(2, 53))));
        assertFalse(Ops.eq(n(Long.MAX_VALUE), f(Math.pow(2, 63))));
        assertTrue(Ops.eq(n(Long.MIN_VALUE), f(-Math.pow(2, 63))));
    }

    /** NaN no es igual a nada, ni siquiera a sí mismo. */
    @Test
    void nanIsNeverEqual() {
        LuaValue nan = f(0.0 / 0.0);
        assertFalse(Ops.eq(nan, nan));
        assertFalse(Ops.eq(nan, f(0.0 / 0.0)));
        assertFalse(Ops.eq(nan, n(1)));
        assertFalse(Ops.eq(n(1), nan));
    }

    /** Valores de tipos distintos nunca son iguales ("10" ~= 10); los strings se comparan por contenido. */
    @Test
    void equalityAcrossTypesAndStrings() {
        assertFalse(Ops.eq(s("10"), n(10)));
        assertTrue(Ops.eq(s("a"), s("a")));
        assertFalse(Ops.eq(s("a"), s("b")));
        assertTrue(Ops.eq(LuaNil.NIL, LuaNil.NIL));
        assertFalse(Ops.eq(LuaNil.NIL, LuaBoolean.FALSE));
        assertTrue(Ops.eq(LuaBoolean.TRUE, LuaBoolean.TRUE));
        assertFalse(Ops.eq(LuaBoolean.TRUE, LuaBoolean.FALSE));
    }

    /** Dos tablas solo son iguales si son la misma tabla. */
    @Test
    void tablesCompareByReference() {
        LuaTable t1 = new LuaTable();
        LuaTable t2 = new LuaTable();
        assertFalse(Ops.eq(t1, t2));
        assertTrue(Ops.eq(t1, t1));
        assertFalse(Ops.eq(t1, LuaNil.NIL));
    }

    // ---- Orden (< y <=) ----

    /** Orden entre enteros, decimales e infinitos. */
    @Test
    void numericOrder() {
        assertTrue(Ops.lt(n(1), n(2)));
        assertFalse(Ops.lt(n(2), n(2)));
        assertTrue(Ops.le(n(2), n(2)));
        assertTrue(Ops.lt(n(1), f(1.5)));
        assertFalse(Ops.le(n(2), f(1.5)));
        assertTrue(Ops.lt(f(1.5), n(2)));
        assertFalse(Ops.le(f(1.5), n(1)));
        assertTrue(Ops.lt(f(-1.0 / 0.0), n(Long.MIN_VALUE)));
        assertTrue(Ops.lt(n(Long.MAX_VALUE), f(1.0 / 0.0)));
    }

    /** Comparar un entero con un decimal no pierde precisión con números muy grandes. */
    @Test
    void mixedOrderWithoutPrecisionLoss() {
        long big = (1L << 53) + 1;
        double p53 = Math.pow(2, 53);
        assertFalse(Ops.lt(n(big), f(p53)));
        assertFalse(Ops.lt(f(p53), n(1L << 53)));
        assertTrue(Ops.lt(f(p53), n(big)));
        assertTrue(Ops.le(f(p53), n(big)));
        assertFalse(Ops.le(n(big), f(p53)));
        assertTrue(Ops.le(n(1L << 53), f(p53)));
        double p63 = Math.pow(2, 63);
        assertTrue(Ops.lt(n(Long.MAX_VALUE), f(p63)));
        assertTrue(Ops.le(n(Long.MAX_VALUE), f(p63)));
        assertTrue(Ops.le(n(Long.MIN_VALUE), f(-p63)));
        assertFalse(Ops.lt(n(Long.MIN_VALUE), f(-p63)));
        assertFalse(Ops.lt(f(p63), n(Long.MAX_VALUE)));
    }

    /** Cualquier comparación de orden con NaN da false. */
    @Test
    void nanComparisonsAreFalse() {
        LuaValue nan = f(0.0 / 0.0);
        assertFalse(Ops.lt(nan, nan));
        assertFalse(Ops.le(nan, nan));
        assertFalse(Ops.lt(nan, n(1)));
        assertFalse(Ops.lt(n(1), nan));
        assertFalse(Ops.le(nan, n(1)));
        assertFalse(Ops.le(n(1), nan));
        assertFalse(Ops.lt(nan, f(1.5)));
    }

    /** Los strings se ordenan byte a byte, como en un diccionario ("apple" < "banana"). */
    @Test
    void lexicographicOrder() {
        assertTrue(Ops.lt(s("apple"), s("banana")));
        assertFalse(Ops.lt(s("b"), s("a")));
        assertTrue(Ops.le(s("a"), s("a")));
        assertTrue(Ops.lt(s("abc"), s("abd")));
        assertTrue(Ops.lt(s("Z"), s("a")));
        assertTrue(Ops.lt(s(""), s("a")));
        assertTrue(Ops.lt(s("a"), s("ab")));
        assertTrue(Ops.lt(s("a"), s(String.valueOf((char) 255)))); // el byte 255 va después de todos
    }

    /** Ordenar un número con un string, tablas, nil o booleanos es un error. */
    @Test
    void invalidComparisonIsError() {
        assertThrows(LuaError.class, () -> Ops.lt(n(1), s("2")));
        assertThrows(LuaError.class, () -> Ops.le(s("1"), n(2)));
        assertThrows(LuaError.class, () -> Ops.lt(new LuaTable(), new LuaTable()));
        assertThrows(LuaError.class, () -> Ops.le(LuaNil.NIL, n(1)));
        assertThrows(LuaError.class, () -> Ops.lt(LuaBoolean.TRUE, LuaBoolean.FALSE));
    }

    // ---- Concatenación ----

    /** Se concatenan strings y números; los decimales se escriben como en print. */
    @Test
    void concatenatesStringsAndNumbers() {
        assertEquals("Hola Mundo", concat(s("Hola "), s("Mundo")));
        assertEquals("1020", concat(n(10), n(20)));
        assertEquals("Valor: 99", concat(s("Valor: "), n(99)));
        assertEquals("1.5", concat(f(1.5), s("")));
        assertEquals("x2.0", concat(s("x"), f(2.0)));
        assertEquals("-0.0", concat(f(-0.0), s("")));
        assertEquals("1e+100|", concat(f(1e100), s("|")));
    }

    /** Concatenar nil, tablas o booleanos es un error. */
    @Test
    void concatWithInvalidTypeIsError() {
        assertThrows(LuaError.class, () -> Ops.concat(LuaNil.NIL, s("a")));
        assertThrows(LuaError.class, () -> Ops.concat(s("a"), new LuaTable()));
        assertThrows(LuaError.class, () -> Ops.concat(LuaBoolean.TRUE, n(1)));
    }

    // ---- Longitud (#) ----

    /** La longitud de un string es su cantidad de bytes. */
    @Test
    void stringLength() {
        assertEquals(10, len(s("Hola Mundo")));
        assertEquals(0, len(s("")));
        assertEquals(3, len(s("abc")));
    }

    /** Las longitudes de tablas del caso 03, incluidas las que tienen huecos. */
    @Test
    void tableLength() {
        assertEquals(5, len(constructor(n(10), n(20), n(30), n(40), n(50))));
        assertEquals(5, len(constructor(n(10), n(20), n(30), LuaNil.NIL, n(50))));
        assertEquals(3, len(constructor(LuaNil.NIL, n(20), n(30))));
        assertEquals(0, len(new LuaTable()));
    }

    /** Pedir la longitud de un número o de nil es un error. */
    @Test
    void lengthOfInvalidTypeIsError() {
        assertThrows(LuaError.class, () -> Ops.len(n(5)));
        assertThrows(LuaError.class, () -> Ops.len(LuaNil.NIL));
    }
}
