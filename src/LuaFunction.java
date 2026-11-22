// Contrato §5.2 (dueño: B). Base de LuaClosure (A) y de las funciones nativas (C).
// - Devuelve siempre un arreglo; sin resultados es LuaValue.NONE, nunca null.
// - Los argumentos que faltan valen nil y los completa quien recibe.
// - En obj:m(x), obj llega como args[0].
// Igualdad por referencia (no se sobrescribe equals/hashCode).
public abstract class LuaFunction extends LuaValue {
    @Override public String typeName() { return "function"; }

    public abstract LuaValue[] call(LuaValue[] args);

    // Argumento i, o NIL si no se pasó. Utilidad para las funciones nativas.
    public static LuaValue arg(LuaValue[] args, int i) {
        return i < args.length ? args[i] : LuaNil.NIL;
    }
}
