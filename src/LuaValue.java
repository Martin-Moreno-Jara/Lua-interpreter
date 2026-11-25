// Contrato §5.1 (dueño: B). Valor de Lua. Nunca se usa el null de Java como nil.
public abstract class LuaValue {
    public static final LuaValue[] NONE = new LuaValue[0];

    // "nil","boolean","number","string","table","function","thread","userdata"
    public abstract String typeName();

    // Solo NIL y FALSE devuelven false.
    public boolean isTruthy() { return true; }

    public static LuaValue of(long v) { return new LuaInteger(v); }

    public static LuaValue of(double v) { return new LuaFloat(v); }

    public static LuaValue of(String s) { return new LuaString(s); }

    public static LuaValue of(boolean b) { return b ? LuaBoolean.TRUE : LuaBoolean.FALSE; }
}
