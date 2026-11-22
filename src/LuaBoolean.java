// Contrato §5.1 (dueño: B). Instancias únicas: LuaBoolean.TRUE y LuaBoolean.FALSE.
public final class LuaBoolean extends LuaValue {
    public static final LuaBoolean TRUE = new LuaBoolean(true);
    public static final LuaBoolean FALSE = new LuaBoolean(false);

    public final boolean value;

    private LuaBoolean(boolean value) { this.value = value; }

    @Override public String typeName() { return "boolean"; }

    @Override public boolean isTruthy() { return value; }

    @Override public String toString() { return value ? "true" : "false"; }
}
