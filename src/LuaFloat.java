// Contrato §5.1 (dueño: B). Subtipo flotante de number.
public final class LuaFloat extends LuaValue {
    public final double value;

    public LuaFloat(double value) { this.value = value; }

    @Override public String typeName() { return "number"; }

    @Override public boolean equals(Object o) {
        return o instanceof LuaFloat
                && Double.doubleToLongBits(((LuaFloat) o).value) == Double.doubleToLongBits(value);
    }

    @Override public int hashCode() { return Double.valueOf(value).hashCode(); }

    // Solo para depuración: el formato de Lua (%.14g) es NumberFormat / Ops.tostring.
    @Override public String toString() { return Double.toString(value); }
}
