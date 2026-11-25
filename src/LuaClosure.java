import java.util.ArrayList;
import java.util.List;

// Interno de A. Función definida en Lua: parámetros, varargs y upvalues.
// Guarda el cuerpo (un subárbol) y el Scope donde se definió; el cuerpo solo
// se recorre cuando alguien la llama.
public class LuaClosure extends LuaFunction {
    private final LuaParser.FuncbodyContext body;
    private final Scope captured;        // scope de la definición: de aquí salen los upvalues
    private final LuaTable env;
    private final String chunkName;
    private final String[] params;
    private final boolean isVararg;

    // isMethod: definida con 'function a.b:m()', que agrega 'self' como primer parámetro.
    public LuaClosure(LuaParser.FuncbodyContext body, Scope captured, LuaTable env,
                      String chunkName, boolean isMethod) {
        this.body = body;
        this.captured = captured;
        this.env = env;
        this.chunkName = chunkName;

        // parlist: namelist (',' '...')? | '...' | (vacío)
        List<String> names = new ArrayList<String>();
        if (isMethod) names.add("self");
        LuaParser.ParlistContext pl = body.parlist();
        if (pl.namelist() != null) {
            for (org.antlr.v4.runtime.tree.TerminalNode n : pl.namelist().NAME()) names.add(n.getText());
        }
        this.params = names.toArray(new String[0]);
        this.isVararg = pl.DDD() != null;
    }

    @Override
    public LuaValue[] call(LuaValue[] args) {
        // Un scope nuevo por llamada, hijo del capturado: así cada llamada tiene
        // sus propios parámetros y todas ven las mismas variables de afuera.
        Scope scope = new Scope(captured);
        for (int i = 0; i < params.length; i++) {
            scope.declare(params[i], i < args.length ? args[i] : LuaNil.NIL);   // faltantes: nil
        }

        // Los argumentos que sobran son el '...' (solo si la función lo declara).
        LuaValue[] varargs = LuaValue.NONE;
        if (isVararg && args.length > params.length) {
            varargs = new LuaValue[args.length - params.length];
            System.arraycopy(args, params.length, varargs, 0, varargs.length);
        }

        Evaluator ev = new Evaluator(env, chunkName, scope, varargs);
        try {
            ev.visit(body.block());
            return LuaValue.NONE;              // terminó sin 'return'
        } catch (ControlSignal.Return r) {
            return r.values;
        }
    }
}
