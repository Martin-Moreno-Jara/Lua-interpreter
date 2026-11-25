// Contrato §5.4 (dueño: B). Frontera entre el evaluador (A) y la semántica de valores (B).
// El evaluador nunca opera valores directamente; los metamétodos viven solo aquí.
// >, >= y ~= los traduce el evaluador a lt/le invertidos y a !eq.
// and/or/not los resuelve el evaluador (cortocircuito).
public final class Ops {
    private Ops() {}

    // Metamétodo `event` de v, o NIL si no tiene. Solo las tablas tienen metatabla en estos casos.
    private static LuaValue metamethod(LuaValue v, String event) {
        if (!(v instanceof LuaTable)) return LuaNil.NIL;
        LuaTable mt = ((LuaTable) v).getMetatable();
        return mt == null ? LuaNil.NIL : mt.rawget(LuaValue.of(event));
    }

    // ===================== ZONA GRUPO 1: aritmética, bits y tostring =====================

    // op: "+","-","*","/","//","%","^","&","|","~","<<",">>"
    public static LuaValue arith(String op, LuaValue a, LuaValue b) {
        char c = op.charAt(0);
        boolean bits = c == '&' || c == '|' || c == '~' || op.equals("<<") || op.equals(">>");
        if (bits) {
            // Los strings no se coercionan en bits (CLAUDE.md §6)
            if (!(a instanceof LuaInteger || a instanceof LuaFloat)
                    || !(b instanceof LuaInteger || b instanceof LuaFloat)) {
                return arithError(op, a, b);
            }
            long x = toIntegerStrict(a);
            long y = toIntegerStrict(b);
            switch (op) {
                case "&": return LuaValue.of(x & y);
                case "|": return LuaValue.of(x | y);
                case "~": return LuaValue.of(x ^ y);
                case "<<": return LuaValue.of(shiftLeft(x, y));
                default: return LuaValue.of(shiftRight(x, y));
            }
        }
        LuaValue x = toNumberCoerce(a);
        LuaValue y = toNumberCoerce(b);
        if (x == null || y == null) return arithError(op, a, b);
        if (x instanceof LuaInteger && y instanceof LuaInteger && !op.equals("/") && !op.equals("^")) {
            long p = ((LuaInteger) x).value;
            long q = ((LuaInteger) y).value;
            switch (op) {
                case "+": return LuaValue.of(p + q);
                case "-": return LuaValue.of(p - q);
                case "*": return LuaValue.of(p * q);
                case "//":
                    if (q == 0) throw new LuaError("attempt to divide by zero"); // texto de lua5.4
                    return LuaValue.of(Math.floorDiv(p, q));
                default: // "%"
                    if (q == 0) throw new LuaError("attempt to perform 'n%0'");
                    return LuaValue.of(Math.floorMod(p, q));
            }
        }
        double p = toDouble(x);
        double q = toDouble(y);
        switch (op) {
            case "+": return LuaValue.of(p + q);
            case "-": return LuaValue.of(p - q);
            case "*": return LuaValue.of(p * q);
            case "/": return LuaValue.of(p / q);
            case "^": return LuaValue.of(Math.pow(p, q));
            case "//": return LuaValue.of(Math.floor(p / q));
            default: { // "%"
                double m = p % q; // igual que fmod de C
                // Lua ajusta el resto para que tenga el signo del divisor
                if (m != 0 && (m < 0) != (q < 0)) m += q;
                return LuaValue.of(m);
            }
        }
    }

    public static LuaValue unm(LuaValue a) {
        LuaValue x = toNumberCoerce(a);
        if (x == null) return arithError("unm", a, a);
        if (x instanceof LuaInteger) return LuaValue.of(-((LuaInteger) x).value);
        return LuaValue.of(-((LuaFloat) x).value);
    }

    public static LuaValue bnot(LuaValue a) {
        // "bnot" y no "~", para no confundirlo con __bxor
        if (!(a instanceof LuaInteger || a instanceof LuaFloat)) return arithError("bnot", a, a);
        return LuaValue.of(~toIntegerStrict(a));
    }

    // Formato de print/tostring. Tablas y funciones: "table: 0x%08x" con identityHashCode.
    public static String tostring(LuaValue v) {
        if (v instanceof LuaNil) return "nil";
        if (v instanceof LuaBoolean) return v.isTruthy() ? "true" : "false";
        if (v instanceof LuaInteger) return Long.toString(((LuaInteger) v).value);
        if (v instanceof LuaFloat) return NumberFormat.format(((LuaFloat) v).value);
        if (v instanceof LuaString) return ((LuaString) v).value;
        return String.format("%s: 0x%08x", v.typeName(), System.identityHashCode(v));
    }

    // Único punto de error de aritmética y bits: aquí se insertará la búsqueda de metamétodos.
    // op es el operador, o "unm" para el menos unario.
    private static LuaValue arithError(String op, LuaValue a, LuaValue b) {
        // Lua busca el metamétodo primero en a y luego en b
        LuaValue h = binaryHandler(arithEvent(op), a, b);
        if (h != LuaNil.NIL) {
            LuaValue[] r = call(h, new LuaValue[]{a, b});
            return r.length > 0 ? r[0] : LuaNil.NIL;
        }
        boolean bits = op.equals("&") || op.equals("|") || op.equals("~") || op.equals("<<")
                || op.equals(">>") || op.equals("bnot");
        if (bits) {
            // Se culpa al primer operando que no es número
            LuaValue bad = (a instanceof LuaInteger || a instanceof LuaFloat) ? b : a;
            throw new LuaError("attempt to perform bitwise operation on a " + bad.typeName() + " value");
        }
        // Un string que no es número también se culpa a él
        LuaValue bad = toNumberCoerce(a) == null ? a : b;
        throw new LuaError("attempt to perform arithmetic on a " + bad.typeName() + " value");
    }

    // Nombre del evento de metamétodo de cada operador
    private static String arithEvent(String op) {
        switch (op) {
            case "+": return "__add";
            case "-": return "__sub";
            case "*": return "__mul";
            case "/": return "__div";
            case "%": return "__mod";
            case "^": return "__pow";
            case "//": return "__idiv";
            case "&": return "__band";
            case "|": return "__bor";
            case "~": return "__bxor";
            case "<<": return "__shl";
            case ">>": return "__shr";
            case "unm": return "__unm";
            default: return "__bnot";
        }
    }

    // Metamétodo de a, o si no tiene el de b; NIL si ninguno
    private static LuaValue binaryHandler(String event, LuaValue a, LuaValue b) {
        LuaValue h = metamethod(a, event);
        return h != LuaNil.NIL ? h : metamethod(b, event);
    }

    private static double toDouble(LuaValue n) {
        return n instanceof LuaInteger ? (double) ((LuaInteger) n).value : ((LuaFloat) n).value;
    }

    // Entero, o float con valor entero exacto. Solo se llama con números.
    private static long toIntegerStrict(LuaValue n) {
        if (n instanceof LuaInteger) return ((LuaInteger) n).value;
        double d = ((LuaFloat) n).value;
        // 2^63 como double es el primer valor que ya no cabe en long
        if (d != Math.floor(d) || d < -9.223372036854775808E18 || d >= 9.223372036854775808E18) {
            throw new LuaError("number has no integer representation");
        }
        return (long) d;
    }

    private static long shiftLeft(long x, long n) {
        if (n <= -64 || n >= 64) return 0;
        // Lógico: >>> rellena con ceros, no con el signo
        return n >= 0 ? x << n : x >>> -n;
    }

    private static long shiftRight(long x, long n) {
        if (n <= -64 || n >= 64) return 0;
        return shiftLeft(x, -n);
    }

    // Número tal cual, o string convertido; null si no es convertible.
    private static LuaValue toNumberCoerce(LuaValue v) {
        if (v instanceof LuaInteger || v instanceof LuaFloat) return v;
        if (!(v instanceof LuaString)) return null;
        String s = ((LuaString) v).value;
        int i = 0;
        int j = s.length();
        while (i < j && isLuaSpace(s.charAt(i))) i++;
        while (j > i && isLuaSpace(s.charAt(j - 1))) j--;
        boolean neg = false;
        if (i < j && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        if (i >= j) return null;
        String body = s.substring(i, j);
        char first = body.charAt(0);
        // Java acepta "NaN", "Infinity" y sufijos d/f; Lua no
        if (!(first >= '0' && first <= '9') && first != '.') return null;
        boolean hex = body.length() > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X');
        char last = body.charAt(body.length() - 1);
        if (!hex && (last == 'd' || last == 'D' || last == 'f' || last == 'F')) return null;
        LuaValue n;
        try {
            n = Literals.parseNumber(body);
        } catch (RuntimeException e) {
            try {
                // Un decimal entero que no cabe en long pasa a float
                if (hex) return null;
                n = LuaValue.of(Double.parseDouble(body));
            } catch (RuntimeException e2) {
                return null;
            }
        }
        if (!neg) return n;
        return n instanceof LuaInteger ? LuaValue.of(-((LuaInteger) n).value) : LuaValue.of(-((LuaFloat) n).value);
    }

    private static boolean isLuaSpace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }

    // ===================== FIN ZONA GRUPO 1 =====================

    // ===================== ZONA GRUPO 2: comparaciones, concat y len =====================

    // Punto unico de error por tipos de este grupo; aqui se consultaria el metamétodo antes de fallar.
    private static LuaError typeErrorGroup2(String msg) {
        return new LuaError(msg);
    }

    // Un metamétodo se trunca a su primer resultado
    private static LuaValue firstResult(LuaValue[] r) {
        return r.length > 0 ? r[0] : LuaNil.NIL;
    }

    private static LuaError compareError(LuaValue a, LuaValue b) {
        String ta = a.typeName(), tb = b.typeName();
        if (ta.equals(tb)) return typeErrorGroup2("attempt to compare two " + ta + " values");
        return typeErrorGroup2("attempt to compare " + ta + " with " + tb);
    }

    private static boolean isNumberG2(LuaValue v) {
        return v instanceof LuaInteger || v instanceof LuaFloat;
    }

    // Texto de un operando de concat, o null si no es string ni numero.
    private static String concatPiece(LuaValue v) {
        if (v instanceof LuaString) return ((LuaString) v).value;
        if (v instanceof LuaInteger) return Long.toString(((LuaInteger) v).value);
        if (v instanceof LuaFloat) return NumberFormat.format(((LuaFloat) v).value);
        return null;
    }

    public static LuaValue concat(LuaValue a, LuaValue b) {
        String sa = concatPiece(a), sb = concatPiece(b);
        if (sa == null || sb == null) {
            LuaValue h = binaryHandler("__concat", a, b);
            if (h != LuaNil.NIL) return firstResult(Ops.call(h, new LuaValue[]{a, b}));
            LuaValue bad = (sa == null) ? a : b;
            throw typeErrorGroup2("attempt to concatenate a " + bad.typeName() + " value");
        }
        return new LuaString(sa + sb);
    }

    public static LuaValue len(LuaValue a) {
        if (a instanceof LuaString) return new LuaInteger(((LuaString) a).value.length());
        if (a instanceof LuaTable) {
            LuaValue h = metamethod(a, "__len");
            if (h != LuaNil.NIL) return firstResult(Ops.call(h, new LuaValue[]{a}));
            return new LuaInteger(((LuaTable) a).length());
        }
        throw typeErrorGroup2("attempt to get length of a " + a.typeName() + " value");
    }

    public static boolean eq(LuaValue a, LuaValue b) {
        if (a == b) {
            // Un NaN es la misma referencia pero no es igual a si mismo.
            return !(a instanceof LuaFloat && Double.isNaN(((LuaFloat) a).value));
        }
        if (isNumberG2(a) && isNumberG2(b)) return numEquals(a, b);
        if (a instanceof LuaString && b instanceof LuaString) {
            return ((LuaString) a).value.equals(((LuaString) b).value);
        }
        if (a instanceof LuaTable && b instanceof LuaTable) {
            LuaValue h = binaryHandler("__eq", a, b);
            return h != LuaNil.NIL && firstResult(Ops.call(h, new LuaValue[]{a, b})).isTruthy();
        }
        // nil y booleanos son unicos; funciones, threads y userdata van por referencia.
        return false;
    }

    // Igualdad matematica exacta entre int y float.
    private static boolean numEquals(LuaValue a, LuaValue b) {
        if (a instanceof LuaInteger && b instanceof LuaInteger) {
            return ((LuaInteger) a).value == ((LuaInteger) b).value;
        }
        if (a instanceof LuaFloat && b instanceof LuaFloat) {
            return ((LuaFloat) a).value == ((LuaFloat) b).value;
        }
        long i;
        double d;
        if (a instanceof LuaInteger) {
            i = ((LuaInteger) a).value;
            d = ((LuaFloat) b).value;
        } else {
            i = ((LuaInteger) b).value;
            d = ((LuaFloat) a).value;
        }
        // Debe ser entero exacto y caber en long.
        return d >= -9.223372036854775808E18 && d < 9.223372036854775808E18
                && d == Math.floor(d) && (long) d == i;
    }

    // i < d sin perder precision (NaN da false).
    private static boolean intLtFloat(long i, double d) {
        if (Double.isNaN(d)) return false;
        if (d >= 9.223372036854775808E18) return true;
        if (d < -9.223372036854775808E18) return false;
        return i < (long) Math.ceil(d);
    }

    private static boolean intLeFloat(long i, double d) {
        if (Double.isNaN(d)) return false;
        if (d >= 9.223372036854775808E18) return true;
        if (d < -9.223372036854775808E18) return false;
        return i <= (long) Math.floor(d);
    }

    private static boolean floatLtInt(double d, long i) {
        if (Double.isNaN(d)) return false;
        if (d >= 9.223372036854775808E18) return false;
        if (d < -9.223372036854775808E18) return true;
        return (long) Math.floor(d) < i;
    }

    private static boolean floatLeInt(double d, long i) {
        if (Double.isNaN(d)) return false;
        if (d >= 9.223372036854775808E18) return false;
        if (d < -9.223372036854775808E18) return true;
        return (long) Math.ceil(d) <= i;
    }

    public static boolean lt(LuaValue a, LuaValue b) {
        if (a instanceof LuaInteger) {
            long x = ((LuaInteger) a).value;
            if (b instanceof LuaInteger) return x < ((LuaInteger) b).value;
            if (b instanceof LuaFloat) return intLtFloat(x, ((LuaFloat) b).value);
        } else if (a instanceof LuaFloat) {
            double x = ((LuaFloat) a).value;
            if (b instanceof LuaFloat) return x < ((LuaFloat) b).value;
            if (b instanceof LuaInteger) return floatLtInt(x, ((LuaInteger) b).value);
        } else if (a instanceof LuaString && b instanceof LuaString) {
            return ((LuaString) a).value.compareTo(((LuaString) b).value) < 0;
        }
        LuaValue h = binaryHandler("__lt", a, b);
        if (h != LuaNil.NIL) return firstResult(Ops.call(h, new LuaValue[]{a, b})).isTruthy();
        throw compareError(a, b);
    }

    public static boolean le(LuaValue a, LuaValue b) {
        if (a instanceof LuaInteger) {
            long x = ((LuaInteger) a).value;
            if (b instanceof LuaInteger) return x <= ((LuaInteger) b).value;
            if (b instanceof LuaFloat) return intLeFloat(x, ((LuaFloat) b).value);
        } else if (a instanceof LuaFloat) {
            double x = ((LuaFloat) a).value;
            if (b instanceof LuaFloat) return x <= ((LuaFloat) b).value;
            if (b instanceof LuaInteger) return floatLeInt(x, ((LuaInteger) b).value);
        } else if (a instanceof LuaString && b instanceof LuaString) {
            return ((LuaString) a).value.compareTo(((LuaString) b).value) <= 0;
        }
        // En 5.4 __le ya no se simula con not __lt
        LuaValue h = binaryHandler("__le", a, b);
        if (h != LuaNil.NIL) return firstResult(Ops.call(h, new LuaValue[]{a, b})).isTruthy();
        throw compareError(a, b);
    }

    // ===================== FIN ZONA GRUPO 2 =====================

    // ===================== ZONA GRUPO 3: indexación y llamada =====================

    // t[k], incluye la cadena __index (función o tabla).
    public static LuaValue index(LuaValue t, LuaValue k) {
        // Bucle en vez de recursión; el límite corta ciclos de __index.
        for (int i = 0; i < META_CHAIN_LIMIT; i++) {
            LuaValue h;
            if (t instanceof LuaTable) {
                LuaValue v = ((LuaTable) t).rawget(k);
                if (v != LuaNil.NIL) return v;
                h = metamethod(t, "__index");
                if (h == LuaNil.NIL) return LuaNil.NIL;
            } else {
                h = metamethod(t, "__index");
                if (h == LuaNil.NIL) throw indexError(t);
            }
            if (h instanceof LuaFunction) {
                LuaValue[] r = call(h, new LuaValue[] { t, k });
                return r.length > 0 ? r[0] : LuaNil.NIL;
            }
            t = h;
        }
        throw new LuaError("'__index' chain too long; possible loop");
    }

    private static final int META_CHAIN_LIMIT = 2000;

    // t[k] = v, incluye __newindex (función o tabla).
    public static void setIndex(LuaValue t, LuaValue k, LuaValue v) {
        for (int i = 0; i < META_CHAIN_LIMIT; i++) {
            LuaValue h;
            if (t instanceof LuaTable) {
                LuaTable tt = (LuaTable) t;
                h = tt.rawget(k) != LuaNil.NIL ? LuaNil.NIL : metamethod(t, "__newindex");
                if (h == LuaNil.NIL) {
                    // rawset ya lanza "index is nil" / "index is NaN".
                    tt.rawset(k, v);
                    return;
                }
            } else {
                h = metamethod(t, "__newindex");
                if (h == LuaNil.NIL) throw indexError(t);
            }
            if (h instanceof LuaFunction) {
                call(h, new LuaValue[] { t, k, v });
                return;
            }
            t = h;
        }
        throw new LuaError("'__newindex' chain too long; possible loop");
    }

    // Llamada a f, incluye __call.
    public static LuaValue[] call(LuaValue f, LuaValue[] args) {
        // __call puede ser otra tabla con __call, por eso es un bucle.
        for (int i = 0; i < META_CHAIN_LIMIT; i++) {
            if (f instanceof LuaFunction) {
                LuaValue[] r = ((LuaFunction) f).call(args);
                // El contrato prohíbe null, pero así no se propaga si una nativa se equivoca.
                return r == null ? LuaValue.NONE : r;
            }
            LuaValue h = metamethod(f, "__call");
            if (h == LuaNil.NIL) throw callError(f);
            // El objeto llamado pasa como primer argumento.
            LuaValue[] nuevos = new LuaValue[args.length + 1];
            nuevos[0] = f;
            System.arraycopy(args, 0, nuevos, 1, args.length);
            args = nuevos;
            f = h;
        }
        throw new LuaError("'__call' chain too long; possible loop");
    }

    private static LuaError indexError(LuaValue t) {
        return new LuaError("attempt to index a " + t.typeName() + " value");
    }

    private static LuaError callError(LuaValue f) {
        return new LuaError("attempt to call a " + f.typeName() + " value");
    }

    // ===================== FIN ZONA GRUPO 3 =====================
}
