import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Los resultados de # salen de lua5.4 (test_cases/03.expected) o de los casos que el manual de Lua deja claros.
/** Pruebas de LuaTable: lectura y escritura, longitud, recorrido con next y modo débil. */
public class LuaTableTest {

    /** Crea un entero de Lua. */
    private static LuaValue n(long v) { return LuaValue.of(v); }

    /** Crea un string de Lua. */
    private static LuaValue s(String v) { return LuaValue.of(v); }

    /** Arma la tabla igual que lo hará el evaluador con el constructor {a, b, c}. */
    private static LuaTable constructor(LuaValue... values) {
        LuaTable t = new LuaTable();
        for (LuaValue v : values) t.appendArray(v);
        return t;
    }

    // ---- rawget y rawset ----

    /** Leer una clave que no existe devuelve nil. */
    @Test
    void missingKeyReturnsNil() {
        LuaTable t = new LuaTable();
        assertSame(LuaNil.NIL, t.rawget(s("nada")));
        assertSame(LuaNil.NIL, t.rawget(n(1)));
        assertSame(LuaNil.NIL, t.rawget(LuaNil.NIL));
    }

    /** Se pueden guardar y leer claves string, entero, booleano, tabla y decimal. */
    @Test
    void storesAndReadsKeysOfEveryType() {
        LuaTable t = new LuaTable();
        LuaTable tableKey = new LuaTable();
        t.rawset(s("name"), s("Analizador"));
        t.rawset(n(7), s("siete"));
        t.rawset(LuaBoolean.TRUE, s("verdad"));
        t.rawset(tableKey, s("tabla"));
        t.rawset(LuaValue.of(1.5), s("uno y medio"));

        assertEquals(s("Analizador"), t.rawget(s("name")));
        assertEquals(s("siete"), t.rawget(n(7)));
        assertEquals(s("verdad"), t.rawget(LuaBoolean.TRUE));
        assertEquals(s("tabla"), t.rawget(tableKey));
        assertEquals(s("uno y medio"), t.rawget(LuaValue.of(1.5)));
        // Otra tabla vacía es otra clave, porque las tablas se comparan por referencia
        assertSame(LuaNil.NIL, t.rawget(new LuaTable()));
    }

    /** La clave 2.0 y la clave 2 son la misma (test_cases/01.in). */
    @Test
    void integralFloatIsSameKey() {
        LuaTable t = constructor(s("entero"));
        t.rawset(LuaValue.of(2.0), s("valor insertado con float"));
        assertEquals(s("valor insertado con float"), t.rawget(n(2)));
        assertEquals(s("valor insertado con float"), t.rawget(LuaValue.of(2.0)));
        assertEquals(2, t.length());
    }

    /** Asignar nil borra la clave. */
    @Test
    void assigningNilDeletes() {
        LuaTable t = new LuaTable();
        t.rawset(s("x"), n(1));
        t.rawset(s("x"), LuaNil.NIL);
        assertSame(LuaNil.NIL, t.rawget(s("x")));
        assertNull(t.next(LuaNil.NIL));
    }

    /** Usar nil o NaN como clave es un error, con el mismo mensaje que Lua. */
    @Test
    void nilOrNanKeyIsError() {
        LuaTable t = new LuaTable();
        LuaError e1 = assertThrows(LuaError.class, () -> t.rawset(LuaNil.NIL, n(1)));
        assertEquals("index is nil", e1.getMessage());
        LuaError e2 = assertThrows(LuaError.class, () -> t.rawset(LuaValue.of(Double.NaN), n(1)));
        assertEquals("index is NaN", e2.getMessage());
    }

    // ---- Longitud (#) ----

    /** Las cuatro longitudes que imprime el caso 03, incluidas las tablas con huecos. */
    @Test
    void lengthsFromCase03() {
        assertEquals(5, constructor(n(10), n(20), n(30), n(40), n(50)).length());
        assertEquals(5, constructor(n(10), n(20), n(30), LuaNil.NIL, n(50)).length());
        assertEquals(3, constructor(LuaNil.NIL, n(20), n(30)).length());
        assertEquals(0, new LuaTable().length());
    }

    /** Llenar la tabla con asignaciones t[i] = v da la longitud esperada. */
    @Test
    void lengthAfterFillingWithAssignments() {
        LuaTable t = new LuaTable();
        for (int i = 1; i <= 100; i++) t.rawset(n(i), n(i * i));
        assertEquals(100, t.length());
        assertEquals(n(49), t.rawget(n(7)));
    }

    /** Si las claves llegan en desorden, # ve la secuencia completa en cuanto se cierra. */
    @Test
    void lengthWithKeysAssignedOutOfOrder() {
        LuaTable t = new LuaTable();
        t.rawset(n(3), s("c"));
        t.rawset(n(2), s("b"));
        assertEquals(0, t.length());
        t.rawset(n(1), s("a"));
        assertEquals(3, t.length());
    }

    /** Borrar el último elemento acorta la longitud. */
    @Test
    void lengthAfterDeletingLast() {
        LuaTable t = constructor(n(1), n(2), n(3));
        t.rawset(n(3), LuaNil.NIL);
        assertEquals(2, t.length());
    }

    // ---- Recorrido con next ----

    /** Recorre la tabla con next, comprueba que ninguna clave se repita y devuelve las claves vistas. */
    private static Set<LuaValue> keysOf(LuaTable t) {
        Set<LuaValue> seen = new HashSet<>();
        for (LuaValue[] e = t.next(LuaNil.NIL); e != null; e = t.next(e[0])) {
            assertTrue(seen.add(e[0]), "clave repetida: " + e[0]);
            assertEquals(t.rawget(e[0]), e[1]);
        }
        return seen;
    }

    /** next visita las claves de la parte array y de la parte hash una sola vez, saltándose los huecos. */
    @Test
    void nextVisitsArrayAndHashOnce() {
        LuaTable t = constructor(n(10), LuaNil.NIL, n(30));
        t.rawset(s("a"), n(1));
        t.rawset(LuaBoolean.FALSE, n(2));
        Set<LuaValue> expected = new HashSet<>();
        expected.add(n(1));
        expected.add(n(3));
        expected.add(s("a"));
        expected.add(LuaBoolean.FALSE);
        assertEquals(expected, keysOf(t));
    }

    /** En una tabla vacía, next termina de inmediato. */
    @Test
    void nextOnEmptyTableEnds() {
        assertNull(new LuaTable().next(LuaNil.NIL));
    }

    /** Se pueden borrar claves mientras se recorre la tabla, como permite pairs en Lua. */
    @Test
    void nextAllowsDeletingWhileIterating() {
        LuaTable t = new LuaTable();
        for (int i = 0; i < 20; i++) t.rawset(s("k" + i), n(i));
        int seen = 0;
        for (LuaValue[] e = t.next(LuaNil.NIL); e != null; e = t.next(e[0])) {
            t.rawset(e[0], LuaNil.NIL);
            seen++;
        }
        assertEquals(20, seen);
        assertNull(t.next(LuaNil.NIL));
    }

    /** Llamar a next con una clave que no está en la tabla es un error. */
    @Test
    void nextWithMissingKeyIsError() {
        LuaTable t = new LuaTable();
        LuaError e = assertThrows(LuaError.class, () -> t.next(s("fantasma")));
        assertEquals("invalid key to 'next'", e.getMessage());
    }

    // ---- Metatabla y modo débil ----

    /** La metatabla se puede asignar, leer y quitar. */
    @Test
    void metatable() {
        LuaTable t = new LuaTable();
        assertNull(t.getMetatable());
        LuaTable mt = new LuaTable();
        t.setMetatable(mt);
        assertSame(mt, t.getMetatable());
        t.setMetatable(null);
        assertNull(t.getMetatable());
    }

    /** Crea una tabla débil con el __mode dado ("k", "v" o "kv"). */
    private static LuaTable weakTable(String mode) {
        LuaTable mt = new LuaTable();
        mt.rawset(s("__mode"), s(mode));
        LuaTable t = new LuaTable();
        t.setMetatable(mt);
        return t;
    }

    // Está en un método aparte para que ninguna variable local siga guardando la clave
    // y el recolector de basura pueda liberarla.
    /** Guarda un valor en t usando como clave una tabla que nadie más conserva. */
    private static void storeWithTemporaryKey(LuaTable t, LuaValue value) {
        LuaTable key = new LuaTable();
        key.rawset(s("key"), s("temporal"));
        t.rawset(key, value);
        assertSame(value, t.rawget(key));
    }

    // Se llama varias veces con pausas porque Java no garantiza que una sola llamada libere todo.
    /** Pide al recolector de basura de Java que libere los objetos sin uso. */
    private static void collectGarbage() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(10);
        }
    }

    /** Una tabla débil queda vacía cuando su clave deja de usarse (test_cases/01.in, sección 2.5). */
    @Test
    void weakTableIsEmptyAfterGc() throws InterruptedException {
        LuaTable t = weakTable("kv");
        LuaTable value = new LuaTable();
        storeWithTemporaryKey(t, value);
        assertTrue(t.next(LuaNil.NIL) != null);
        collectGarbage();
        assertNull(t.next(LuaNil.NIL));
    }

    /** Una tabla débil conserva lo que sigue en uso, y los strings y números nunca se liberan. */
    @Test
    void weakTableKeepsLiveEntries() throws InterruptedException {
        LuaTable t = weakTable("k");
        LuaTable key = new LuaTable();
        t.rawset(key, s("vivo"));
        t.rawset(s("texto"), s("los strings nunca se recolectan"));
        t.rawset(n(1), s("los números tampoco"));
        collectGarbage();
        assertEquals(s("vivo"), t.rawget(key));
        assertEquals(3, keysOf(t).size());
        assertEquals(1, t.length());
    }

    /** Con valores débiles, una entrada desaparece cuando su valor deja de usarse. */
    @Test
    void weakValueDisappearsAfterGc() throws InterruptedException {
        LuaTable t = weakTable("v");
        t.rawset(s("x"), new LuaTable());
        collectGarbage();
        assertSame(LuaNil.NIL, t.rawget(s("x")));
        assertNull(t.next(LuaNil.NIL));
    }

    /** Activar el modo débil en una tabla con datos no pierde ninguna entrada. */
    @Test
    void enablingWeakModeKeepsEntries() {
        LuaTable t = constructor(n(10), n(20));
        t.rawset(s("a"), s("b"));
        LuaTable mt = new LuaTable();
        mt.rawset(s("__mode"), s("k"));
        t.setMetatable(mt);
        assertEquals(n(10), t.rawget(n(1)));
        assertEquals(n(20), t.rawget(n(2)));
        assertEquals(s("b"), t.rawget(s("a")));
        assertEquals(2, t.length());
    }
}
