import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pruebas de los metamétodos de operadores (aritméticos, bits, concat, len,
 * eq, lt y le).
 */
public class OpsMetaTest {

    private static LuaValue n(long v) { return LuaValue.of(v); }
    private static LuaValue s(String v) { return LuaValue.of(v); }

    // Metamétodo que devuelve siempre los mismos resultados y cuenta las llamadas
    private static class Fixed extends LuaFunction {
        final LuaValue[] result;
        int calls = 0;
        LuaValue[] lastArgs;

        Fixed(LuaValue... result) { this.result = result; }

        @Override public String typeName() { return "function"; }

        @Override public LuaValue[] call(LuaValue[] args) {
            calls++;
            lastArgs = args;
            return result;
        }
    }

    // Tabla con un solo metamétodo
    private static LuaTable withMeta(String event, LuaFunction h) {
        LuaTable mt = new LuaTable();
        mt.rawset(s(event), h);
        LuaTable t = new LuaTable();
        t.setMetatable(mt);
        return t;
    }

    // ---- aritmética y bits ----

    @Test
    void add() {
        Fixed h = new Fixed(n(42));
        LuaTable obj = withMeta("__add", h);
        assertEquals(n(42), Ops.arith("+", obj, n(1)));
        assertSame(obj, h.lastArgs[0]);
        assertEquals(n(1), h.lastArgs[1]);
    }

    @Test
    void sub() {
        assertEquals(n(7), Ops.arith("-", withMeta("__sub", new Fixed(n(7))), n(1)));
    }

    @Test
    void idiv() {
        assertEquals(n(3), Ops.arith("//", withMeta("__idiv", new Fixed(n(3))), n(2)));
    }

    @Test
    void band() {
        assertEquals(n(1), Ops.arith("&", withMeta("__band", new Fixed(n(1))), n(3)));
    }

    @Test
    void shl() {
        assertEquals(n(8), Ops.arith("<<", withMeta("__shl", new Fixed(n(8))), n(3)));
    }

    @Test
    void demasOperadores() {
        assertEquals(n(1), Ops.arith("*", withMeta("__mul", new Fixed(n(1))), n(3)));
        assertEquals(n(2), Ops.arith("/", withMeta("__div", new Fixed(n(2))), n(3)));
        assertEquals(n(3), Ops.arith("%", withMeta("__mod", new Fixed(n(3))), n(3)));
        assertEquals(n(4), Ops.arith("^", withMeta("__pow", new Fixed(n(4))), n(3)));
        assertEquals(n(5), Ops.arith("|", withMeta("__bor", new Fixed(n(5))), n(3)));
        assertEquals(n(6), Ops.arith("~", withMeta("__bxor", new Fixed(n(6))), n(3)));
        assertEquals(n(7), Ops.arith(">>", withMeta("__shr", new Fixed(n(7))), n(3)));
    }

    @Test
    void unmRecibeElOperandoDosVeces() {
        Fixed h = new Fixed(n(-1));
        LuaTable obj = withMeta("__unm", h);
        assertEquals(n(-1), Ops.unm(obj));
        assertEquals(2, h.lastArgs.length);
        assertSame(obj, h.lastArgs[0]);
        assertSame(obj, h.lastArgs[1]);
    }

    @Test
    void bnotRecibeElOperandoDosVeces() {
        Fixed h = new Fixed(n(9));
        LuaTable obj = withMeta("__bnot", h);
        assertEquals(n(9), Ops.bnot(obj));
        assertSame(obj, h.lastArgs[0]);
        assertSame(obj, h.lastArgs[1]);
    }

    @Test
    void bnotNoUsaBxor() {
        // Solo hay __bxor: ~obj debe fallar
        assertThrows(LuaError.class, () -> Ops.bnot(withMeta("__bxor", new Fixed(n(1)))));
    }

    @Test
    void sebusaPrimeroEnAYLuegoEnB() {
        Fixed ha = new Fixed(s("a"));
        Fixed hb = new Fixed(s("b"));
        LuaTable a = withMeta("__add", ha);
        LuaTable b = withMeta("__add", hb);
        // Numero a la izquierda: usa el de b
        assertEquals(s("b"), Ops.arith("+", n(5), b));
        assertEquals(n(5), hb.lastArgs[0]);
        assertSame(b, hb.lastArgs[1]);
        // Si ambos tienen, gana a
        assertEquals(s("a"), Ops.arith("+", a, b));
        assertEquals(1, ha.calls);
        assertEquals(1, hb.calls);
    }

    @Test
    void stringNumericoConTabla() {
        assertEquals(n(1), Ops.arith("+", s("10"), withMeta("__add", new Fixed(n(1)))));
    }

    @Test
    void resultadoSeTruncaAUnValor() {
        assertEquals(n(1), Ops.arith("+", withMeta("__add", new Fixed(n(1), n(2))), n(1)));
    }

    @Test
    void sinResultadoDaNil() {
        assertSame(LuaNil.NIL, Ops.arith("+", withMeta("__add", new Fixed()), n(1)));
    }

    @Test
    void sinMetamétodoSigueElError() {
        LuaTable t = new LuaTable();
        LuaError e = assertThrows(LuaError.class, () -> Ops.arith("+", t, n(1)));
        assertEquals(s("attempt to perform arithmetic on a table value"), e.value);
        e = assertThrows(LuaError.class, () -> Ops.arith("&", t, n(1)));
        assertEquals(s("attempt to perform bitwise operation on a table value"), e.value);
        e = assertThrows(LuaError.class, () -> Ops.bnot(t));
        assertEquals(s("attempt to perform bitwise operation on a table value"), e.value);
        e = assertThrows(LuaError.class, () -> Ops.unm(t));
        assertEquals(s("attempt to perform arithmetic on a table value"), e.value);
    }

    @Test
    void metatablaConOtroEventoNoAplica() {
        LuaTable t = withMeta("__sub", new Fixed(n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", t, n(1)));
    }

    // ---- concat y len ----

    @Test
    void concatConNumeroALaIzquierda() {
        Fixed h = new Fixed(s("ok"));
        LuaTable obj = withMeta("__concat", h);
        assertEquals(s("ok"), Ops.concat(n(1), obj));
        assertEquals(n(1), h.lastArgs[0]);
        assertSame(obj, h.lastArgs[1]);
    }

    @Test
    void concatConTablaALaIzquierda() {
        Fixed h = new Fixed(s("ok"), s("extra"));
        assertEquals(s("ok"), Ops.concat(withMeta("__concat", h), s("x")));
    }

    @Test
    void concatSinMetamétodoSigueElError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.concat(s("x"), new LuaTable()));
        assertEquals(s("attempt to concatenate a table value"), e.value);
    }

    @Test
    void lenUsaMetamétodo() {
        Fixed h = new Fixed(n(99));
        LuaTable obj = withMeta("__len", h);
        assertEquals(n(99), Ops.len(obj));
        assertEquals(1, h.lastArgs.length);
        assertSame(obj, h.lastArgs[0]);
    }

    @Test
    void lenSinMetamétodoUsaElBorde() {
        LuaTable t = new LuaTable();
        t.appendArray(n(1));
        t.appendArray(n(2));
        assertEquals(n(2), Ops.len(t));
    }

    // ---- eq ----

    @Test
    void eqUsaMetamétodoConTablasDistintas() {
        Fixed h = new Fixed(LuaBoolean.TRUE);
        LuaTable a = withMeta("__eq", h);
        LuaTable b = new LuaTable();
        assertTrue(Ops.eq(a, b));
        assertEquals(1, h.calls);
        // Tambien se busca en b
        assertTrue(Ops.eq(b, a));
    }

    @Test
    void eqConvierteElResultadoABooleano() {
        assertTrue(Ops.eq(withMeta("__eq", new Fixed(n(0))), new LuaTable()));
        assertFalse(Ops.eq(withMeta("__eq", new Fixed(LuaNil.NIL)), new LuaTable()));
        assertFalse(Ops.eq(withMeta("__eq", new Fixed()), new LuaTable()));
    }

    @Test
    void eqNoSeLlamaConLaMismaReferencia() {
        Fixed h = new Fixed(LuaBoolean.FALSE);
        LuaTable a = withMeta("__eq", h);
        assertTrue(Ops.eq(a, a));
        assertEquals(0, h.calls);
    }

    @Test
    void eqNoSeLlamaSiUnoNoEsTabla() {
        Fixed h = new Fixed(LuaBoolean.TRUE);
        LuaTable a = withMeta("__eq", h);
        assertFalse(Ops.eq(a, n(1)));
        assertFalse(Ops.eq(s("x"), a));
        assertEquals(0, h.calls);
    }

    @Test
    void eqSinMetamétodoEsFalse() {
        assertFalse(Ops.eq(new LuaTable(), new LuaTable()));
    }

    // ---- lt y le ----

    @Test
    void ltUsaMetamétodo() {
        Fixed h = new Fixed(LuaBoolean.TRUE);
        LuaTable a = withMeta("__lt", h);
        LuaTable b = new LuaTable();
        assertTrue(Ops.lt(a, b));
        assertSame(a, h.lastArgs[0]);
        assertSame(b, h.lastArgs[1]);
        // Con un numero tambien, y se busca en b
        assertTrue(Ops.lt(n(1), a));
        assertEquals(n(1), h.lastArgs[0]);
    }

    @Test
    void ltConvierteElResultadoABooleano() {
        assertTrue(Ops.lt(withMeta("__lt", new Fixed(n(0))), new LuaTable()));
        assertFalse(Ops.lt(withMeta("__lt", new Fixed(LuaBoolean.FALSE)), new LuaTable()));
        assertFalse(Ops.lt(withMeta("__lt", new Fixed()), new LuaTable()));
    }

    @Test
    void leUsaMetamétodo() {
        Fixed h = new Fixed(LuaBoolean.TRUE, LuaBoolean.FALSE);
        LuaTable a = withMeta("__le", h);
        assertTrue(Ops.le(a, new LuaTable()));
        assertTrue(Ops.le(new LuaTable(), a));
    }

    @Test
    void leNoCaeEnLt() {
        // En 5.4 __le ausente da error aunque haya __lt
        LuaTable a = withMeta("__lt", new Fixed(LuaBoolean.TRUE));
        LuaError e = assertThrows(LuaError.class, () -> Ops.le(a, new LuaTable()));
        assertEquals(s("attempt to compare two table values"), e.value);
    }

    @Test
    void comparacionSinMetamétodoSigueElError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.lt(new LuaTable(), n(1)));
        assertEquals(s("attempt to compare table with number"), e.value);
    }
}
