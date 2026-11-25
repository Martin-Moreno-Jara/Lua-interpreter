import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pruebas de los metamétodos __index, __newindex y __call. */
public class OpsAccessMetaTest {

    /** Crea un entero de Lua. */
    private static LuaValue n(long v) { return LuaValue.of(v); }

    /** Crea un string de Lua. */
    private static LuaValue s(String v) { return LuaValue.of(v); }

    /** Crea una tabla cuya metatabla define un solo metamétodo, que puede ser función o tabla. */
    private static LuaTable withMeta(String event, LuaValue h) {
        LuaTable t = new LuaTable();
        LuaTable mt = new LuaTable();
        mt.rawset(s(event), h);
        t.setMetatable(mt);
        return t;
    }

    /** Función nativa que devuelve sus argumentos tal cual. */
    private static LuaFunction echo() {
        return new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return args; }
        };
    }

    /** Función nativa que falla si alguien la llama. */
    private static LuaFunction mustNotBeCalled() {
        return new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { throw new LuaError("no debe llamarse"); }
        };
    }

    // ---- __index ----

    /** __index como función recibe la tabla y la clave, y se usa su primer resultado. */
    @Test
    void indexFunctionReceivesTableAndKey() {
        final LuaValue[] seen = new LuaValue[2];
        LuaTable t = withMeta("__index", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) {
                seen[0] = a[0];
                seen[1] = a[1];
                return new LuaValue[] { n(7), n(8) };
            }
        });
        assertEquals(n(7), Ops.index(t, s("x")));
        assertSame(t, seen[0]);
        assertEquals(s("x"), seen[1]);
    }

    /** Si __index no devuelve nada, el resultado es nil. */
    @Test
    void indexFunctionWithoutResultsGivesNil() {
        LuaTable t = withMeta("__index", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) { return LuaValue.NONE; }
        });
        assertSame(LuaNil.NIL, Ops.index(t, s("x")));
    }

    /** __index como tabla: la clave que falta se busca en esa otra tabla. */
    @Test
    void indexTable() {
        LuaTable base = new LuaTable();
        base.rawset(s("a"), n(1));
        LuaTable t = withMeta("__index", base);
        assertEquals(n(1), Ops.index(t, s("a")));
        assertSame(LuaNil.NIL, Ops.index(t, s("b")));
    }

    /** La búsqueda sigue una cadena de tres tablas enlazadas por __index. */
    @Test
    void indexChainOfThreeTables() {
        LuaTable c = new LuaTable();
        c.rawset(s("k"), n(3));
        LuaTable b = withMeta("__index", c);
        LuaTable a = withMeta("__index", b);
        assertEquals(n(3), Ops.index(a, s("k")));
    }

    /** Una clave que ya existe no consulta __index. */
    @Test
    void existingKeySkipsIndex() {
        LuaTable t = withMeta("__index", mustNotBeCalled());
        t.rawset(s("x"), n(5));
        assertEquals(n(5), Ops.index(t, s("x")));
    }

    /** Dos tablas que se apuntan entre sí con __index producen un error en vez de colgarse. */
    @Test
    void indexLoopIsError() {
        LuaTable a = new LuaTable();
        LuaTable b = withMeta("__index", a);
        LuaTable mt = new LuaTable();
        mt.rawset(s("__index"), b);
        a.setMetatable(mt);
        LuaError e = assertThrows(LuaError.class, () -> Ops.index(a, s("x")));
        assertTrue(Ops.tostring(e.value).contains("chain too long"));
    }

    // ---- __newindex ----

    /** __newindex como función recibe la tabla, la clave y el valor, y la tabla no se modifica. */
    @Test
    void newindexFunction() {
        final LuaValue[] seen = new LuaValue[3];
        LuaTable t = withMeta("__newindex", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] a) {
                seen[0] = a[0];
                seen[1] = a[1];
                seen[2] = a[2];
                return LuaValue.NONE;
            }
        });
        Ops.setIndex(t, s("x"), n(9));
        assertSame(t, seen[0]);
        assertEquals(s("x"), seen[1]);
        assertEquals(n(9), seen[2]);
        assertSame(LuaNil.NIL, t.rawget(s("x")));
    }

    /** Escribir en una clave que ya existe no llama a __newindex. */
    @Test
    void existingKeySkipsNewindex() {
        LuaTable t = withMeta("__newindex", mustNotBeCalled());
        t.rawset(s("x"), n(1));
        Ops.setIndex(t, s("x"), n(2));
        assertEquals(n(2), t.rawget(s("x")));
    }

    /** __newindex como tabla: el valor se escribe en esa otra tabla. */
    @Test
    void newindexTableWritesToOther() {
        LuaTable other = new LuaTable();
        LuaTable t = withMeta("__newindex", other);
        Ops.setIndex(t, s("x"), n(4));
        assertEquals(n(4), other.rawget(s("x")));
        assertSame(LuaNil.NIL, t.rawget(s("x")));
    }

    /** Una tabla cuyo __newindex es ella misma produce un error en vez de colgarse. */
    @Test
    void newindexLoopIsError() {
        LuaTable a = new LuaTable();
        LuaTable mt = new LuaTable();
        mt.rawset(s("__newindex"), a);
        a.setMetatable(mt);
        LuaError e = assertThrows(LuaError.class, () -> Ops.setIndex(a, s("x"), n(1)));
        assertTrue(Ops.tostring(e.value).contains("chain too long"));
    }

    // ---- __call ----

    /** Llamar a una tabla con __call le pasa la tabla como primer argumento. */
    @Test
    void callPrependsObject() {
        LuaTable t = withMeta("__call", echo());
        LuaValue[] r = Ops.call(t, new LuaValue[] { n(1), n(2) });
        assertEquals(3, r.length);
        assertSame(t, r[0]);
        assertEquals(n(1), r[1]);
        assertEquals(n(2), r[2]);
    }

    /** Si __call es otra tabla con __call, se sigue la cadena y cada tabla se agrega como argumento. */
    @Test
    void callWhoseValueIsTableWithCall() {
        LuaTable inner = withMeta("__call", echo());
        LuaTable t = withMeta("__call", inner);
        LuaValue[] r = Ops.call(t, new LuaValue[] { n(1) });
        assertEquals(3, r.length);
        assertSame(inner, r[0]);
        assertSame(t, r[1]);
        assertEquals(n(1), r[2]);
    }

    // ---- Errores ----

    /** Sin metamétodos se mantienen los errores de indexar y llamar. */
    @Test
    void errorsWithoutMetamethod() {
        LuaError e1 = assertThrows(LuaError.class, () -> Ops.index(n(1), s("x")));
        assertEquals("attempt to index a number value", Ops.tostring(e1.value));
        LuaError e2 = assertThrows(LuaError.class, () -> Ops.setIndex(LuaNil.NIL, s("x"), n(1)));
        assertEquals("attempt to index a nil value", Ops.tostring(e2.value));
        LuaError e3 = assertThrows(LuaError.class, () -> Ops.call(new LuaTable(), LuaValue.NONE));
        assertEquals("attempt to call a table value", Ops.tostring(e3.value));
    }
}
