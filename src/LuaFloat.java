/** Número decimal de Lua, uno de los dos tipos de número (el otro es LuaInteger). Contrato §5.1. */
public final class LuaFloat extends LuaValue {
    public final double value;

    /** Crea un número decimal con el valor dado. */
    public LuaFloat(double value) { this.value = value; }

    @Override public String typeName() { return "number"; }

    // Se comparan los bits porque con == un NaN no es igual a sí mismo,
    // y las colecciones de Java necesitan que todo valor sea igual a sí mismo.
    @Override public boolean equals(Object o) {
        return o instanceof LuaFloat
                && Double.doubleToLongBits(((LuaFloat) o).value) == Double.doubleToLongBits(value);
    }

    @Override public int hashCode() { return Double.valueOf(value).hashCode(); }

    // Solo sirve para depurar: Lua imprime los decimales con el formato de NumberFormat.
    @Override public String toString() { return Double.toString(value); }
}
