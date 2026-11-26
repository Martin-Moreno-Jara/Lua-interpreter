// Dos funciones solo son iguales si son el mismo objeto, por eso no se redefine equals.
/** Función de Lua: base de las escritas en Lua (LuaClosure) y de las nativas en Java. Contrato §5.2. */
public abstract class LuaFunction extends LuaValue {
    @Override public String typeName() { return "function"; }

    // Los argumentos que faltan valen nil y los completa la función que recibe la llamada.
    // En obj:m(x), obj llega como args[0].
    /** Ejecuta la función y devuelve sus resultados; si no hay ninguno devuelve LuaValue.NONE, nunca null. */
    public abstract LuaValue[] call(LuaValue[] args);

    /** Devuelve el argumento en la posición i, o nil si no se pasó. */
    public static LuaValue arg(LuaValue[] args, int i) {
        return i < args.length ? args[i] : LuaNil.NIL;
    }
}
