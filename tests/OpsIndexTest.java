import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Pruebas de Ops.index, Ops.setIndex y Ops.call (sin metamétodos). */
public class OpsIndexTest {

    private static LuaValue n(long v) { return LuaValue.of(v); }
    private static LuaValue s(String v) { return LuaValue.of(v); }

    // Función nativa que devuelve sus argumentos tal cual.
    private static LuaFunction eco() {
        return new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return args; }
        };
    }

    // ---- index ----

    @Test
    void leeClaveExistenteYAusente() {
        LuaTable t = new LuaTable();
        t.rawset(s("a"), n(1));
        assertEquals(n(1), Ops.index(t, s("a")));
        assertSame(LuaNil.NIL, Ops.index(t, s("b")));
    }

    @Test
    void clavesFloatSeNormalizan() {
        LuaTable t = new LuaTable();
        Ops.setIndex(t, n(2), s("x"));
        assertEquals(s("x"), Ops.index(t, LuaValue.of(2.0)));
        Ops.setIndex(t, LuaValue.of(3.0), s("y"));
        assertEquals(s("y"), Ops.index(t, n(3)));
    }

    @Test
    void indexarNoTablaEsError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.index(LuaNil.NIL, s("x")));
        assertEquals(s("attempt to index a nil value"), e.value);
        e = assertThrows(LuaError.class, () -> Ops.index(n(5), s("x")));
        assertEquals(s("attempt to index a number value"), e.value);
        assertThrows(LuaError.class, () -> Ops.index(LuaBoolean.TRUE, s("x")));
    }

    // ---- setIndex ----

    @Test
    void escribeYBorraConNil() {
        LuaTable t = new LuaTable();
        Ops.setIndex(t, s("k"), n(7));
        assertEquals(n(7), t.rawget(s("k")));
        Ops.setIndex(t, s("k"), LuaNil.NIL);
        assertSame(LuaNil.NIL, Ops.index(t, s("k")));
    }

    @Test
    void escribirEnNoTablaEsError() {
        assertThrows(LuaError.class, () -> Ops.setIndex(LuaNil.NIL, s("k"), n(1)));
        assertThrows(LuaError.class, () -> Ops.setIndex(n(1), s("k"), n(1)));
        assertThrows(LuaError.class, () -> Ops.setIndex(LuaBoolean.FALSE, s("k"), n(1)));
    }

    @Test
    void claveNilOnanEnSetIndexEsError() {
        LuaTable t = new LuaTable();
        LuaError e = assertThrows(LuaError.class, () -> Ops.setIndex(t, LuaNil.NIL, n(1)));
        assertEquals(s("index is nil"), e.value);
        e = assertThrows(LuaError.class, () -> Ops.setIndex(t, LuaValue.of(Double.NaN), n(1)));
        assertEquals(s("index is NaN"), e.value);
    }

    // ---- call ----

    @Test
    void llamaFuncionNativa() {
        LuaValue[] r = Ops.call(eco(), new LuaValue[] {n(1), s("a")});
        assertEquals(2, r.length);
        assertEquals(n(1), r[0]);
        assertEquals(s("a"), r[1]);
    }

    @Test
    void resultadoNullSeConvierteEnNone() {
        LuaFunction f = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return null; }
        };
        assertSame(LuaValue.NONE, Ops.call(f, LuaValue.NONE));
    }

    @Test
    void llamarNoFuncionEsError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.call(LuaNil.NIL, LuaValue.NONE));
        assertEquals(s("attempt to call a nil value"), e.value);
        assertThrows(LuaError.class, () -> Ops.call(new LuaTable(), LuaValue.NONE));
        assertThrows(LuaError.class, () -> Ops.call(n(3), LuaValue.NONE));
    }
}
