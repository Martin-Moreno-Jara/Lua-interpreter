// Dueño: C. Tipo userdata (solo se usa para io.stdin). Igualdad por referencia.
public class LuaUserdata extends LuaValue {
    private final String name;   // solo informativo, p. ej. "file (stdin)"

    public LuaUserdata(String name) { this.name = name; }

    @Override public String typeName() { return "userdata"; }

    @Override public String toString() { return name; }
}
