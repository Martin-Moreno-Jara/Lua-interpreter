import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Dueño: C. Punto de entrada del intérprete.
// Debe compilar con Java 8: el juez de UNCode usa JDK 1.8.
//
//   java Main archivo.lua          ejecuta el archivo (chunk = la ruta tal cual)
//   java Main                      ejecuta lo que llegue por stdin (chunk = "stdin")
//   java Main archivo.lua --tree   solo imprime el árbol sintáctico (depuración)
public class Main {
    // Pila grande para el hilo principal: un intérprete que recorre el árbol
    // gasta varios marcos de Java por cada llamada de Lua.
    private static final long STACK_SIZE = 512L * 1024 * 1024;

    public static void main(final String[] args) throws Exception {
        // ISO-8859-1 en ambos sentidos: un char de Java es un byte de Lua (§2.4).
        // "\n" explícito en todo el intérprete: println usaría "\r\n" en Windows.
        final PrintStream out = new PrintStream(
                new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16),
                false, "ISO-8859-1");
        System.setOut(out);

        final String chunkName = args.length > 0 ? args[0] : "stdin";
        final String code = new String(
                args.length > 0 ? Files.readAllBytes(Paths.get(args[0])) : readAll(System.in),
                StandardCharsets.ISO_8859_1);

        if (args.length > 1 && args[1].equals("--tree")) {
            LuaParser parser = new LuaParser(new CommonTokenStream(new LuaLexer(CharStreams.fromString(code))));
            printTree(parser.start_(), "", out);
            out.flush();
            return;
        }

        final int[] status = { 0 };
        Thread runner = new Thread(null, new Runnable() {
            @Override public void run() {
                status[0] = Main.run(code, chunkName, out);
            }
        }, "lua-main", STACK_SIZE);
        runner.start();
        runner.join();
        out.flush();
        // exit también termina los hilos de corrutinas que quedaron suspendidas.
        System.exit(status[0]);
    }

    // Ejecuta el programa y devuelve el código de salida (0 bien, 1 error no capturado).
    static int run(String code, String chunkName, PrintStream out) {
        try {
            LuaTable globals = Stdlib.createGlobals();
            LuaFunction chunk = Interpreter.load(code, chunkName, globals);
            chunk.call(LuaValue.NONE);
            return 0;
        } catch (LuaError e) {
            reportError(out, errorMessage(e.value));
        } catch (StackOverflowError e) {
            reportError(out, chunkName + ": stack overflow");
        } catch (RuntimeException e) {
            // No debería pasar (§5.3): es un error del intérprete, no del programa Lua.
            reportError(out, "internal error: " + e);
        }
        return 1;
    }

    // Como lua.c: el mensaje si es string o número; si no, el tipo del objeto.
    static String errorMessage(LuaValue v) {
        if (v instanceof LuaString || v instanceof LuaInteger || v instanceof LuaFloat) return Ops.tostring(v);
        return "(error object is a " + v.typeName() + " value)";
    }

    // Toda la salida va a stdout (§2.5), igual que en los .expected.
    private static void reportError(PrintStream out, String msg) {
        out.print("lua: " + msg + "\n");
        out.flush();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int n; (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    // Un nodo por línea, indentado. Las alternativas con etiqueta muestran la
    // etiqueta (p. ej. BitOrExp), que es lo que ve el visitor.
    static void printTree(ParseTree t, String indent, PrintStream out) {
        if (t instanceof TerminalNode) {
            out.print(indent + "'" + t.getText() + "'\n");
            return;
        }
        String name = t.getClass().getSimpleName().replaceFirst("Context$", "");
        out.print(indent + name + "\n");
        for (int i = 0; i < t.getChildCount(); i++) printTree(t.getChild(i), indent + "  ", out);
    }
}
