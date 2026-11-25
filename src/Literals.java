// Contrato §5.7 (dueño: B). Decodificación de literales; la usa el evaluador (A).
public final class Literals {
    private Literals() {}

    // Texto de un token INT, HEX, FLOAT o HEX_FLOAT -> LuaInteger o LuaFloat.
    // Ej.: "345", "0xBEBADA", "314.16e-2", "0X1.921FB54442D18P+1".
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

    // Texto completo de un token NORMALSTRING, CHARSTRING o LONGSTRING,
    // incluidas comillas o corchetes -> contenido decodificado (un char por byte).
    // Ojo: Java procesa la barra seguida de u como escape Unicode incluso en
    // comentarios, por eso aquí se escribe "barra-u".
    public static String parseString(String tokenText) {
        if (tokenText.charAt(0) == '[') return parseLongString(tokenText);
        return decodeEscapes(tokenText.substring(1, tokenText.length() - 1));
    }

    // [==[ ... ]==]: se copian tal cual, sin procesar escapes.
    private static String parseLongString(String tokenText) {
        int level = tokenText.indexOf('[', 1) - 1;
        String body = tokenText.substring(level + 2, tokenText.length() - level - 2);
        // Lua descarta el salto de línea pegado al corchete de apertura
        if (body.startsWith("\r\n")) return body.substring(2);
        if (body.startsWith("\n")) return body.substring(1);
        return body;
    }

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

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\013';
    }

    // Los strings de Lua son bytes: el carácter se guarda como sus bytes UTF-8.
    // Lua acepta códigos hasta 2^31, así que puede usar hasta 6 bytes.
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
