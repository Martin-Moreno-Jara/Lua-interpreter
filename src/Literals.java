// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de B.
// Números decimales/hex simples y escapes básicos; sin \x, \ddd, \z ni hex float.
public final class Literals {
    private Literals() {}

    public static LuaValue parseNumber(String t) {
        String s = t.toLowerCase();
        if (s.startsWith("0x")) return LuaValue.of(Long.parseLong(s.substring(2), 16));
        if (s.contains(".") || s.contains("e")) return LuaValue.of(Double.parseDouble(s));
        return LuaValue.of(Long.parseLong(s));
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
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case '0': sb.append('\0'); break;
                default:  sb.append(e);
            }
        }
        return sb.toString();
    }
}
