import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pruebas de los metamétodos de operadores: aritmética, bits, concatenación, longitud e igualdad y orden. */
public class OpsMetaTest {

    /** Crea un entero de Lua. */
    private static LuaValue n(long v) { return LuaValue.of(v); }

    /** Crea un string de Lua. */
    private static LuaValue s(String v) { return LuaValue.of(v); }

    /** Metamétodo de prueba que siempre devuelve los mismos resultados y recuerda cómo lo llamaron. */
    private static class Fixed extends LuaFunction {
        final LuaValue[] result;
        int calls = 0;
        LuaValue[] lastArgs;

        /** Crea el metamétodo con los resultados que devolverá. */
        Fixed(LuaValue... result) { this.result = result; }

        @Override public String typeName() { return "function"; }

        @Override public LuaValue[] call(LuaValue[] args) {
            calls++;
            lastArgs = args;
            return result;
        }
    }

    /** Crea una tabla cuya metatabla define un solo metamétodo. */
    private static LuaTable withMeta(String event, LuaFunction h) {
        LuaTable mt = new LuaTable();
        mt.rawset(s(event), h);
        LuaTable t = new LuaTable();
        t.setMetatable(mt);
        return t;
    }

    // ---- Aritmética y bits ----

    /** __add recibe los dos operandos en orden y su resultado es el de la suma. */
    @Test
    void add() {
        Fixed h = new Fixed(n(42));
        LuaTable obj = withMeta("__add", h);
        assertEquals(n(42), Ops.arith("+", obj, n(1)));
        assertSame(obj, h.lastArgs[0]);
        assertEquals(n(1), h.lastArgs[1]);
    }

    /** La resta usa __sub. */
    @Test
    void sub() {
        assertEquals(n(7), Ops.arith("-", withMeta("__sub", new Fixed(n(7))), n(1)));
    }

    /** La división entera usa __idiv. */
    @Test
    void idiv() {
        assertEquals(n(3), Ops.arith("//", withMeta("__idiv", new Fixed(n(3))), n(2)));
    }

    /** El & de bits usa __band. */
    @Test
    void band() {
        assertEquals(n(1), Ops.arith("&", withMeta("__band", new Fixed(n(1))), n(3)));
    }

    /** El desplazamiento a la izquierda usa __shl. */
    @Test
    void shl() {
        assertEquals(n(8), Ops.arith("<<", withMeta("__shl", new Fixed(n(8))), n(3)));
    }

    /** Cada uno de los demás operadores usa su propio metamétodo. */
    @Test
    void otherOperators() {
        assertEquals(n(1), Ops.arith("*", withMeta("__mul", new Fixed(n(1))), n(3)));
        assertEquals(n(2), Ops.arith("/", withMeta("__div", new Fixed(n(2))), n(3)));
        assertEquals(n(3), Ops.arith("%", withMeta("__mod", new Fixed(n(3))), n(3)));
        assertEquals(n(4), Ops.arith("^", withMeta("__pow", new Fixed(n(4))), n(3)));
        assertEquals(n(5), Ops.arith("|", withMeta("__bor", new Fixed(n(5))), n(3)));
        assertEquals(n(6), Ops.arith("~", withMeta("__bxor", new Fixed(n(6))), n(3)));
        assertEquals(n(7), Ops.arith(">>", withMeta("__shr", new Fixed(n(7))), n(3)));
    }

    /** __unm recibe el operando dos veces, igual que en Lua. */
    @Test
    void unmReceivesOperandTwice() {
        Fixed h = new Fixed(n(-1));
        LuaTable obj = withMeta("__unm", h);
        assertEquals(n(-1), Ops.unm(obj));
        assertEquals(2, h.lastArgs.length);
        assertSame(obj, h.lastArgs[0]);
        assertSame(obj, h.lastArgs[1]);
    }

    /** __bnot recibe el operando dos veces, igual que en Lua. */
    @Test
    void bnotReceivesOperandTwice() {
        Fixed h = new Fixed(n(9));
        LuaTable obj = withMeta("__bnot", h);
        assertEquals(n(9), Ops.bnot(obj));
        assertSame(obj, h.lastArgs[0]);
        assertSame(obj, h.lastArgs[1]);
    }

    /** ~obj no usa __bxor, que es el ~ de dos operandos. */
    @Test
    void bnotDoesNotUseBxor() {
        assertThrows(LuaError.class, () -> Ops.bnot(withMeta("__bxor", new Fixed(n(1)))));
    }

    /** El metamétodo se busca primero en el operando izquierdo y luego en el derecho. */
    @Test
    void looksUpLeftThenRight() {
        Fixed ha = new Fixed(s("a"));
        Fixed hb = new Fixed(s("b"));
        LuaTable a = withMeta("__add", ha);
        LuaTable b = withMeta("__add", hb);
        // Con un número a la izquierda se usa el de b
        assertEquals(s("b"), Ops.arith("+", n(5), b));
        assertEquals(n(5), hb.lastArgs[0]);
        assertSame(b, hb.lastArgs[1]);
        // Si los dos tienen, gana el de a
        assertEquals(s("a"), Ops.arith("+", a, b));
        assertEquals(1, ha.calls);
        assertEquals(1, hb.calls);
    }

    /** Un string numérico con una tabla usa el metamétodo de la tabla. */
    @Test
    void numericStringWithTable() {
        assertEquals(n(1), Ops.arith("+", s("10"), withMeta("__add", new Fixed(n(1)))));
    }

    /** Si el metamétodo devuelve varios valores, solo se usa el primero. */
    @Test
    void resultIsTruncatedToOneValue() {
        assertEquals(n(1), Ops.arith("+", withMeta("__add", new Fixed(n(1), n(2))), n(1)));
    }

    /** Si el metamétodo no devuelve nada, el resultado es nil. */
    @Test
    void noResultGivesNil() {
        assertSame(LuaNil.NIL, Ops.arith("+", withMeta("__add", new Fixed()), n(1)));
    }

    /** Sin metamétodo se mantienen los mismos errores de antes. */
    @Test
    void withoutMetamethodKeepsError() {
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

    /** Un metamétodo de otra operación no sirve: una tabla con __sub no se puede sumar. */
    @Test
    void otherEventDoesNotApply() {
        LuaTable t = withMeta("__sub", new Fixed(n(1)));
        assertThrows(LuaError.class, () -> Ops.arith("+", t, n(1)));
    }

    // ---- Concatenación y longitud ----

    /** __concat se usa aunque la tabla esté a la derecha y recibe los operandos en orden. */
    @Test
    void concatWithNumberOnLeft() {
        Fixed h = new Fixed(s("ok"));
        LuaTable obj = withMeta("__concat", h);
        assertEquals(s("ok"), Ops.concat(n(1), obj));
        assertEquals(n(1), h.lastArgs[0]);
        assertSame(obj, h.lastArgs[1]);
    }

    /** __concat con la tabla a la izquierda usa solo el primer resultado. */
    @Test
    void concatWithTableOnLeft() {
        Fixed h = new Fixed(s("ok"), s("extra"));
        assertEquals(s("ok"), Ops.concat(withMeta("__concat", h), s("x")));
    }

    /** Sin __concat, concatenar una tabla sigue siendo un error. */
    @Test
    void concatWithoutMetamethodKeepsError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.concat(s("x"), new LuaTable()));
        assertEquals(s("attempt to concatenate a table value"), e.value);
    }

    /** #t usa __len y le pasa solo la tabla. */
    @Test
    void lenUsesMetamethod() {
        Fixed h = new Fixed(n(99));
        LuaTable obj = withMeta("__len", h);
        assertEquals(n(99), Ops.len(obj));
        assertEquals(1, h.lastArgs.length);
        assertSame(obj, h.lastArgs[0]);
    }

    /** Sin __len, #t cuenta los elementos de la tabla. */
    @Test
    void lenWithoutMetamethodCountsElements() {
        LuaTable t = new LuaTable();
        t.appendArray(n(1));
        t.appendArray(n(2));
        assertEquals(n(2), Ops.len(t));
    }

    // ---- Igualdad ----

    /** __eq se usa al comparar dos tablas distintas, y se busca en cualquiera de las dos. */
    @Test
    void eqUsesMetamethodForDistinctTables() {
        Fixed h = new Fixed(LuaBoolean.TRUE);
        LuaTable a = withMeta("__eq", h);
        LuaTable b = new LuaTable();
        assertTrue(Ops.eq(a, b));
        assertEquals(1, h.calls);
        assertTrue(Ops.eq(b, a));
    }

    /** El resultado de __eq se convierte a verdadero o falso: solo nil y false son falsos. */
    @Test
    void eqConvertsResultToBoolean() {
        assertTrue(Ops.eq(withMeta("__eq", new Fixed(n(0))), new LuaTable()));
        assertFalse(Ops.eq(withMeta("__eq", new Fixed(LuaNil.NIL)), new LuaTable()));
        assertFalse(Ops.eq(withMeta("__eq", new Fixed()), new LuaTable()));
    }

    /** Una tabla siempre es igual a sí misma, sin llamar a __eq. */
    @Test
    void eqNotCalledForSameTable() {
        Fixed h = new Fixed(LuaBoolean.FALSE);
        LuaTable a = withMeta("__eq", h);
        assertTrue(Ops.eq(a, a));
        assertEquals(0, h.calls);
    }

    /** __eq no se llama si uno de los dos valores no es una tabla. */
    @Test
    void eqNotCalledWhenOneIsNotTable() {
        Fixed h = new Fixed(LuaBoolean.TRUE);
        LuaTable a = withMeta("__eq", h);
        assertFalse(Ops.eq(a, n(1)));
        assertFalse(Ops.eq(s("x"), a));
        assertEquals(0, h.calls);
    }

    /** Sin __eq, dos tablas distintas nunca son iguales. */
    @Test
    void eqWithoutMetamethodIsFalse() {
        assertFalse(Ops.eq(new LuaTable(), new LuaTable()));
    }

    // ---- Orden (< y <=) ----

    /** a < b usa __lt con los operandos en orden, también si a es un número. */
    @Test
    void ltUsesMetamethod() {
        Fixed h = new Fixed(LuaBoolean.TRUE);
        LuaTable a = withMeta("__lt", h);
        LuaTable b = new LuaTable();
        assertTrue(Ops.lt(a, b));
        assertSame(a, h.lastArgs[0]);
        assertSame(b, h.lastArgs[1]);
        // Con un número a la izquierda se busca en el operando derecho
        assertTrue(Ops.lt(n(1), a));
        assertEquals(n(1), h.lastArgs[0]);
    }

    /** El resultado de __lt se convierte a verdadero o falso. */
    @Test
    void ltConvertsResultToBoolean() {
        assertTrue(Ops.lt(withMeta("__lt", new Fixed(n(0))), new LuaTable()));
        assertFalse(Ops.lt(withMeta("__lt", new Fixed(LuaBoolean.FALSE)), new LuaTable()));
        assertFalse(Ops.lt(withMeta("__lt", new Fixed()), new LuaTable()));
    }

    /** a <= b usa __le, buscándolo en cualquiera de los dos operandos. */
    @Test
    void leUsesMetamethod() {
        Fixed h = new Fixed(LuaBoolean.TRUE, LuaBoolean.FALSE);
        LuaTable a = withMeta("__le", h);
        assertTrue(Ops.le(a, new LuaTable()));
        assertTrue(Ops.le(new LuaTable(), a));
    }

    /** Desde Lua 5.4, si falta __le es un error aunque exista __lt. */
    @Test
    void leDoesNotFallBackToLt() {
        LuaTable a = withMeta("__lt", new Fixed(LuaBoolean.TRUE));
        LuaError e = assertThrows(LuaError.class, () -> Ops.le(a, new LuaTable()));
        assertEquals(s("attempt to compare two table values"), e.value);
    }

    /** Sin metamétodo, comparar una tabla con un número sigue siendo un error. */
    @Test
    void comparisonWithoutMetamethodKeepsError() {
        LuaError e = assertThrows(LuaError.class, () -> Ops.lt(new LuaTable(), n(1)));
        assertEquals(s("attempt to compare table with number"), e.value);
    }
}
