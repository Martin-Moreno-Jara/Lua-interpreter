/** String de Lua; cada carácter de Java guarda exactamente un byte, como en Lua. Contrato §5.1. */
public final class LuaString extends LuaValue {
    public final String value;

    /** Crea un string con el texto dado. */
    public LuaString(String value) { this.value = value; }

    @Override public String typeName() { return "string"; }

    @Override public boolean equals(Object o) {
        return o instanceof LuaString && ((LuaString) o).value.equals(value);
    }

    @Override public int hashCode() { return value.hashCode(); }

    @Override public String toString() { return value; }
}
