import java.math.BigInteger;

// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de B.
public final class Literals {
    private Literals() {}

    public static LuaValue parseNumber(String t) {
        String s = t.trim().toLowerCase();
        boolean neg = s.startsWith("-");
        if (neg || s.startsWith("+")) s = s.substring(1);
        LuaValue v;
        if (s.startsWith("0x")) {
            if (s.contains(".") || s.contains("p")) {
                if (!s.contains("p")) s = s + "p0";            // Java exige el exponente
                v = LuaValue.of(Double.parseDouble(s));
            } else {
                v = LuaValue.of(new BigInteger(s.substring(2), 16).longValue());   // da la vuelta como Lua
            }
        } else if (s.contains(".") || s.contains("e") || s.equals("inf") || s.equals("nan")) {
            if (s.equals("inf") || s.equals("nan")) throw new NumberFormatException(s);
            v = LuaValue.of(Double.parseDouble(s));
        } else {
            try {
                v = LuaValue.of(Long.parseLong(s));
            } catch (NumberFormatException e) {
                v = LuaValue.of(Double.parseDouble(s));         // entero demasiado grande: float
            }
        }
        if (!neg) return v;
        if (v instanceof LuaInteger) return LuaValue.of(-((LuaInteger) v).value);
        return LuaValue.of(-((LuaFloat) v).value);
    }

    public static String parseString(String t) {
        if (t.startsWith("[")) {                       // [[...]] o [==[...]==]
            int open = t.indexOf('[', 1) + 1;
            String body = t.substring(open, t.length() - open);
            if (body.startsWith("\r\n")) return body.substring(2);
            if (body.startsWith("\n")) return body.substring(1);
            return body;
        }
        String body = t.substring(1, t.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') { sb.append(c); continue; }
            char e = body.charAt(++i);
            switch (e) {
                case 'a': sb.append((char) 7); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'v': sb.append((char) 11); break;
                case '\n': sb.append('\n'); break;
                case 'x':
                    sb.append((char) Integer.parseInt(body.substring(i + 1, i + 3), 16));
                    i += 2;
                    break;
                case 'z':
                    while (i + 1 < body.length() && Character.isWhitespace(body.charAt(i + 1))) i++;
                    break;
                case 'u': {                            // barra-u{XXX}: código a UTF-8, un char por byte
                    int close = body.indexOf('}', i);
                    int cp = Integer.parseInt(body.substring(i + 2, close), 16);
                    byte[] utf8 = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    for (byte b : utf8) sb.append((char) (b & 0xFF));
                    i = close;
                    break;
                }
                default:
                    if (Character.isDigit(e)) {        // \ddd decimal, hasta 3 dígitos
                        int j = i, n = 0;
                        while (j < body.length() && j < i + 3 && Character.isDigit(body.charAt(j))) {
                            n = n * 10 + (body.charAt(j) - '0');
                            j++;
                        }
                        sb.append((char) n);
                        i = j - 1;
                    } else {
                        sb.append(e);                  // \\ \" \'
                    }
            }
        }
        return sb.toString();
    }
}
