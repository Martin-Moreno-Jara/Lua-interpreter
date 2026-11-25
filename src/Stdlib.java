// Contrato §5.6 (dueño: C). Biblioteca estándar usada por los casos de prueba.
public class Stdlib {
    private Stdlib() {}

    // _G con print, type, tostring, pcall, xpcall, error, setmetatable, rawset,
    // pairs, ipairs, collectgarbage, load, coroutine.{create,resume,yield},
    // table.{pack,unpack}, io.{write,stdin}, math.pi. _ENV apunta a esta tabla.
    public static LuaTable createGlobals() {
        throw new UnsupportedOperationException("TODO C: Stdlib.createGlobals");
    }
}
