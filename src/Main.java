import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de C.
public class Main {
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, "ISO-8859-1");
        System.setOut(out);
        String chunkName = args.length > 0 ? args[0] : "stdin";
        byte[] bytes = args.length > 0 ? Files.readAllBytes(Paths.get(args[0])) : readAll(System.in);
        String code = new String(bytes, StandardCharsets.ISO_8859_1);
        try {
            LuaFunction chunk = Interpreter.load(code, chunkName, Stdlib.createGlobals());
            chunk.call(LuaValue.NONE);
        } catch (LuaError e) {
            out.println("lua: " + Ops.tostring(e.value));
        } finally {
            out.flush();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        return buf.toByteArray();
    }
}
