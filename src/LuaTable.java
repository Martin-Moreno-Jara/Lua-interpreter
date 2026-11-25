// Contrato §5.5 (dueño: B). Parte array + parte hash; modo débil según __mode.
// Igualdad por referencia (no se sobrescribe equals/hashCode).
public class LuaTable extends LuaValue {

    @Override public String typeName() { return "table"; }

    // NIL si no existe. Ignora metamétodos.
    public LuaValue rawget(LuaValue k) {
        throw new UnsupportedOperationException("TODO B: LuaTable.rawget");
    }

    // v == NIL borra; normaliza claves float con valor entero (2.0 -> 2). Ignora metamétodos.
    public void rawset(LuaValue k, LuaValue v) {
        throw new UnsupportedOperationException("TODO B: LuaTable.rawset");
    }

    // Entrada posicional del constructor de tabla, en orden (admite NIL).
    public void appendArray(LuaValue v) {
        throw new UnsupportedOperationException("TODO B: LuaTable.appendArray");
    }

    // {clave, valor} siguiente a k (k == NIL para empezar); null al terminar.
    public LuaValue[] next(LuaValue k) {
        throw new UnsupportedOperationException("TODO B: LuaTable.next");
    }

    // Borde, sin __len.
    public long length() {
        throw new UnsupportedOperationException("TODO B: LuaTable.length");
    }

    // null si no tiene.
    public LuaTable getMetatable() {
        throw new UnsupportedOperationException("TODO B: LuaTable.getMetatable");
    }

    // Lee __mode para activar el modo débil.
    public void setMetatable(LuaTable mt) {
        throw new UnsupportedOperationException("TODO B: LuaTable.setMetatable");
    }
}
