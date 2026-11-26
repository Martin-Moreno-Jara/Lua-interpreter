// Contrato §5.6 (dueño: A). API pública del intérprete.

import org.antlr.v4.runtime.*;

public class Interpreter {
    // Posición en curso, por hilo (cada corrutina corre en su propio hilo).
    // A las actualiza al evaluar cada sentencia y cada llamada a función.
    static final ThreadLocal<Integer> LINE = new ThreadLocal<Integer>() {
        @Override 
        protected Integer initialValue() { return 0; }
    };
    static final ThreadLocal<String> CHUNK = new ThreadLocal<String>() {
        @Override 
        protected String initialValue() { return "?"; }
    };

    private Interpreter() {}

    // Parsea code y devuelve el chunk como función. env es la tabla de globales (_ENV).
    // chunkName: ruta del archivo, "stdin", o [string "..."] para load.
    public static LuaFunction load(String code, final String chunkName, final LuaTable env) {
        LuaLexer lexer = new LuaLexer(CharStreams.fromString(code));
        LuaParser parser = new LuaParser(new CommonTokenStream(lexer));
        final LuaParser.Start_Context tree = parser.start_();

        return new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                CHUNK.set(chunkName);
                LINE.set(0);
                // Un Evaluator por ejecución: scope y varargs son locales a esta llamada.
                Evaluator ev = new Evaluator(env, chunkName, new Scope(null), args);
                try {
                    ev.visit(tree);
                    return LuaValue.NONE;          // terminó sin 'return'
                } catch (ControlSignal.Return r) {
                    return r.values;               // 'return' al nivel del chunk
                }
            }
        };
    }

    // Línea de la llamada o sentencia en curso en este hilo.
    public static int currentLine() { return LINE.get(); }

    // Nombre del chunk en curso en este hilo.
    public static String currentChunk() { return CHUNK.get(); }
}
