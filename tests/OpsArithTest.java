import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Los valores esperados se obtuvieron de lua5.4 (secciones 03_3.4.1, 03_3.4.2 y 03_3.4.3, más casos límite).
/** Pruebas de Ops.arith, unm, bnot y tostring, sin metamétodos. */
public class OpsArithTest {

    /** Crea un entero de Lua. */
    private static LuaValue n(long v) { return LuaValue.of(v); }

    /** Crea un número decimal de Lua. */
    private static LuaValue f(double v) { return LuaValue.of(v); }

    /** Crea un string de Lua. */
    private static LuaValue s(String v) { return LuaValue.of(v); }

    // Revisa el tipo además del texto, porque 2 y 2.0 se imprimen distinto pero un error
    // podría devolver un decimal donde Lua devuelve un entero.
    /** Comprueba que el resultado sea un entero y se imprima como se espera. */
    private static void assertInt(String expected, LuaValue r) {
        assertTrue(r instanceof LuaInteger, "se esperaba entero y fue " + r.getClass().getSimpleName());
        assertEquals(expected, Ops.tostring(r));
    }

    /** Comprueba que el resultado sea un decimal y se imprima como se espera. */
    private static void assertFloat(String expected, LuaValue r) {
        assertTrue(r instanceof LuaFloat, "se esperaba float y fue " + r.getClass().getSimpleName());
        assertEquals(expected, Ops.tostring(r));
    }

    // ---- Aritmética (3.4.1) ----

    /** Entero más entero da entero; si hay un decimal, el resultado es decimal. */
    @Test
    void integerAndMixedAddition() {
        assertInt("15", Ops.arith("+", n(10), n(5)));
        assertFloat("13.14", Ops.arith("+", n(10), f(3.14)));
        assertFloat("0.5", Ops.arith("-", n(3), f(2.5)));
    }

    /** La división / y la potencia ^ siempre dan decimal, aunque los operandos sean enteros. */
    @Test
    void divisionAndPowerAlwaysFloat() {
        assertFloat("3.3333333333333", Ops.arith("/", n(10), n(3)));
        assertFloat("2.0", Ops.arith("/", n(4), n(2)));
        assertFloat("8.0", Ops.arith("^", n(2), n(3)));
        assertFloat("1.4142135623731", Ops.arith("^", n(2), f(0.5)));
    }

    /** La división entera // redondea hacia abajo, también con negativos. */
    @Test
    void floorDivisionRoundsDown() {
        assertInt("3", Ops.arith("//", n(10), n(3)));
        assertInt("-4", Ops.arith("//", n(-10), n(3)));
        assertInt("-4", Ops.arith("//", n(-7), n(2)));
        assertInt("-4", Ops.arith("//", n(7), n(-2)));
        assertFloat("2.0", Ops.arith("//", f(5.5), n(2)));
        assertFloat("-4.0", Ops.arith("//", n(-7), f(2.0)));
    }

    /** El resto % lleva el signo del divisor, como en Lua. */
    @Test
    void moduloTakesDivisorSign() {
        assertInt("1", Ops.arith("%", n(10), n(3)));
        assertInt("2", Ops.arith("%", n(-10), n(3)));
        assertInt("-2", Ops.arith("%", n(7), n(-3)));
        assertInt("-1", Ops.arith("%", n(-7), n(-3)));
        assertFloat("1.0", Ops.arith("%", n(-7), f(2.0)));
        assertFloat("0.5", Ops.arith("%", f(-5.5), n(2)));
        assertFloat("-0.5", Ops.arith("%", f(5.5), n(-2)));
        assertFloat("1.3", Ops.arith("%", f(5.3), n(2)));
        assertFloat("-0.0", Ops.arith("%", f(-0.0), n(1)));
    }

    /** El resto con un divisor infinito da los mismos resultados que Lua. */
    @Test
    void moduloWithInfinity() {
        assertFloat("5.0", Ops.arith("%", n(5), f(Double.POSITIVE_INFINITY)));
        assertFloat("inf", Ops.arith("%", n(-5), f(Double.POSITIVE_INFINITY)));
        assertFloat("-inf", Ops.arith("%", n(5), f(Double.NEGATIVE_INFINITY)));
    }

    /** Dividir entre cero con decimales da infinito en lugar de error. */
    @Test
    void floatDivisionByZero() {
        assertFloat("inf", Ops.arith("//", n(3), f(0.0)));
        assertFloat("-inf", Ops.arith("//", n(-3), f(0.0)));
        assertFloat("inf", Ops.arith("/", n(1), n(0)));
    }

    /** La división entera y el resto entre enteros con divisor cero son errores. */
    @Test
    void integerDivisionByZeroIsError() {
        assertThrows(LuaError.class, () -> Ops.arith("//", n(1), n(0)));
        assertThrows(LuaError.class, () -> Ops.arith("%", n(1), n(0)));
    }

    /** Al pasarse del entero más grande, el resultado da la vuelta al más pequeño, como en Lua. */
    @Test
    void overflowWrapsAround() {
        assertInt("-9223372036854775808", Ops.arith("+", n(Long.MAX_VALUE), n(1)));
        assertInt("-9223372036854775808", Ops.arith("//", n(Long.MIN_VALUE), n(-1)));
        assertInt("0", Ops.arith("%", n(Long.MIN_VALUE), n(-1)));
    }

    /** El menos unario funciona con enteros, decimales y strings numéricos. */
    @Test
    void unaryMinus() {
        assertInt("-10", Ops.unm(n(10)));
        assertFloat("-3.14", Ops.unm(f(3.14)));
        assertInt("-9223372036854775808", Ops.unm(n(Long.MIN_VALUE)));
        assertInt("-2", Ops.unm(s("2")));
        assertInt("-16", Ops.unm(s(" 0x10 ")));
        assertFloat("-2.5", Ops.unm(s("2.5")));
    }

    // ---- Bits (3.4.2) ----

    /** Las operaciones de bits del caso 03 dan los mismos resultados que lua5.4. */
    @Test
    void bitwiseOperations() {
        assertInt("2", Ops.arith("&", n(0xA), n(3)));
        assertInt("11", Ops.arith("|", n(0xA), n(3)));
        assertInt("9", Ops.arith("~", n(0xA), n(3)));
        assertInt("-11", Ops.bnot(n(0xA)));
        assertInt("5", Ops.arith(">>", n(0xA), n(1)));
        assertInt("40", Ops.arith("<<", n(0xA), n(2)));
    }

    /** Desplazamientos negativos, de 64 o más posiciones y con números negativos. */
    @Test
    void shifts() {
        assertInt("5", Ops.arith("<<", n(0xA), n(-1)));
        assertInt("0", Ops.arith(">>", n(0xA), n(100)));
        assertInt("0", Ops.arith("<<", n(1), n(64)));
        assertInt("-9223372036854775808", Ops.arith("<<", n(1), n(63)));
        assertInt("2", Ops.arith(">>", n(1), n(-1)));
        assertInt("9223372036854775807", Ops.arith(">>", n(-1), n(1))); // rellena con ceros, no con el signo
        assertInt("1", Ops.arith(">>", n(-1), n(63)));
        assertInt("0", Ops.arith("<<", n(1), n(-64)));
        assertInt("0", Ops.arith("<<", n(1), n(Long.MIN_VALUE)));
    }

    // ---- Conversiones (3.4.3) ----

    /** En aritmética, un string con un número se convierte, incluidos espacios, signo y hexadecimal. */
    @Test
    void stringToNumberInArithmetic() {
        assertInt("15", Ops.arith("+", s("10"), n(5)));
        assertFloat("21.0", Ops.arith("*", s("10.5"), n(2)));
        assertInt("-10", Ops.arith("+", s(" -10 "), n(0)));
        assertInt("17", Ops.arith("+", s("0x10"), n(1)));
        assertFloat("100.0", Ops.arith("+", s("1e2"), n(0)));
        assertInt("10", Ops.arith("*", s("5"), s("2")));
    }

    /** Un string que Lua no reconoce como número es un error, aunque Java sí lo aceptaría. */
    @Test
    void nonNumericStringIsError() {
        assertThrows(LuaError.class, () -> Ops.arith("+", s("abc"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s(""), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("1d"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("NaN"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("Infinity"), n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", s("1e"), n(1)));
    }

    /** Las operaciones de bits aceptan decimales sin parte fraccionaria, como 10.0. */
    @Test
    void bitwiseWithIntegralFloat() {
        assertInt("0", Ops.arith("&", f(10.0), f(5.0)));
        assertInt("7", Ops.arith("|", n(3), f(4.0)));
        assertInt("-6", Ops.bnot(f(5.0)));
        assertInt("9007199254740992", Ops.arith("|", f(9007199254740992.0), n(0)));
    }

    /** Un decimal con parte fraccionaria, demasiado grande o NaN no sirve en operaciones de bits. */
    @Test
    void bitwiseWithNonIntegralFloatIsError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.arith("&", n(1), f(1.5)));
        assertEquals("number has no integer representation", e.getMessage());
        assertThrows(LuaError.class, () -> Ops.arith("&", f(9.3e18), n(1)));
        assertThrows(LuaError.class, () -> Ops.bnot(f(Double.NaN)));
    }

    /** En operaciones de bits los strings no se convierten a número (CLAUDE.md §6). */
    @Test
    void bitwiseWithStringIsError() {
        assertThrows(LuaError.class, () -> Ops.arith("&", s("10"), n(5)));
        assertThrows(LuaError.class, () -> Ops.arith("<<", n(5), s("1")));
        assertThrows(LuaError.class, () -> Ops.bnot(s("3")));
    }

    // ---- Errores de tipo ----

    /** Operar con nil, tablas o booleanos es un error con el mensaje de Lua. */
    @Test
    void typeErrors() {
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

    // ---- Conversión a texto ----

    /** nil, booleanos, números y strings se muestran igual que con print en Lua. */
    @Test
    void tostringOfSimpleValues() {
        assertEquals("nil", Ops.tostring(LuaNil.NIL));
        assertEquals("true", Ops.tostring(LuaBoolean.TRUE));
        assertEquals("false", Ops.tostring(LuaBoolean.FALSE));
        assertEquals("42", Ops.tostring(n(42)));
        assertEquals("-9223372036854775808", Ops.tostring(n(Long.MIN_VALUE)));
        assertEquals("3.0", Ops.tostring(f(3.0)));
        assertEquals("1e+15", Ops.tostring(f(1e15)));
        assertEquals("hola", Ops.tostring(s("hola")));
    }

    /** Tablas y funciones se muestran con su tipo y un número que identifica al objeto. */
    @Test
    void tostringOfTableAndFunction() {
        LuaTable t = new LuaTable();
        assertEquals(String.format("table: 0x%08x", System.identityHashCode(t)), Ops.tostring(t));
        LuaFunction fn = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) { return NONE; }
            @Override public String typeName() { return "function"; }
        };
        assertEquals(String.format("function: 0x%08x", System.identityHashCode(fn)), Ops.tostring(fn));
    }
}
