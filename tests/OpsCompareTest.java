import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pruebas de eq, lt, le, concat y len (sin metamétodos). Los valores esperados
 * salen de lua5.4 real.
 */
public class OpsCompareTest {

    private static LuaValue n(long v) { return LuaValue.of(v); }
    private static LuaValue f(double v) { return LuaValue.of(v); }
    private static LuaValue s(String v) { return LuaValue.of(v); }

    private static LuaTable constructor(LuaValue... values) {
        LuaTable t = new LuaTable();
        for (LuaValue v : values) t.appendArray(v);
        return t;
    }

    private static long len(LuaValue v) { return ((LuaInteger) Ops.len(v)).value; }

    // ---- eq ----

    @Test
    void igualdadEntreEnteroYFloat() {
        assertTrue(Ops.eq(n(10), f(10.0)));
        assertTrue(Ops.eq(f(10.0), n(10)));
        assertFalse(Ops.eq(n(1), f(1.5)));
        assertTrue(Ops.eq(f(0.0), f(-0.0)));
    }

    @Test
    void igualdadSinPerderPrecision() {
        long big = (1L << 53) + 1;
        assertFalse(Ops.eq(n(big), f(Math.pow(2, 53))));
        assertTrue(Ops.eq(n(1L << 53), f(Math.pow(2, 53))));
        assertFalse(Ops.eq(n(Long.MAX_VALUE), f(Math.pow(2, 63))));
        assertTrue(Ops.eq(n(Long.MIN_VALUE), f(-Math.pow(2, 63))));
    }

    @Test
    void nanNuncaEsIgual() {
        LuaValue nan = f(0.0 / 0.0);
        assertFalse(Ops.eq(nan, nan));
        assertFalse(Ops.eq(nan, f(0.0 / 0.0)));
        assertFalse(Ops.eq(nan, n(1)));
        assertFalse(Ops.eq(n(1), nan));
    }

    @Test
    void igualdadDeTiposDistintosYStrings() {
        assertFalse(Ops.eq(s("10"), n(10)));
        assertTrue(Ops.eq(s("a"), s("a")));
        assertFalse(Ops.eq(s("a"), s("b")));
        assertTrue(Ops.eq(LuaNil.NIL, LuaNil.NIL));
        assertFalse(Ops.eq(LuaNil.NIL, LuaBoolean.FALSE));
        assertTrue(Ops.eq(LuaBoolean.TRUE, LuaBoolean.TRUE));
        assertFalse(Ops.eq(LuaBoolean.TRUE, LuaBoolean.FALSE));
    }

    @Test
    void tablasSePorReferencia() {
        LuaTable t1 = new LuaTable();
        LuaTable t2 = new LuaTable();
        assertFalse(Ops.eq(t1, t2));
        assertTrue(Ops.eq(t1, t1));
        assertFalse(Ops.eq(t1, LuaNil.NIL));
    }

    // ---- lt / le ----

    @Test
    void ordenNumerico() {
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

    @Test
    void ordenMixtoSinPerderPrecision() {
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
        assertTrue(Ops.lt(f(p63), n(Long.MAX_VALUE)) == false);
    }

    @Test
    void nanDaFalseEnComparaciones() {
        LuaValue nan = f(0.0 / 0.0);
        assertFalse(Ops.lt(nan, nan));
        assertFalse(Ops.le(nan, nan));
        assertFalse(Ops.lt(nan, n(1)));
        assertFalse(Ops.lt(n(1), nan));
        assertFalse(Ops.le(nan, n(1)));
        assertFalse(Ops.le(n(1), nan));
        assertFalse(Ops.lt(nan, f(1.5)));
    }

    @Test
    void ordenLexicografico() {
        assertTrue(Ops.lt(s("apple"), s("banana")));
        assertFalse(Ops.lt(s("b"), s("a")));
        assertTrue(Ops.le(s("a"), s("a")));
        assertTrue(Ops.lt(s("abc"), s("abd")));
        assertTrue(Ops.lt(s("Z"), s("a")));
        assertTrue(Ops.lt(s(""), s("a")));
        assertTrue(Ops.lt(s("a"), s("ab")));
        assertTrue(Ops.lt(s("a"), s(String.valueOf((char) 255))));
    }

    @Test
    void comparacionInvalidaEsError() {
        assertThrows(LuaError.class, () -> Ops.lt(n(1), s("2")));
        assertThrows(LuaError.class, () -> Ops.le(s("1"), n(2)));
        assertThrows(LuaError.class, () -> Ops.lt(new LuaTable(), new LuaTable()));
        assertThrows(LuaError.class, () -> Ops.le(LuaNil.NIL, n(1)));
        assertThrows(LuaError.class, () -> Ops.lt(LuaBoolean.TRUE, LuaBoolean.FALSE));
    }

    // ---- concat ----

    @Test
    void concatenaStringsYNumeros() {
        assertEquals("Hola Mundo", ((LuaString) Ops.concat(s("Hola "), s("Mundo"))).value);
        assertEquals("1020", ((LuaString) Ops.concat(n(10), n(20))).value);
        assertEquals("Valor: 99", ((LuaString) Ops.concat(s("Valor: "), n(99))).value);
        assertEquals("1.5", ((LuaString) Ops.concat(f(1.5), s(""))).value);
        assertEquals("x2.0", ((LuaString) Ops.concat(s("x"), f(2.0))).value);
        assertEquals("-0.0", ((LuaString) Ops.concat(f(-0.0), s(""))).value);
        assertEquals("1e+100|", ((LuaString) Ops.concat(f(1e100), s("|"))).value);
    }

    @Test
    void concatTipoInvalidoEsError() {
        assertThrows(LuaError.class, () -> Ops.concat(LuaNil.NIL, s("a")));
        assertThrows(LuaError.class, () -> Ops.concat(s("a"), new LuaTable()));
        assertThrows(LuaError.class, () -> Ops.concat(LuaBoolean.TRUE, n(1)));
    }

    // ---- len ----

    @Test
    void longitudDeString() {
        assertEquals(10, len(s("Hola Mundo")));
        assertEquals(0, len(s("")));
        assertEquals(3, len(s("abc")));
    }

    @Test
    void longitudDeTablas() {
        assertEquals(5, len(constructor(n(10), n(20), n(30), n(40), n(50))));
        assertEquals(5, len(constructor(n(10), n(20), n(30), LuaNil.NIL, n(50))));
        assertEquals(3, len(constructor(LuaNil.NIL, n(20), n(30))));
        assertEquals(0, len(new LuaTable()));
    }

    @Test
    void longitudDeTipoInvalidoEsError() {
        assertThrows(LuaError.class, () -> Ops.len(n(5)));
        assertThrows(LuaError.class, () -> Ops.len(LuaNil.NIL));
    }
}
