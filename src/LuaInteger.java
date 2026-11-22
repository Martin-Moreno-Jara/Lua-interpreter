// Contrato §5.1 (dueño: B). Subtipo entero de number.
// Igualdad por valor entre LuaInteger. La equivalencia 2 == 2.0 como clave
// la resuelve LuaTable.rawset normalizando floats enteros a LuaInteger.
public final class LuaInteger extends LuaValue {
    public final long value;

    public LuaInteger(long value) { this.value = value; }

    @Override public String typeName() { return "number"; }

    @Override public boolean equals(Object o) {
        return o instanceof LuaInteger && ((LuaInteger) o).value == value;
    }

    @Override public int hashCode() { return (int) (value ^ (value >>> 32)); }

    @Override public String toString() { return Long.toString(value); }
}
