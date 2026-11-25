/** Convierte el texto de números y strings escritos en el código fuente a valores de Lua. Contrato §5.7. */
// La usa el evaluador (A), que le pasa el texto completo de cada token tal como lo entrega ANTLR.
public final class Literals {
    private Literals() {}

    // ---- Números ----

    /** Convierte un número escrito en el código ("345", "0xBEBADA", "314.16e-2", "0x1.8p1") en entero o decimal. */
    public static LuaValue parseNumber(String tokenText) {
        boolean hex = tokenText.length() > 1 && (tokenText.charAt(1) == 'x' || tokenText.charAt(1) == 'X');
        if (hex) {
            String digits = tokenText.substring(2);
            // En hex la 'e' es un dígito, así que solo el punto o la 'p' lo hacen float
            if (digits.indexOf('.') < 0 && digits.indexOf('p') < 0 && digits.indexOf('P') < 0) {
                return LuaValue.of(Long.parseLong(digits, 16));
            }
            // Java exige la 'p' en los hex float, Lua no: 0x1.8 vale 1.5
            String text = digits.indexOf('p') < 0 && digits.indexOf('P') < 0 ? tokenText + "p0" : tokenText;
            return LuaValue.of(Double.parseDouble(text));
        }
        if (tokenText.indexOf('.') < 0 && tokenText.indexOf('e') < 0 && tokenText.indexOf('E') < 0) {
            return LuaValue.of(Long.parseLong(tokenText));
        }
        return LuaValue.of(Double.parseDouble(tokenText));
    }

    // ---- Strings ----

    // Java interpreta la barra invertida seguida de la letra u como un carácter especial
    // incluso dentro de comentarios, y el archivo deja de compilar. Por eso en este
    // archivo esa combinación se describe con palabras en vez de escribirla.
    /** Convierte un string escrito en el código, con sus comillas o corchetes, en su contenido real (un carácter por byte). */
    public static String parseString(String tokenText) {
        if (tokenText.charAt(0) == '[') return parseLongString(tokenText);
        return decodeEscapes(tokenText.substring(1, tokenText.length() - 1));
    }

    /** Contenido de un string largo [==[ ... ]==], que se copia tal cual, sin interpretar las barras invertidas. */
    private static String parseLongString(String tokenText) {
        int level = tokenText.indexOf('[', 1) - 1;
        String body = tokenText.substring(level + 2, tokenText.length() - level - 2);
        // Lua descarta el salto de línea pegado al corchete de apertura
        if (body.startsWith("\r\n")) return body.substring(2);
        if (body.startsWith("\n")) return body.substring(1);
        return body;
    }

    /** Reemplaza cada secuencia que empieza con barra invertida (\n, \t, \x41, \65...) por el carácter que representa. */
    private static String decodeEscapes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'a': out.append('\007'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'v': out.append('\013'); break;
                case '\\': out.append('\\'); break;
                case '"': out.append('"'); break;
                case '\'': out.append('\''); break;
                case '\r':
                    // barra + salto de línea es un salto de línea; \r\n cuenta como uno solo
                    if (i < s.length() && s.charAt(i) == '\n') i++;
                    out.append('\n');
                    break;
                case '\n':
                    out.append('\n');
                    break;
                case 'x': {
                    out.append((char) Integer.parseInt(s.substring(i, i + 2), 16));
                    i += 2;
                    break;
                }
                case 'z':
                    // \z se salta los espacios y saltos de línea que siguen
                    while (i < s.length() && isSpace(s.charAt(i))) i++;
                    break;
                case 'u': {
                    int close = s.indexOf('}', i);
                    long code = Long.parseLong(s.substring(i + 1, close), 16);
                    appendUtf8(out, code);
                    i = close + 1;
                    break;
                }
                default: {
                    if (e < '0' || e > '9') throw new LuaError("invalid escape sequence");
                    // \ddd: hasta 3 dígitos decimales
                    int value = e - '0';
                    for (int k = 0; k < 2 && i < s.length() && Character.isDigit(s.charAt(i)); k++) {
                        value = value * 10 + (s.charAt(i++) - '0');
                    }
                    // la gramática deja pasar hasta \299, pero un byte llega a 255
                    if (value > 255) throw new LuaError("decimal escape too large");
                    out.append((char) value);
                }
            }
        }
        return out.toString();
    }

    /** Indica si el carácter es un espacio en blanco para Lua, incluidos los saltos de línea. */
    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\013';
    }

    // Los strings de Lua son bytes, así que el carácter se guarda con los bytes de UTF-8, la codificación
    // más común de texto. Lua acepta códigos hasta 2^31, por eso un carácter puede ocupar hasta 6 bytes.
    /** Agrega al texto los bytes que representan el carácter con el código dado. */
    private static void appendUtf8(StringBuilder out, long code) {
        if (code < 0x80) {
            out.append((char) code);
            return;
        }
        char[] bytes = new char[6];
        int n = 0;
        long firstMax = 0x3f; // lo que cabe en el primer byte, se achica con cada byte extra
        do {
            bytes[n++] = (char) (0x80 | (code & 0x3f));
            code >>= 6;
            firstMax >>= 1;
        } while (code > firstMax);
        bytes[n++] = (char) (((~firstMax << 1) | code) & 0xff);
        for (int k = n - 1; k >= 0; k--) out.append(bytes[k]);
    }
}
