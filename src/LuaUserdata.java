// Interno de C. Tipo userdata (solo se usa para io.stdin). Igualdad por referencia.
public class LuaUserdata extends LuaValue {
    @Override public String typeName() { return "userdata"; }
}
