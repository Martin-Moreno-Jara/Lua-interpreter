/** Valor de Lua y base de todos sus tipos; nil es siempre LuaNil.NIL, nunca el null de Java. Contrato §5.1. */
public abstract class LuaValue {
    /** Lista de resultados vacía, compartida para no crear un arreglo nuevo en cada llamada. */
    public static final LuaValue[] NONE = new LuaValue[0];

    /** Nombre del tipo: "nil", "boolean", "number", "string", "table", "function", "thread" o "userdata". */
    public abstract String typeName();

    /** Indica si el valor cuenta como verdadero; en Lua solo nil y false son falsos. */
    public boolean isTruthy() { return true; }

    /** Crea un entero de Lua. */
    public static LuaValue of(long v) { return new LuaInteger(v); }

    /** Crea un número decimal de Lua. */
    public static LuaValue of(double v) { return new LuaFloat(v); }

    /** Crea un string de Lua. */
    public static LuaValue of(String s) { return new LuaString(s); }

    /** Devuelve la instancia única de true o de false. */
    public static LuaValue of(boolean b) { return b ? LuaBoolean.TRUE : LuaBoolean.FALSE; }
}
