import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Los valores esperados se obtuvieron de lua5.4 con math.type, print y los bytes de cada string.
/** Pruebas de Literals: números y strings escritos en el código fuente. */
public class LiteralsTest {

    // ---- Números ----

    /** Cada número escrito en el código da el tipo (entero o decimal) y el valor que muestra lua5.4. */
    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource({
        // Enteros en base 10 y en base 16 (hexadecimal)
        "345, integer, 345",
        "0, integer, 0",
        "0xBEBADA, integer, 12499674",
        "0xff, integer, 255",
        "0XA, integer, 10",
        // El punto o el exponente lo vuelven decimal
        "314.16e-2, float, 3.1416",
        "3.14, float, 3.14",
        "10.0, float, 10.0",
        "5., float, 5.0",
        ".5, float, 0.5",
        "1e2, float, 100.0",
        "2E-3, float, 0.002",
        // Decimales en hexadecimal, con y sin exponente p
        "0X1.921FB54442D18P+1, float, 3.1415926535898",
        "0x1.8, float, 1.5",
        "0x.8, float, 0.5",
        "0xAp2, float, 40.0",
        "0x1P-1, float, 0.5",
        "0xA.8p0, float, 10.5",
    })
    void numberLikeLua(String token, String type, String printed) {
        LuaValue v = Literals.parseNumber(token);
        if (type.equals("integer")) {
            assertInstanceOf(LuaInteger.class, v);
            assertEquals(printed, Long.toString(((LuaInteger) v).value));
        } else {
            assertInstanceOf(LuaFloat.class, v);
            assertEquals(printed, NumberFormat.format(((LuaFloat) v).value));
        }
    }

    // En lua5.4, 0X1.921FB54442D18P+1 == math.pi da true
    /** Un decimal en hexadecimal se lee sin perder precisión. */
    @Test
    void hexFloatIsExact() {
        assertEquals(LuaValue.of(Math.PI), Literals.parseNumber("0X1.921FB54442D18P+1"));
    }

    // ---- Strings entre comillas ----

    /** Arma un string de Java a partir de bytes, para comparar con los bytes UTF-8 esperados. */
    private static String bytes(int... bs) {
        StringBuilder sb = new StringBuilder();
        for (int b : bs) sb.append((char) b);
        return sb.toString();
    }

    // Se arma por partes porque escribir la barra invertida seguida de u impediría compilar este archivo.
    /** Token "barra-u{code}" entre comillas, con el código del carácter dado. */
    private static String utfToken(String code) {
        return "\"\\" + "u{" + code + "}\"";
    }

    /** Un string sin barras invertidas se copia tal cual, con comillas dobles o simples. */
    @Test
    void plainString() {
        assertEquals("hola", Literals.parseString("\"hola\""));
        assertEquals("hola", Literals.parseString("'hola'"));
        assertEquals("", Literals.parseString("\"\""));
    }

    /** Las secuencias de una letra (\n, \t, \a...) dan el carácter que representan. */
    @Test
    void singleLetterEscapes() {
        assertEquals("a\nb\tc", Literals.parseString("\"a\\nb\\tc\""));
        assertEquals(bytes(0x07, 0x08, 0x0C, 0x0B, 0x0D), Literals.parseString("\"\\a\\b\\f\\v\\r\""));
        assertEquals("\\", Literals.parseString("\"\\\\\""));
        assertEquals("di \"si\"", Literals.parseString("\"di \\\"si\\\"\""));
        assertEquals("it's", Literals.parseString("'it\\'s'"));
    }

    /** \xXX da el byte con ese valor hexadecimal. */
    @Test
    void hexEscape() {
        assertEquals("A", Literals.parseString("\"\\x41\""));
        assertEquals(bytes(0xFF), Literals.parseString("\"\\xff\""));
    }

    /** \ddd lee como máximo tres dígitos decimales; lo que sigue es texto normal. */
    @Test
    void decimalEscapeReadsUpToThreeDigits() {
        assertEquals("B", Literals.parseString("\"\\066\""));
        assertEquals("A9", Literals.parseString("\"\\0659\""));
        assertEquals("{4", Literals.parseString("\"\\1234\""));
        assertEquals(bytes(0xFF), Literals.parseString("\"\\255\""));
    }

    // test_cases/01.in: #"Lua\0String" es 10
    /** Un byte 0 en medio del string es un carácter más, no el final del texto. */
    @Test
    void zeroByteInsideString() {
        String s = Literals.parseString("\"Lua\\0String\"");
        assertEquals(10, s.length());
        assertEquals(0, s.charAt(3));
    }

    /** Un escape decimal mayor que 255 es un error, porque no cabe en un byte. */
    @Test
    void decimalEscapeAbove255IsError() {
        assertThrows(LuaError.class, () -> Literals.parseString("\"\\300\""));
    }

    /** El escape de un código de carácter da sus bytes UTF-8, de 1 a 6 bytes. */
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
    void utf8Escape(String code, String expected) {
        String[] parts = expected.split(" ");
        int[] bs = new int[parts.length];
        for (int k = 0; k < parts.length; k++) bs[k] = Integer.parseInt(parts[k], 16);
        assertEquals(bytes(bs), Literals.parseString(utfToken(code)));
    }

    /** \z se salta los espacios y saltos de línea que le siguen. */
    @Test
    void zEscapeSkipsWhitespace() {
        assertEquals("ab", Literals.parseString("\"a\\z   \n\t  b\""));
    }

    /** Una barra invertida antes de un salto de línea da un salto de línea, también con \r\n. */
    @Test
    void backslashNewline() {
        assertEquals("a\nb", Literals.parseString("\"a\\\nb\""));
        assertEquals("a\nb", Literals.parseString("\"a\\\r\nb\""));
    }

    /** El string con escapes mezclados de test_cases/02.in. */
    @Test
    void stringFromCase02() {
        String token = "\"Linea1\\n\\tTab\\x41\\066\\" + "u{2603}\\z\n    ContinuacionSinEspacios\"";
        String expected = "Linea1\n\tTabAB" + bytes(0xE2, 0x98, 0x83) + "ContinuacionSinEspacios";
        assertEquals(expected, Literals.parseString(token));
    }

    // ---- Strings largos [[ ]] ----

    /** Un string largo no interpreta las barras invertidas. */
    @Test
    void longStringKeepsBackslashes() {
        assertEquals("a\\nb", Literals.parseString("[[a\\nb]]"));
    }

    /** Un string largo descarta solo el primer salto de línea, el que va pegado a los corchetes. */
    @Test
    void longStringDropsFirstNewline() {
        assertEquals("hola", Literals.parseString("[[\nhola]]"));
        assertEquals("hola", Literals.parseString("[[\r\nhola]]"));
        // Solo el primero
        assertEquals("\nx", Literals.parseString("[[\n\nx]]"));
    }

    /** Con signos = entre los corchetes, ]] dentro del texto no cierra el string. */
    @Test
    void longStringWithLevel() {
        assertEquals("a]]b", Literals.parseString("[=[a]]b]=]"));
        assertEquals("", Literals.parseString("[==[]==]"));
    }

    /** El string largo de test_cases/02.in. */
    @Test
    void longStringFromCase02() {
        String token = "[==[\nTexto\nMultilinea\nIgnora secuencias \\n y usa literal]==]";
        assertEquals("Texto\nMultilinea\nIgnora secuencias \\n y usa literal", Literals.parseString(token));
    }
}
