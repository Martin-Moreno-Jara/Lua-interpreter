// Contrato §5.1 (dueño: B). String de Lua: cada char de Java es un byte (ISO-8859-1).
public final class LuaString extends LuaValue {
    public final String value;

    public LuaString(String value) { this.value = value; }

    @Override public String typeName() { return "string"; }

    @Override public boolean equals(Object o) {
        return o instanceof LuaString && ((LuaString) o).value.equals(value);
    }

    @Override public int hashCode() { return value.hashCode(); }

    @Override public String toString() { return value; }
}
