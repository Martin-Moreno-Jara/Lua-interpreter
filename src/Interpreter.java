// Contrato §5.6 (dueño: A). API pública del intérprete.
public class Interpreter {
    // Posición en curso, por hilo (cada corrutina corre en su propio hilo).
    // A las actualiza al evaluar cada sentencia y cada llamada a función.
    static final ThreadLocal<Integer> LINE = new ThreadLocal<Integer>() {
        @Override protected Integer initialValue() { return 0; }
    };
    static final ThreadLocal<String> CHUNK = new ThreadLocal<String>() {
        @Override protected String initialValue() { return "?"; }
    };

    private Interpreter() {}

    // Parsea code y devuelve el chunk como función. env es la tabla de globales (_ENV).
    // chunkName: ruta del archivo, "stdin", o [string "..."] para load.
    public static LuaFunction load(String code, String chunkName, LuaTable env) {
        throw new UnsupportedOperationException("TODO A: Interpreter.load");
    }

    // Línea de la llamada o sentencia en curso en este hilo.
    public static int currentLine() { return LINE.get(); }

    // Nombre del chunk en curso en este hilo.
    public static String currentChunk() { return CHUNK.get(); }
}
