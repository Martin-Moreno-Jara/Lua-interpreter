/** Booleano de Lua; existen solo dos instancias, LuaBoolean.TRUE y LuaBoolean.FALSE. Contrato §5.1. */
public final class LuaBoolean extends LuaValue {
    /** Única instancia de true. */
    public static final LuaBoolean TRUE = new LuaBoolean(true);
    /** Única instancia de false. */
    public static final LuaBoolean FALSE = new LuaBoolean(false);

    public final boolean value;

    // Es privado para que solo existan TRUE y FALSE y comparar con == siempre funcione.
    private LuaBoolean(boolean value) { this.value = value; }

    @Override public String typeName() { return "boolean"; }

    @Override public boolean isTruthy() { return value; }

    @Override public String toString() { return value ? "true" : "false"; }
}
