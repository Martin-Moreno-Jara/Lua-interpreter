// Contrato §5.3 (dueño: B). Todo error de Lua es un LuaError.
// - error("msg") (C) construye el valor ya con "chunk:línea: " y marca positioned.
// - Los errores de runtime de Ops (B) usan LuaError(String) sin posición; el
//   evaluador (A) les antepone la posición una sola vez y marca positioned.
public class LuaError extends RuntimeException {
    public final LuaValue value;
    public boolean positioned;

    public LuaError(LuaValue value) {
        super(value instanceof LuaString ? ((LuaString) value).value : null, null, false, false);
        this.value = value;
    }

    // Error de runtime sin posición.
    public LuaError(String message) {
        this(new LuaString(message));
    }
}
