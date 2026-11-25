import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pruebas de Ops.arith, unm, bnot y tostring. Los valores esperados salen de
 * lua5.4 real (secciones 03_3.4.1, 03_3.4.2 y 03_3.4.3 y bordes comprobados a mano).
 */
public class OpsArithTest {

    private static LuaValue n(long v) { return LuaValue.of(v); }
    private static LuaValue f(double v) { return LuaValue.of(v); }
    private static LuaValue s(String v) { return LuaValue.of(v); }

    // Compara texto y subtipo, para que un int no pase por float ni al revés.
    private static void esInt(String esperado, LuaValue r) {
        assertTrue(r instanceof LuaInteger, "se esperaba entero y fue " + r.getClass().getSimpleName());
        assertEquals(esperado, Ops.tostring(r));
    }

    private static void esFloat(String esperado, LuaValue r) {
        assertTrue(r instanceof LuaFloat, "se esperaba float y fue " + r.getClass().getSimpleName());
        assertEquals(esperado, Ops.tostring(r));
    }

    // ---- 3.4.1 aritmética ----

    @Test
    void sumaEnteraYMixta() {
        esInt("15", Ops.arith("+", n(10), n(5)));
        esFloat("13.14", Ops.arith("+", n(10), f(3.14)));
        esFloat("0.5", Ops.arith("-", n(3), f(2.5)));
    }

    @Test
    void divisionYPotenciaSiempreFloat() {
        esFloat("3.3333333333333", Ops.arith("/", n(10), n(3)));
        esFloat("2.0", Ops.arith("/", n(4), n(2)));
        esFloat("8.0", Ops.arith("^", n(2), n(3)));
        esFloat("1.4142135623731", Ops.arith("^", n(2), f(0.5)));
    }

    @Test
    void divisionEnteraRedondeaHaciaMenosInfinito() {
        esInt("3", Ops.arith("//", n(10), n(3)));
        esInt("-4", Ops.arith("//", n(-10), n(3)));
        esInt("-4", Ops.arith("//", n(-7), n(2)));
        esInt("-4", Ops.arith("//", n(7), n(-2)));
        esFloat("2.0", Ops.arith("//", f(5.5), n(2)));
        esFloat("-4.0", Ops.arith("//", n(-7), f(2.0)));
    }

    @Test
    void moduloTomaElSignoDelDivisor() {
        esInt("1", Ops.arith("%", n(10), n(3)));
        esInt("2", Ops.arith("%", n(-10), n(3)));
        esInt("-2", Ops.arith("%", n(7), n(-3)));
        esInt("-1", Ops.arith("%", n(-7), n(-3)));
        esFloat("1.0", Ops.arith("%", n(-7), f(2.0)));
        esFloat("0.5", Ops.arith("%", f(-5.5), n(2)));
        esFloat("-0.5", Ops.arith("%", f(5.5), n(-2)));
        esFloat("1.3", Ops.arith("%", f(5.3), n(2)));
        esFloat("-0.0", Ops.arith("%", f(-0.0), n(1)));
    }

    @Test
    void moduloConInfinito() {
        esFloat("5.0", Ops.arith("%", n(5), f(Double.POSITIVE_INFINITY)));
        esFloat("inf", Ops.arith("%", n(-5), f(Double.POSITIVE_INFINITY)));
        esFloat("-inf", Ops.arith("%", n(5), f(Double.NEGATIVE_INFINITY)));
    }

    @Test
    void divisionPorCeroFloat() {
        esFloat("inf", Ops.arith("//", n(3), f(0.0)));
        esFloat("-inf", Ops.arith("//", n(-3), f(0.0)));
        esFloat("inf", Ops.arith("/", n(1), n(0)));
    }

    @Test
    void divisionEnteraOModuloEnteroPorCeroFalla() {
        assertThrows(LuaError.class, () -> Ops.arith("//", n(1), n(0)));
        assertThrows(LuaError.class, () -> Ops.arith("%", n(1), n(0)));
    }

    @Test
    void desbordamientoDaLaVuelta() {
        esInt("-9223372036854775808", Ops.arith("+", n(Long.MAX_VALUE), n(1)));
        esInt("-9223372036854775808", Ops.arith("//", n(Long.MIN_VALUE), n(-1)));
        esInt("0", Ops.arith("%", n(Long.MIN_VALUE), n(-1)));
    }

    @Test
    void menosUnario() {
        esInt("-10", Ops.unm(n(10)));
        esFloat("-3.14", Ops.unm(f(3.14)));
        esInt("-9223372036854775808", Ops.unm(n(Long.MIN_VALUE)));
        esInt("-2", Ops.unm(s("2")));
        esInt("-16", Ops.unm(s(" 0x10 ")));
        esFloat("-2.5", Ops.unm(s("2.5")));
    }

    // ---- 3.4.2 bits ----

    @Test
    void operacionesDeBits() {
        esInt("2", Ops.arith("&", n(0xA), n(3)));
        esInt("11", Ops.arith("|", n(0xA), n(3)));
        esInt("9", Ops.arith("~", n(0xA), n(3)));
        esInt("-11", Ops.bnot(n(0xA)));
        esInt("5", Ops.arith(">>", n(0xA), n(1)));
        esInt("40", Ops.arith("<<", n(0xA), n(2)));
    }

    @Test
    void desplazamientos() {
        esInt("5", Ops.arith("<<", n(0xA), n(-1)));
        esInt("0", Ops.arith(">>", n(0xA), n(100)));
        esInt("0", Ops.arith("<<", n(1), n(64)));
        esInt("-9223372036854775808", Ops.arith("<<", n(1), n(63)));
        esInt("2", Ops.arith(">>", n(1), n(-1)));
        esInt("9223372036854775807", Ops.arith(">>", n(-1), n(1))); // lógico, no aritmético
        esInt("1", Ops.arith(">>", n(-1), n(63)));
        esInt("0", Ops.arith("<<", n(1), n(-64)));
        esInt("0", Ops.arith("<<", n(1), n(Long.MIN_VALUE)));
    }

    // ---- 3.4.3 coerciones ----

    @Test
    void stringANumeroEnAritmetica() {
        esInt("15", Ops.arith("+", s("10"), n(5)));
        esFloat("21.0", Ops.arith("*", s("10.5"), n(2)));
        esInt("-10", Ops.arith("+", s(" -10 "), n(0)));
        esInt("17", Ops.arith("+", s("0x10"), n(1)));
        esFloat("100.0", Ops.arith("+", s("1e2"), n(0)));
        esInt("10", Ops.arith("*", s("5"), s("2")));
    }

    @Test
    void stringNoNumericoFalla() {
        assertThrows(LuaError.class, () -> Ops.arith("+", s("abc"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s(""), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("1d"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("NaN"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("Infinity"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("1e"), n(1)));
    }

    @Test
    void bitsConFloatEnteroExacto() {
        esInt("0", Ops.arith("&", f(10.0), f(5.0)));
        esInt("7", Ops.arith("|", n(3), f(4.0)));
        esInt("-6", Ops.bnot(f(5.0)));
        esInt("9007199254740992", Ops.arith("|", f(9007199254740992.0), n(0)));
    }

    @Test
    void bitsConFloatNoEnteroFalla() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.arith("&", n(1), f(1.5)));
        assertEquals("number has no integer representation", e.getMessage());
        assertThrows(LuaError.class, () -> Ops.arith("&", f(9.3e18), n(1)));
        assertThrows(LuaError.class, () -> Ops.bnot(f(Double.NaN)));
    }

    @Test
    void bitsConStringFalla() {
        assertThrows(LuaError.class, () -> Ops.arith("&", s("10"), n(5)));
        assertThrows(LuaError.class, () -> Ops.arith("<<", n(5), s("1")));
        assertThrows(LuaError.class, () -> Ops.bnot(s("3")));
    }

    // ---- errores de tipo ----

    @Test
    void erroresDeTipo() {
        assertThrows(LuaError.class, () -> Ops.arith("+", LuaNil.NIL, n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("*", n(1), new LuaTable()));
        assertThrows(LuaError.class, () -> Ops.arith("|", LuaBoolean.TRUE, n(1)));
        assertThrows(LuaError.class, () -> Ops.unm(new LuaTable()));
        assertThrows(LuaError.class, () -> Ops.bnot(LuaNil.NIL));
        LuaError e = assertThrows(LuaError.class, () -> Ops.arith("+", n(1), LuaNil.NIL));
        assertEquals("attempt to perform arithmetic on a nil value", e.getMessage());
        e = assertThrows(LuaError.class, () -> Ops.arith("&", n(1), LuaNil.NIL));
        assertEquals("attempt to perform bitwise operation on a nil value", e.getMessage());
    }

    // ---- tostring ----

    @Test
    void tostringDeValoresSimples() {
        assertEquals("nil", Ops.tostring(LuaNil.NIL));
        assertEquals("true", Ops.tostring(LuaBoolean.TRUE));
        assertEquals("false", Ops.tostring(LuaBoolean.FALSE));
        assertEquals("42", Ops.tostring(n(42)));
        assertEquals("-9223372036854775808", Ops.tostring(n(Long.MIN_VALUE)));
        assertEquals("3.0", Ops.tostring(f(3.0)));
        assertEquals("1e+15", Ops.tostring(f(1e15)));
        assertEquals("hola", Ops.tostring(s("hola")));
    }

    @Test
    void tostringDeTablaYFuncion() {
        LuaTable t = new LuaTable();
        assertEquals(String.format("table: 0x%08x", System.identityHashCode(t)), Ops.tostring(t));
        LuaFunction fn = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return NONE; }
            @Override public String typeName() { return "function"; }
        };
        assertEquals(String.format("function: 0x%08x", System.identityHashCode(fn)), Ops.tostring(fn));
    }
}
