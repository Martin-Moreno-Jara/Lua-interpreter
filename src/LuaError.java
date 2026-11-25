// Quién agrega la posición "archivo:línea: " al mensaje:
// - error("msg") (C) la escribe al crear el error y marca positioned.
// - Los errores de Ops (B) se crean sin posición; el evaluador (A) la agrega una sola vez.
/** Error de Lua; todo error que pcall deba poder atrapar es un LuaError. Contrato §5.3. */
public class LuaError extends RuntimeException {
    public final LuaValue value;  // string, tabla o cualquier otro valor de Lua
    public boolean positioned;    // true si el mensaje ya empieza con "archivo:línea: "

    /** Crea un error cuyo contenido es cualquier valor de Lua. */
    public LuaError(LuaValue value) {
        // Sin el registro de llamadas de Java: Lua no lo muestra y generarlo vuelve lento a pcall.
        super(value instanceof LuaString ? ((LuaString) value).value : null, null, false, false);
        this.value = value;
    }

    /** Crea un error de ejecución sin posición a partir de un mensaje. */
    public LuaError(String message) {
        this(new LuaString(message));
    }
}
