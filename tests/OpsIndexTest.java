import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Pruebas de Ops.index, setIndex y call, sin metamétodos. */
public class OpsIndexTest {

    /** Crea un entero de Lua. */
    private static LuaValue n(long v) { return LuaValue.of(v); }

    /** Crea un string de Lua. */
    private static LuaValue s(String v) { return LuaValue.of(v); }

    /** Función nativa que devuelve sus argumentos tal cual. */
    private static LuaFunction echo() {
        return new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return args; }
        };
    }

    // ---- Lectura (t[k]) ----

    /** Una clave existente devuelve su valor y una ausente devuelve nil. */
    @Test
    void readsExistingAndMissingKey() {
        LuaTable t = new LuaTable();
        t.rawset(s("a"), n(1));
        assertEquals(n(1), Ops.index(t, s("a")));
        assertSame(LuaNil.NIL, Ops.index(t, s("b")));
    }

    /** t[2.0] y t[2] son la misma clave, al leer y al escribir. */
    @Test
    void floatKeysAreNormalized() {
        LuaTable t = new LuaTable();
        Ops.setIndex(t, n(2), s("x"));
        assertEquals(s("x"), Ops.index(t, LuaValue.of(2.0)));
        Ops.setIndex(t, LuaValue.of(3.0), s("y"));
        assertEquals(s("y"), Ops.index(t, n(3)));
    }

    /** Indexar nil, un número o un booleano es un error. */
    @Test
    void indexingNonTableIsError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.index(LuaNil.NIL, s("x")));
        assertEquals(s("attempt to index a nil value"), e.value);
        e = assertThrows(LuaError.class, () -> Ops.index(n(5), s("x")));
        assertEquals(s("attempt to index a number value"), e.value);
        assertThrows(LuaError.class, () -> Ops.index(LuaBoolean.TRUE, s("x")));
    }

    // ---- Escritura (t[k] = v) ----

    /** Se puede escribir una clave y borrarla asignando nil. */
    @Test
    void writesAndDeletesWithNil() {
        LuaTable t = new LuaTable();
        Ops.setIndex(t, s("k"), n(7));
        assertEquals(n(7), t.rawget(s("k")));
        Ops.setIndex(t, s("k"), LuaNil.NIL);
        assertSame(LuaNil.NIL, Ops.index(t, s("k")));
    }

    /** Escribir en nil, un número o un booleano es un error. */
    @Test
    void writingToNonTableIsError() {
        assertThrows(LuaError.class, () -> Ops.setIndex(LuaNil.NIL, s("k"), n(1)));
        assertThrows(LuaError.class, () -> Ops.setIndex(n(1), s("k"), n(1)));
        assertThrows(LuaError.class, () -> Ops.setIndex(LuaBoolean.FALSE, s("k"), n(1)));
    }

    /** Usar nil o NaN como clave al escribir es un error, con el mensaje de Lua. */
    @Test
    void nilOrNanKeyIsError() {
        LuaTable t = new LuaTable();
        LuaError e = assertThrows(LuaError.class, () -> Ops.setIndex(t, LuaNil.NIL, n(1)));
        assertEquals(s("index is nil"), e.value);
        e = assertThrows(LuaError.class, () -> Ops.setIndex(t, LuaValue.of(Double.NaN), n(1)));
        assertEquals(s("index is NaN"), e.value);
    }

    // ---- Llamadas ----

    /** Llamar a una función nativa le pasa los argumentos y devuelve sus resultados. */
    @Test
    void callsNativeFunction() {
        LuaValue[] r = Ops.call(echo(), new LuaValue[] { n(1), s("a") });
        assertEquals(2, r.length);
        assertEquals(n(1), r[0]);
        assertEquals(s("a"), r[1]);
    }

    /** Si una función nativa devuelve null por error, la llamada devuelve una lista vacía. */
    @Test
    void nullResultBecomesNone() {
        LuaFunction f = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return null; }
        };
        assertSame(LuaValue.NONE, Ops.call(f, LuaValue.NONE));
    }

    /** Llamar a algo que no es función ni tiene __call es un error. */
    @Test
    void callingNonFunctionIsError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.call(LuaNil.NIL, LuaValue.NONE));
        assertEquals(s("attempt to call a nil value"), e.value);
        assertThrows(LuaError.class, () -> Ops.call(new LuaTable(), LuaValue.NONE));
        assertThrows(LuaError.class, () -> Ops.call(n(3), LuaValue.NONE));
    }
}
