import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de LuaTable. Los resultados de # salen de lua5.4 real
 * (test_cases/03.expected) o de los casos que el manual de Lua deja claros.
 */
public class LuaTableTest {

    private static LuaValue n(long v) { return LuaValue.of(v); }
    private static LuaValue s(String v) { return LuaValue.of(v); }

    // Simula el constructor {a, b, c} que arma el evaluador.
    private static LuaTable constructor(LuaValue... values) {
        LuaTable t = new LuaTable();
        for (LuaValue v : values) t.appendArray(v);
        return t;
    }

    // ---- rawget / rawset ----

    @Test
    void claveInexistenteDaNil() {
        LuaTable t = new LuaTable();
        assertSame(LuaNil.NIL, t.rawget(s("nada")));
        assertSame(LuaNil.NIL, t.rawget(n(1)));
        assertSame(LuaNil.NIL, t.rawget(LuaNil.NIL));
    }

    @Test
    void guardaYLeeClavesDeTodoTipo() {
        LuaTable t = new LuaTable();
        LuaTable claveTabla = new LuaTable();
        t.rawset(s("name"), s("Analizador"));
        t.rawset(n(7), s("siete"));
        t.rawset(LuaBoolean.TRUE, s("verdad"));
        t.rawset(claveTabla, s("tabla"));
        t.rawset(LuaValue.of(1.5), s("uno y medio"));

        assertEquals(s("Analizador"), t.rawget(s("name")));
        assertEquals(s("siete"), t.rawget(n(7)));
        assertEquals(s("verdad"), t.rawget(LuaBoolean.TRUE));
        assertEquals(s("tabla"), t.rawget(claveTabla));
        assertEquals(s("uno y medio"), t.rawget(LuaValue.of(1.5)));
        // otra tabla vacía es otra clave: se comparan por referencia
        assertSame(LuaNil.NIL, t.rawget(new LuaTable()));
    }

    @Test
    void floatConValorEnteroEsLaMismaClave() {
        // test_cases/01.in: val_table[2.0] = ...; val_table[2] == ...
        LuaTable t = constructor(s("entero"));
        t.rawset(LuaValue.of(2.0), s("valor insertado con float"));
        assertEquals(s("valor insertado con float"), t.rawget(n(2)));
        assertEquals(s("valor insertado con float"), t.rawget(LuaValue.of(2.0)));
        assertEquals(2, t.length());
    }

    @Test
    void asignarNilBorra() {
        LuaTable t = new LuaTable();
        t.rawset(s("x"), n(1));
        t.rawset(s("x"), LuaNil.NIL);
        assertSame(LuaNil.NIL, t.rawget(s("x")));
        assertNull(t.next(LuaNil.NIL));
    }

    @Test
    void claveNilONaNEsError() {
        LuaTable t = new LuaTable();
        LuaError e1 = assertThrows(LuaError.class, () -> t.rawset(LuaNil.NIL, n(1)));
        assertEquals("index is nil", e1.getMessage());
        LuaError e2 = assertThrows(LuaError.class, () -> t.rawset(LuaValue.of(Double.NaN), n(1)));
        assertEquals("index is NaN", e2.getMessage());
    }

    // ---- length (#) ----

    @Test
    void longitudesDelCaso03() {
        assertEquals(5, constructor(n(10), n(20), n(30), n(40), n(50)).length());
        assertEquals(5, constructor(n(10), n(20), n(30), LuaNil.NIL, n(50)).length());
        assertEquals(3, constructor(LuaNil.NIL, n(20), n(30)).length());
        assertEquals(0, new LuaTable().length());
    }

    @Test
    void longitudAlLlenarConAsignaciones() {
        LuaTable t = new LuaTable();
        for (int i = 1; i <= 100; i++) t.rawset(n(i), n(i * i));
        assertEquals(100, t.length());
        assertEquals(n(49), t.rawget(n(7)));
    }

    @Test
    void longitudConClavesAsignadasEnDesorden() {
        // 3 llega antes que 2 y 1: al completarse la secuencia, # la ve entera
        LuaTable t = new LuaTable();
        t.rawset(n(3), s("c"));
        t.rawset(n(2), s("b"));
        assertEquals(0, t.length());
        t.rawset(n(1), s("a"));
        assertEquals(3, t.length());
    }

    @Test
    void longitudTrasBorrarElUltimo() {
        LuaTable t = constructor(n(1), n(2), n(3));
        t.rawset(n(3), LuaNil.NIL);
        assertEquals(2, t.length());
    }

    // ---- next ----

    // Recorre la tabla con next y devuelve las claves vistas.
    private static Set<LuaValue> claves(LuaTable t) {
        Set<LuaValue> vistas = new HashSet<>();
        for (LuaValue[] e = t.next(LuaNil.NIL); e != null; e = t.next(e[0])) {
            assertTrue(vistas.add(e[0]), "clave repetida: " + e[0]);
            assertEquals(t.rawget(e[0]), e[1]);
        }
        return vistas;
    }

    @Test
    void nextRecorreArrayYHashSinRepetir() {
        LuaTable t = constructor(n(10), LuaNil.NIL, n(30));
        t.rawset(s("a"), n(1));
        t.rawset(LuaBoolean.FALSE, n(2));
        Set<LuaValue> esperadas = new HashSet<>();
        esperadas.add(n(1));
        esperadas.add(n(3));
        esperadas.add(s("a"));
        esperadas.add(LuaBoolean.FALSE);
        assertEquals(esperadas, claves(t));
    }

    @Test
    void nextEnTablaVaciaTermina() {
        assertNull(new LuaTable().next(LuaNil.NIL));
    }

    @Test
    void nextPermiteBorrarDuranteElRecorrido() {
        // En Lua es válido asignar nil a campos existentes mientras se recorre con pairs
        LuaTable t = new LuaTable();
        for (int i = 0; i < 20; i++) t.rawset(s("k" + i), n(i));
        int vistas = 0;
        for (LuaValue[] e = t.next(LuaNil.NIL); e != null; e = t.next(e[0])) {
            t.rawset(e[0], LuaNil.NIL);
            vistas++;
        }
        assertEquals(20, vistas);
        assertNull(t.next(LuaNil.NIL));
    }

    @Test
    void nextConClaveQueNoExisteEsError() {
        LuaTable t = new LuaTable();
        LuaError e = assertThrows(LuaError.class, () -> t.next(s("fantasma")));
        assertEquals("invalid key to 'next'", e.getMessage());
    }

    // ---- metatabla y modo débil ----

    @Test
    void metatabla() {
        LuaTable t = new LuaTable();
        assertNull(t.getMetatable());
        LuaTable mt = new LuaTable();
        t.setMetatable(mt);
        assertSame(mt, t.getMetatable());
        t.setMetatable(null);
        assertNull(t.getMetatable());
    }

    private static LuaTable debil(String mode) {
        LuaTable mt = new LuaTable();
        mt.rawset(s("__mode"), s(mode));
        LuaTable t = new LuaTable();
        t.setMetatable(mt);
        return t;
    }

    // En un método aparte para que ninguna variable local retenga la clave.
    private static void guardarConClaveTemporal(LuaTable t, LuaValue valor) {
        LuaTable clave = new LuaTable();
        clave.rawset(s("key"), s("temporal"));
        t.rawset(clave, valor);
        assertSame(valor, t.rawget(clave));
    }

    private static void recolectar() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(10);
        }
    }

    @Test
    void tablaDebilQuedaVaciaTrasElGC() throws InterruptedException {
        // test_cases/01.in, sección 2.5: el valor sigue vivo pero la clave no
        LuaTable t = debil("kv");
        LuaTable valor = new LuaTable();
        guardarConClaveTemporal(t, valor);
        assertTrue(t.next(LuaNil.NIL) != null);
        recolectar();
        assertNull(t.next(LuaNil.NIL));
    }

    @Test
    void tablaDebilConservaLoQueSigueVivo() throws InterruptedException {
        LuaTable t = debil("k");
        LuaTable clave = new LuaTable();
        t.rawset(clave, s("vivo"));
        t.rawset(s("texto"), s("los strings nunca se recolectan"));
        t.rawset(n(1), s("los números tampoco"));
        recolectar();
        assertEquals(s("vivo"), t.rawget(clave));
        assertEquals(3, claves(t).size());
        assertEquals(1, t.length());
    }

    @Test
    void valorDebilDesapareceTrasElGC() throws InterruptedException {
        LuaTable t = debil("v");
        t.rawset(s("x"), new LuaTable());
        recolectar();
        assertSame(LuaNil.NIL, t.rawget(s("x")));
        assertNull(t.next(LuaNil.NIL));
    }

    @Test
    void activarModoDebilConservaLasEntradas() {
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
