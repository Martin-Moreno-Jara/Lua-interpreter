// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de B.
// Cubre lo común (aritmética int/float, coerción simple, comparaciones, __index,
// __newindex, __call y metamétodos aritméticos), sin pulir mensajes ni bordes.
public final class Ops {
    private Ops() {}

    private static LuaValue meta(LuaValue v, String event) {
        if (v instanceof LuaTable) {
            LuaTable mt = ((LuaTable) v).getMetatable();
            if (mt != null) return mt.rawget(LuaValue.of(event));
        }
        return LuaNil.NIL;
    }

    private static LuaValue first(LuaValue[] r) { return r.length > 0 ? r[0] : LuaNil.NIL; }

    // Metamétodo binario: primero el de a, luego el de b. null si ninguno lo tiene.
    private static LuaValue binMeta(String event, LuaValue a, LuaValue b) {
        LuaValue h = meta(a, event);
        if (h == LuaNil.NIL) h = meta(b, event);
        if (h == LuaNil.NIL) return null;
        return first(call(h, new LuaValue[] { a, b }));
    }

    private static LuaValue tonumber(LuaValue v) {
        if (v instanceof LuaInteger || v instanceof LuaFloat) return v;
        if (v instanceof LuaString) {
            try { return Literals.parseNumber(((LuaString) v).value.trim()); }
            catch (RuntimeException e) { return null; }
        }
        return null;
    }

    private static double dbl(LuaValue n) {
        return n instanceof LuaInteger ? ((LuaInteger) n).value : ((LuaFloat) n).value;
    }

    private static String event(String op) {
        switch (op) {
            case "+": return "__add";  case "-": return "__sub";  case "*": return "__mul";
            case "/": return "__div";  case "%": return "__mod";  case "^": return "__pow";
            case "//": return "__idiv"; case "&": return "__band"; case "|": return "__bor";
            case "~": return "__bxor"; case "<<": return "__shl"; case ">>": return "__shr";
        }
        throw new IllegalArgumentException(op);
    }

    private static Long toInt(LuaValue v) {
        if (v instanceof LuaInteger) return ((LuaInteger) v).value;
        if (v instanceof LuaFloat) {
            double d = ((LuaFloat) v).value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) return (long) d;
        }
        return null;
    }

    private static long shl(long x, long n) {
        if (n <= -64 || n >= 64) return 0;
        return n >= 0 ? x << n : x >>> -n;
    }

    public static LuaValue arith(String op, LuaValue a, LuaValue b) {
        boolean bitwise = op.equals("&") || op.equals("|") || op.equals("~") || op.equals("<<") || op.equals(">>");
        if (bitwise) {
            Long x = toInt(a), y = toInt(b);
            if (x == null || y == null) {
                LuaValue r = binMeta(event(op), a, b);
                if (r != null) return r;
                LuaValue bad = x == null ? a : b;
                throw new LuaError("attempt to perform bitwise operation on a " + bad.typeName() + " value");
            }
            switch (op) {
                case "&": return LuaValue.of(x & y);
                case "|": return LuaValue.of(x | y);
                case "~": return LuaValue.of(x ^ y);
                case "<<": return LuaValue.of(shl(x, y));
                default:  return LuaValue.of(shl(x, -y));
            }
        }
        LuaValue x = tonumber(a), y = tonumber(b);
        if (x == null || y == null) {
            LuaValue r = binMeta(event(op), a, b);
            if (r != null) return r;
            LuaValue bad = x == null ? a : b;
            throw new LuaError("attempt to perform arithmetic on a " + bad.typeName() + " value");
        }
        if (x instanceof LuaInteger && y instanceof LuaInteger && !op.equals("/") && !op.equals("^")) {
            long p = ((LuaInteger) x).value, q = ((LuaInteger) y).value;
            switch (op) {
                case "+": return LuaValue.of(p + q);
                case "-": return LuaValue.of(p - q);
                case "*": return LuaValue.of(p * q);
                case "//":
                    if (q == 0) throw new LuaError("attempt to perform 'n//0'");
                    return LuaValue.of(Math.floorDiv(p, q));
                case "%":
                    if (q == 0) throw new LuaError("attempt to perform 'n%%0'");
                    return LuaValue.of(Math.floorMod(p, q));
            }
        }
        double p = dbl(x), q = dbl(y);
        switch (op) {
            case "+": return LuaValue.of(p + q);
            case "-": return LuaValue.of(p - q);
            case "*": return LuaValue.of(p * q);
            case "/": return LuaValue.of(p / q);
            case "^": return LuaValue.of(Math.pow(p, q));
            case "//": return LuaValue.of(Math.floor(p / q));
            default: {
                double m = p % q;
                if (m != 0 && (m < 0) != (q < 0)) m += q;
                return LuaValue.of(m);
            }
        }
    }

    public static LuaValue unm(LuaValue a) {
        LuaValue x = tonumber(a);
        if (x instanceof LuaInteger) return LuaValue.of(-((LuaInteger) x).value);
        if (x instanceof LuaFloat) return LuaValue.of(-((LuaFloat) x).value);
        LuaValue r = binMeta("__unm", a, a);
        if (r != null) return r;
        throw new LuaError("attempt to perform arithmetic on a " + a.typeName() + " value");
    }

    public static LuaValue bnot(LuaValue a) {
        Long x = toInt(a);
        if (x != null) return LuaValue.of(~x);
        LuaValue r = binMeta("__bnot", a, a);
        if (r != null) return r;
        throw new LuaError("attempt to perform bitwise operation on a " + a.typeName() + " value");
    }

    private static boolean strOrNum(LuaValue v) {
        return v instanceof LuaString || v instanceof LuaInteger || v instanceof LuaFloat;
    }

    public static LuaValue concat(LuaValue a, LuaValue b) {
        if (strOrNum(a) && strOrNum(b)) return LuaValue.of(tostring(a) + tostring(b));
        LuaValue r = binMeta("__concat", a, b);
        if (r != null) return r;
        LuaValue bad = strOrNum(a) ? b : a;
        throw new LuaError("attempt to concatenate a " + bad.typeName() + " value");
    }

    public static LuaValue len(LuaValue a) {
        if (a instanceof LuaString) return LuaValue.of((long) ((LuaString) a).value.length());
        LuaValue h = meta(a, "__len");
        if (h != LuaNil.NIL) return first(call(h, new LuaValue[] { a }));
        if (a instanceof LuaTable) return LuaValue.of(((LuaTable) a).length());
        throw new LuaError("attempt to get length of a " + a.typeName() + " value");
    }

    private static boolean isNum(LuaValue v) { return v instanceof LuaInteger || v instanceof LuaFloat; }

    public static boolean eq(LuaValue a, LuaValue b) {
        if (a == b) return true;
        if (isNum(a) && isNum(b)) return dbl(a) == dbl(b);
        if (a.equals(b)) return true;
        if (a instanceof LuaTable && b instanceof LuaTable) {
            LuaValue r = binMeta("__eq", a, b);
            return r != null && r.isTruthy();
        }
        return false;
    }

    private static boolean compare(String event, LuaValue a, LuaValue b, boolean le) {
        if (isNum(a) && isNum(b)) return le ? dbl(a) <= dbl(b) : dbl(a) < dbl(b);
        if (a instanceof LuaString && b instanceof LuaString) {
            int c = ((LuaString) a).value.compareTo(((LuaString) b).value);
            return le ? c <= 0 : c < 0;
        }
        LuaValue r = binMeta(event, a, b);
        if (r != null) return r.isTruthy();
        if (a.typeName().equals(b.typeName()))
            throw new LuaError("attempt to compare two " + a.typeName() + " values");
        throw new LuaError("attempt to compare " + a.typeName() + " with " + b.typeName());
    }

    public static boolean lt(LuaValue a, LuaValue b) { return compare("__lt", a, b, false); }

    public static boolean le(LuaValue a, LuaValue b) { return compare("__le", a, b, true); }

    public static LuaValue index(LuaValue t, LuaValue k) {
        while (true) {
            LuaValue h;
            if (t instanceof LuaTable) {
                LuaValue v = ((LuaTable) t).rawget(k);
                if (v != LuaNil.NIL) return v;
                h = meta(t, "__index");
                if (h == LuaNil.NIL) return LuaNil.NIL;
            } else {
                h = meta(t, "__index");
                if (h == LuaNil.NIL) throw new LuaError("attempt to index a " + t.typeName() + " value");
            }
            if (h instanceof LuaFunction) return first(call(h, new LuaValue[] { t, k }));
            t = h;
        }
    }

    public static void setIndex(LuaValue t, LuaValue k, LuaValue v) {
        while (true) {
            LuaValue h;
            if (t instanceof LuaTable) {
                LuaTable tt = (LuaTable) t;
                h = meta(t, "__newindex");
                if (tt.rawget(k) != LuaNil.NIL || h == LuaNil.NIL) { tt.rawset(k, v); return; }
            } else {
                h = meta(t, "__newindex");
                if (h == LuaNil.NIL) throw new LuaError("attempt to index a " + t.typeName() + " value");
            }
            if (h instanceof LuaFunction) { call(h, new LuaValue[] { t, k, v }); return; }
            t = h;
        }
    }

    public static LuaValue[] call(LuaValue f, LuaValue[] args) {
        if (f instanceof LuaFunction) return ((LuaFunction) f).call(args);
        LuaValue h = meta(f, "__call");
        if (h == LuaNil.NIL) throw new LuaError("attempt to call a " + f.typeName() + " value");
        LuaValue[] a = new LuaValue[args.length + 1];
        a[0] = f;
        System.arraycopy(args, 0, a, 1, args.length);
        return call(h, a);
    }

    public static String tostring(LuaValue v) {
        if (v instanceof LuaString) return ((LuaString) v).value;
        if (v instanceof LuaInteger) return Long.toString(((LuaInteger) v).value);
        if (v instanceof LuaFloat) return NumberFormat.format(((LuaFloat) v).value);
        if (v instanceof LuaNil || v instanceof LuaBoolean) return v.toString();
        String kind = v.typeName();
        return String.format("%s: 0x%08x", kind, System.identityHashCode(v));
    }
}
