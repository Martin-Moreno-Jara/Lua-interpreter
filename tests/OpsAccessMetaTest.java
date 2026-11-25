import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pruebas de __index, __newindex y __call en Ops. */
public class OpsAccessMetaTest {

    private static LuaValue n(long v) { return LuaValue.of(v); }
    private static LuaValue s(String v) { return LuaValue.of(v); }

    private static LuaTable conMeta(String evento, LuaValue h) {
        LuaTable t = new LuaTable();
        LuaTable mt = new LuaTable();
        mt.rawset(s(evento), h);
        t.setMetatable(mt);
        return t;
    }

    private static LuaFunction eco() {
        return new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return args; }
        };
    }

    @Test
    void indexFuncionRecibeTablaYClave() {
        final LuaValue[] visto = new LuaValue[2];
        LuaTable t = conMeta("__index", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) {
                visto[0] = a[0]; visto[1] = a[1];
                return new LuaValue[] { n(7), n(8) };
            }
        });
        assertEquals(n(7), Ops.index(t, s("x")));
        assertSame(t, visto[0]);
        assertEquals(s("x"), visto[1]);
    }

    @Test
    void indexFuncionSinResultadosDaNil() {
        LuaTable t = conMeta("__index", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) { return LuaValue.NONE; }
        });
        assertSame(LuaNil.NIL, Ops.index(t, s("x")));
    }

    @Test
    void indexTabla() {
        LuaTable base = new LuaTable();
        base.rawset(s("a"), n(1));
        LuaTable t = conMeta("__index", base);
        assertEquals(n(1), Ops.index(t, s("a")));
        assertSame(LuaNil.NIL, Ops.index(t, s("b")));
    }

    @Test
    void indexCadenaDeTresTablas() {
        LuaTable c = new LuaTable();
        c.rawset(s("k"), n(3));
        LuaTable b = conMeta("__index", c);
        LuaTable a = conMeta("__index", b);
        assertEquals(n(3), Ops.index(a, s("k")));
    }

    @Test
    void claveExistenteNoConsultaIndex() {
        LuaTable t = conMeta("__index", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) { throw new LuaError("no debe llamarse"); }
        });
        t.rawset(s("x"), n(5));
        assertEquals(n(5), Ops.index(t, s("x")));
    }

    @Test
    void bucleDeIndexLanzaError() {
        LuaTable a = new LuaTable();
        LuaTable b = conMeta("__index", a);
        LuaTable mt = new LuaTable();
        mt.rawset(s("__index"), b);
        a.setMetatable(mt);
        LuaError e = assertThrows(LuaError.class, () -> Ops.index(a, s("x")));
        assertTrue(Ops.tostring(e.value).contains("chain too long"));
    }

    @Test
    void newindexFuncion() {
        final LuaValue[] visto = new LuaValue[3];
        LuaTable t = conMeta("__newindex", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) {
                visto[0] = a[0]; visto[1] = a[1]; visto[2] = a[2];
                return LuaValue.NONE;
            }
        });
        Ops.setIndex(t, s("x"), n(9));
        assertSame(t, visto[0]);
        assertEquals(s("x"), visto[1]);
        assertEquals(n(9), visto[2]);
        assertSame(LuaNil.NIL, t.rawget(s("x")));
    }

    @Test
    void claveExistenteNoLlamaNewindex() {
        LuaTable t = conMeta("__newindex", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) { throw new LuaError("no debe llamarse"); }
        });
        t.rawset(s("x"), n(1));
        Ops.setIndex(t, s("x"), n(2));
        assertEquals(n(2), t.rawget(s("x")));
    }

    @Test
    void newindexTablaEscribeEnLaOtra() {
        LuaTable otra = new LuaTable();
        LuaTable t = conMeta("__newindex", otra);
        Ops.setIndex(t, s("x"), n(4));
        assertEquals(n(4), otra.rawget(s("x")));
        assertSame(LuaNil.NIL, t.rawget(s("x")));
    }

    @Test
    void bucleDeNewindexLanzaError() {
        LuaTable a = new LuaTable();
        LuaTable mt = new LuaTable();
        mt.rawset(s("__newindex"), a);
        a.setMetatable(mt);
        LuaError e = assertThrows(LuaError.class, () -> Ops.setIndex(a, s("x"), n(1)));
        assertTrue(Ops.tostring(e.value).contains("chain too long"));
    }

    @Test
    void callAnteponeElObjeto() {
        LuaTable t = conMeta("__call", eco());
        LuaValue[] r = Ops.call(t, new LuaValue[] { n(1), n(2) });
        assertEquals(3, r.length);
        assertSame(t, r[0]);
        assertEquals(n(1), r[1]);
        assertEquals(n(2), r[2]);
    }

    @Test
    void callCuyoValorEsTablaConCall() {
        LuaTable interna = conMeta("__call", eco());
        LuaTable t = conMeta("__call", interna);
        LuaValue[] r = Ops.call(t, new LuaValue[] { n(1) });
        assertEquals(3, r.length);
        assertSame(interna, r[0]);
        assertSame(t, r[1]);
        assertEquals(n(1), r[2]);
    }

    @Test
    void erroresSinMetametodo() {
        LuaError e1 = assertThrows(LuaError.class, () -> Ops.index(n(1), s("x")));
        assertEquals("attempt to index a number value", Ops.tostring(e1.value));
        LuaError e2 = assertThrows(LuaError.class, () -> Ops.setIndex(LuaNil.NIL, s("x"), n(1)));
        assertEquals("attempt to index a nil value", Ops.tostring(e2.value));
        LuaError e3 = assertThrows(LuaError.class, () -> Ops.call(new LuaTable(), LuaValue.NONE));
        assertEquals("attempt to call a table value", Ops.tostring(e3.value));
    }
}
