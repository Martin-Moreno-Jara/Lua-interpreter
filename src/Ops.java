// Contrato §5.4 (dueño: B). Frontera entre el evaluador (A) y la semántica de valores (B).
// El evaluador nunca opera valores directamente; los metamétodos viven solo aquí.
// >, >= y ~= los traduce el evaluador a lt/le invertidos y a !eq.
// and/or/not los resuelve el evaluador (cortocircuito).
public final class Ops {
    private Ops() {}

    // op: "+","-","*","/","//","%","^","&","|","~","<<",">>"
    public static LuaValue arith(String op, LuaValue a, LuaValue b) {
        throw new UnsupportedOperationException("TODO B: Ops.arith");
    }

    public static LuaValue unm(LuaValue a) {
        throw new UnsupportedOperationException("TODO B: Ops.unm");
    }

    public static LuaValue bnot(LuaValue a) {
        throw new UnsupportedOperationException("TODO B: Ops.bnot");
    }

    public static LuaValue concat(LuaValue a, LuaValue b) {
        throw new UnsupportedOperationException("TODO B: Ops.concat");
    }

    // Incluye __len.
    public static LuaValue len(LuaValue a) {
        throw new UnsupportedOperationException("TODO B: Ops.len");
    }

    // Incluye __eq.
    public static boolean eq(LuaValue a, LuaValue b) {
        throw new UnsupportedOperationException("TODO B: Ops.eq");
    }

    // Incluye __lt.
    public static boolean lt(LuaValue a, LuaValue b) {
        throw new UnsupportedOperationException("TODO B: Ops.lt");
    }

    // Incluye __le.
    public static boolean le(LuaValue a, LuaValue b) {
        throw new UnsupportedOperationException("TODO B: Ops.le");
    }

    // t[k], incluye la cadena __index (función o tabla).
    public static LuaValue index(LuaValue t, LuaValue k) {
        throw new UnsupportedOperationException("TODO B: Ops.index");
    }

    // t[k] = v, incluye __newindex (función o tabla).
    public static void setIndex(LuaValue t, LuaValue k, LuaValue v) {
        throw new UnsupportedOperationException("TODO B: Ops.setIndex");
    }

    // Llamada a f, incluye __call.
    public static LuaValue[] call(LuaValue f, LuaValue[] args) {
        throw new UnsupportedOperationException("TODO B: Ops.call");
    }

    // Formato de print/tostring. Tablas y funciones: "table: 0x%08x" con identityHashCode.
    public static String tostring(LuaValue v) {
        throw new UnsupportedOperationException("TODO B: Ops.tostring");
    }
}
