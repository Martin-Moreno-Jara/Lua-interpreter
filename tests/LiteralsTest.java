import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pruebas unitarias de Literals. Los valores esperados se sacaron de lua5.4 real
 * (math.type, print y los bytes de cada string).
 */
public class LiteralsTest {

    // ---- números ----

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource({
        // enteros decimales y hexadecimales
        "345, integer, 345",
        "0, integer, 0",
        "0xBEBADA, integer, 12499674",
        "0xff, integer, 255",
        "0XA, integer, 10",
        // punto o exponente lo vuelven float
        "314.16e-2, float, 3.1416",
        "3.14, float, 3.14",
        "10.0, float, 10.0",
        "5., float, 5.0",
        ".5, float, 0.5",
        "1e2, float, 100.0",
        "2E-3, float, 0.002",
        // hex float, con y sin exponente p
        "0X1.921FB54442D18P+1, float, 3.1415926535898",
        "0x1.8, float, 1.5",
        "0x.8, float, 0.5",
        "0xAp2, float, 40.0",
        "0x1P-1, float, 0.5",
        "0xA.8p0, float, 10.5",
    })
    void numeroIgualQueLua(String token, String tipo, String impreso) {
        LuaValue v = Literals.parseNumber(token);
        if (tipo.equals("integer")) {
            assertInstanceOf(LuaInteger.class, v);
            assertEquals(impreso, Long.toString(((LuaInteger) v).value));
        } else {
            assertInstanceOf(LuaFloat.class, v);
            assertEquals(impreso, NumberFormat.format(((LuaFloat) v).value));
        }
    }

    @Test
    void hexFloatEsExacto() {
        // en lua5.4, 0X1.921FB54442D18P+1 == math.pi da true
        assertEquals(LuaValue.of(Math.PI), Literals.parseNumber("0X1.921FB54442D18P+1"));
    }

    // ---- strings con comillas ----

    // Arma un string de Java a partir de bytes, para comparar con la salida UTF-8.
    private static String bytes(int... bs) {
        StringBuilder sb = new StringBuilder();
        for (int b : bs) sb.append((char) b);
        return sb.toString();
    }

    // Token "barra-u{code}" entre comillas, sin escribir la barra seguida de u.
    private static String utfToken(String code) {
        return "\"\\" + "u{" + code + "}\"";
    }

    @Test
    void sinEscapes() {
        assertEquals("hola", Literals.parseString("\"hola\""));
        assertEquals("hola", Literals.parseString("'hola'"));
        assertEquals("", Literals.parseString("\"\""));
    }

    @Test
    void escapesDeUnaLetra() {
        assertEquals("a\nb\tc", Literals.parseString("\"a\\nb\\tc\""));
        assertEquals(bytes(0x07, 0x08, 0x0C, 0x0B, 0x0D), Literals.parseString("\"\\a\\b\\f\\v\\r\""));
        assertEquals("\\", Literals.parseString("\"\\\\\""));
        assertEquals("di \"si\"", Literals.parseString("\"di \\\"si\\\"\""));
        assertEquals("it's", Literals.parseString("'it\\'s'"));
    }

    @Test
    void escapeHex() {
        assertEquals("A", Literals.parseString("\"\\x41\""));
        assertEquals(bytes(0xFF), Literals.parseString("\"\\xff\""));
    }

    @Test
    void escapeDecimalLeeHastaTresDigitos() {
        assertEquals("B", Literals.parseString("\"\\066\""));
        assertEquals("A9", Literals.parseString("\"\\0659\""));
        assertEquals("{4", Literals.parseString("\"\\1234\""));
        assertEquals(bytes(0xFF), Literals.parseString("\"\\255\""));
    }

    @Test
    void ceroDentroDelString() {
        // test_cases/01.in: #"Lua\0String" es 10
        String s = Literals.parseString("\"Lua\\0String\"");
        assertEquals(10, s.length());
        assertEquals(0, s.charAt(3));
    }

    @Test
    void escapeDecimalMayorA255EsError() {
        assertThrows(LuaError.class, () -> Literals.parseString("\"\\300\""));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "7F, 7F",
        "80, C2 80",
        "7FF, DF BF",
        "800, E0 A0 80",
        "2603, E2 98 83",
        "FFFF, EF BF BF",
        "10000, F0 90 80 80",
        "10FFFF, F4 8F BF BF",
        "7FFFFFFF, FD BF BF BF BF BF",
    })
    void escapeUtf8(String code, String esperado) {
        String[] partes = esperado.split(" ");
        int[] bs = new int[partes.length];
        for (int k = 0; k < partes.length; k++) bs[k] = Integer.parseInt(partes[k], 16);
        assertEquals(bytes(bs), Literals.parseString(utfToken(code)));
    }

    @Test
    void escapeZSaltaEspaciosYSaltos() {
        assertEquals("ab", Literals.parseString("\"a\\z   \n\t  b\""));
    }

    @Test
    void barraYSaltoDeLinea() {
        assertEquals("a\nb", Literals.parseString("\"a\\\nb\""));
        assertEquals("a\nb", Literals.parseString("\"a\\\r\nb\""));
    }

    @Test
    void stringDelCaso02() {
        String token = "\"Linea1\\n\\tTab\\x41\\066\\" + "u{2603}\\z\n    ContinuacionSinEspacios\"";
        String esperado = "Linea1\n\tTabAB" + bytes(0xE2, 0x98, 0x83) + "ContinuacionSinEspacios";
        assertEquals(esperado, Literals.parseString(token));
    }

    // ---- strings largos ----

    @Test
    void largoNoProcesaEscapes() {
        assertEquals("a\\nb", Literals.parseString("[[a\\nb]]"));
    }

    @Test
    void largoQuitaElPrimerSaltoDeLinea() {
        assertEquals("hola", Literals.parseString("[[\nhola]]"));
        assertEquals("hola", Literals.parseString("[[\r\nhola]]"));
        // solo el primero
        assertEquals("\nx", Literals.parseString("[[\n\nx]]"));
    }

    @Test
    void largoConNivel() {
        assertEquals("a]]b", Literals.parseString("[=[a]]b]=]"));
        assertEquals("", Literals.parseString("[==[]==]"));
    }

    @Test
    void largoDelCaso02() {
        String token = "[==[\nTexto\nMultilinea\nIgnora secuencias \\n y usa literal]==]";
        assertEquals("Texto\nMultilinea\nIgnora secuencias \\n y usa literal", Literals.parseString(token));
    }
}
