/** Entero de Lua, uno de los dos tipos de número (el otro es LuaFloat). Contrato §5.1. */
public final class LuaInteger extends LuaValue {
    public final long value;

    /** Crea un entero con el valor dado. */
    public LuaInteger(long value) { this.value = value; }

    @Override public String typeName() { return "number"; }

    // Solo compara con otros enteros. Que la clave 2 y la clave 2.0 sean la misma
    // lo resuelve LuaTable, que convierte 2.0 en 2 antes de guardar.
    @Override public boolean equals(Object o) {
        return o instanceof LuaInteger && ((LuaInteger) o).value == value;
    }

    @Override public int hashCode() { return (int) (value ^ (value >>> 32)); }

    @Override public String toString() { return Long.toString(value); }
}
