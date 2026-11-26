/** El valor nil de Lua; existe una sola instancia, LuaNil.NIL. Contrato §5.1. */
public final class LuaNil extends LuaValue {
    /** Única instancia de nil. */
    public static final LuaNil NIL = new LuaNil();

    // Es privado para que no exista un segundo nil y comparar con == siempre funcione.
    private LuaNil() {}

    @Override public String typeName() { return "nil"; }

    @Override public boolean isTruthy() { return false; }

    @Override public String toString() { return "nil"; }
}
