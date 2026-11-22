import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.opentest4j.AssertionFailedError;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Verificación centralizada del avance: ejecuta el intérprete como subproceso
 * ({@code java Main <archivo>}) y compara su salida (stdout + stderr) byte a
 * byte con la salida de lua5.4 real.
 *
 * <ul>
 *   <li>Por cada caso de {@code test_cases/}: el caso completo.</li>
 *   <li>Por cada sección listada en {@code tests/sections.txt}: el fragmento
 *       independiente {@code tests/sections/<id>.lua} contra su
 *       {@code .expected}.</li>
 * </ul>
 *
 * Filtros opcionales (propiedades del sistema, ver run_unit_tests.sh):
 * {@code lua.case=03}, {@code lua.owner=B}, {@code lua.timeout=10} (segundos).
 *
 * Se ejecuta desde la raíz del repositorio y requiere que {@code out/} esté compilado.
 */
public class LuaTestCasesTest {

    private static final Charset BYTES = Charset.forName("ISO-8859-1");
    private static final Path MANIFEST = Paths.get("tests", "sections.txt");
    private static final Path SECTIONS = Paths.get("tests", "sections");
    private static final Path CASES = Paths.get("test_cases");
    private static final String CLASSPATH =
            "out" + File.pathSeparator + "lib" + File.separator + "antlr-4.13.2-complete.jar";

    private static final String CASE_FILTER = System.getProperty("lua.case", "").trim();
    private static final String OWNER_FILTER = System.getProperty("lua.owner", "").trim().toUpperCase();
    private static final long TIMEOUT_S = Long.parseLong(System.getProperty("lua.timeout", "10"));

    /** Una fila de tests/sections.txt. */
    private static final class Section {
        final String id, caseId, owners, title;

        Section(String id, String caseId, String owners, String title) {
            this.id = id;
            this.caseId = caseId;
            this.owners = owners;
            this.title = title;
        }
    }

    @TestFactory
    List<DynamicNode> casosDePrueba() throws IOException {
        Map<String, List<Section>> byCase = new LinkedHashMap<String, List<Section>>();
        for (Section s : readManifest()) {
            List<Section> list = byCase.get(s.caseId);
            if (list == null) {
                list = new ArrayList<Section>();
                byCase.put(s.caseId, list);
            }
            list.add(s);
        }

        List<DynamicNode> containers = new ArrayList<DynamicNode>();
        for (Map.Entry<String, List<Section>> e : byCase.entrySet()) {
            final String caseId = e.getKey();
            if (!CASE_FILTER.isEmpty() && !CASE_FILTER.equals(caseId)) continue;

            List<DynamicNode> tests = new ArrayList<DynamicNode>();
            if (OWNER_FILTER.isEmpty()) {
                final Path in = CASES.resolve(caseId + ".in");
                final Path expected = CASES.resolve(caseId + ".expected");
                tests.add(DynamicTest.dynamicTest("Caso " + caseId + " completo", in.toUri(),
                        () -> check(in, expected)));
            }
            for (Section s : e.getValue()) {
                if (!OWNER_FILTER.isEmpty() && !s.owners.contains(OWNER_FILTER)) continue;
                final Path in = SECTIONS.resolve(s.id + ".lua");
                final Path expected = SECTIONS.resolve(s.id + ".expected");
                tests.add(DynamicTest.dynamicTest("[" + s.owners + "] " + s.title, in.toUri(),
                        () -> check(in, expected)));
            }
            if (!tests.isEmpty()) {
                containers.add(DynamicContainer.dynamicContainer("Caso " + caseId, tests));
            }
        }
        return containers;
    }

    private static List<Section> readManifest() throws IOException {
        List<Section> out = new ArrayList<Section>();
        for (String raw : Files.readAllLines(MANIFEST, Charset.forName("UTF-8"))) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+", 5);   // id caso dueños líneas título
            out.add(new Section(p[0], p[1], p[2], p[4]));
        }
        return out;
    }

    private static void check(Path in, Path expectedFile) throws Exception {
        String expected = new String(Files.readAllBytes(expectedFile), BYTES);
        String actual = runInterpreter(in);
        if (!expected.equals(actual)) {
            throw new AssertionFailedError(describeDiff(expected, actual), expected, actual);
        }
    }

    /** Ejecuta {@code java -cp out:antlr Main <in>} desde la raíz del repo. */
    private static String runInterpreter(Path in) throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        File output = File.createTempFile("lua-test", ".out");
        output.deleteOnExit();
        Process p = new ProcessBuilder(java, "-cp", CLASSPATH, "Main", in.toString())
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start();
        try {
            if (!p.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                String partial = new String(Files.readAllBytes(output.toPath()), BYTES);
                throw new AssertionFailedError("Timeout: el intérprete no terminó en " + TIMEOUT_S
                        + " s (¿corrutina o bucle colgado?). Salida parcial:\n" + partial);
            }
            return new String(Files.readAllBytes(output.toPath()), BYTES);
        } finally {
            output.delete();
        }
    }

    /** Resumen legible: las primeras líneas que difieren. */
    private static String describeDiff(String expected, String actual) {
        String[] e = expected.split("\n", -1);
        String[] a = actual.split("\n", -1);
        StringBuilder sb = new StringBuilder("La salida no coincide con lua5.4:\n");
        int shown = 0;
        for (int i = 0; i < Math.max(e.length, a.length) && shown < 8; i++) {
            String el = i < e.length ? e[i] : "<sin línea>";
            String al = i < a.length ? a[i] : "<sin línea>";
            if (!el.equals(al)) {
                sb.append("  línea ").append(i + 1).append('\n')
                  .append("    esperado: ").append(el).append('\n')
                  .append("    obtenido: ").append(al).append('\n');
                shown++;
            }
        }
        if (shown == 8) sb.append("  ...\n");
        return sb.toString();
    }
}
