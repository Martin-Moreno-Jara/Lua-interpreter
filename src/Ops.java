// El evaluador (A) nunca opera valores directamente: todo pasa por aquí, y por eso los
// metamétodos (funciones que una tabla define para cambiar cómo se comporta un operador)
// viven solo en esta clase. El evaluador convierte a > b en lt(b, a), a >= b en le(b, a)
// y a ~= b en !eq(a, b). Los operadores and, or y not los resuelve el evaluador, porque
// pueden terminar sin evaluar el segundo operando.
/** Semántica de los operadores de Lua: aritmética, comparación, indexación y llamadas. Contrato §5.4. */
public final class Ops {
    private Ops() {}

    // ---- Constantes y metamétodos ----

    // 2^63 como decimal: el primer valor que ya no cabe en un entero de Java (long).
    private static final double TWO_POW_63 = 9.223372036854775808E18;

    // Máximo de saltos al seguir una cadena de __index, __newindex o __call. Si una tabla
    // termina apuntándose a sí misma, el límite evita que el intérprete se quede colgado.
    private static final int META_CHAIN_LIMIT = 2000;

    /** Metamétodo llamado event que define v, o NIL si no tiene; en estos casos solo las tablas tienen metatabla. */
    private static LuaValue metamethod(LuaValue v, String event) {
        if (!(v instanceof LuaTable)) return LuaNil.NIL;
        LuaTable mt = ((LuaTable) v).getMetatable();
        return mt == null ? LuaNil.NIL : mt.rawget(LuaValue.of(event));
    }

    /** Metamétodo del primer operando o, si no tiene, el del segundo; NIL si ninguno lo define. */
    private static LuaValue binaryMetamethod(String event, LuaValue a, LuaValue b) {
        LuaValue h = metamethod(a, event);
        return h != LuaNil.NIL ? h : metamethod(b, event);
    }

    // Un metamétodo puede devolver varios valores, pero un operador solo usa el primero.
    /** Primer resultado de una llamada, o NIL si no devolvió nada. */
    private static LuaValue firstResult(LuaValue[] r) {
        return r.length > 0 ? r[0] : LuaNil.NIL;
    }

    // ---- Aritmética y bits ----

    /** Aplica un operador binario: "+", "-", "*", "/", "//", "%", "^", "&", "|", "~", "<<" o ">>". */
    public static LuaValue arith(String op, LuaValue a, LuaValue b) {
        if (isBitwise(op)) {
            // Los strings no se convierten a número en operaciones de bits (CLAUDE.md §6)
            if (!isNumber(a) || !isNumber(b)) return arithFallback(op, a, b);
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
        LuaValue x = toNumber(a);
        LuaValue y = toNumber(b);
        if (x == null || y == null) return arithFallback(op, a, b);
        // Entre enteros el resultado es entero, salvo / y ^, que en Lua siempre dan decimal
        if (x instanceof LuaInteger && y instanceof LuaInteger && !op.equals("/") && !op.equals("^")) {
            long p = ((LuaInteger) x).value;
            long q = ((LuaInteger) y).value;
            switch (op) {
                case "+": return LuaValue.of(p + q);
                case "-": return LuaValue.of(p - q);
                case "*": return LuaValue.of(p * q);
                case "//":
                    if (q == 0) throw new LuaError("attempt to divide by zero"); // mismo texto que lua5.4
                    // Lua redondea hacia abajo, no hacia cero: -10 // 3 da -4
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
                double m = p % q;
                // Java deja el resto con el signo del dividendo; Lua lo ajusta al signo del divisor
                if (m != 0 && (m < 0) != (q < 0)) m += q;
                return LuaValue.of(m);
            }
        }
    }

    /** Menos unario (-a). */
    public static LuaValue unm(LuaValue a) {
        LuaValue x = toNumber(a);
        if (x == null) return arithFallback("unm", a, a);
        if (x instanceof LuaInteger) return LuaValue.of(-((LuaInteger) x).value);
        return LuaValue.of(-((LuaFloat) x).value);
    }

    /** Negación de bits (~a). */
    public static LuaValue bnot(LuaValue a) {
        // Se marca como "bnot" y no como "~" para que no se busque __bxor, que es el ~ de dos operandos
        if (!isNumber(a)) return arithFallback("bnot", a, a);
        return LuaValue.of(~toIntegerStrict(a));
    }

    // op es el operador, o "unm" y "bnot" para las operaciones de un solo operando.
    /** Busca el metamétodo de la operación y lo llama; si ningún operando lo define, lanza el error de tipo. */
    private static LuaValue arithFallback(String op, LuaValue a, LuaValue b) {
        LuaValue h = binaryMetamethod(arithEvent(op), a, b);
        if (h != LuaNil.NIL) return firstResult(call(h, new LuaValue[] { a, b }));
        if (isBitwise(op)) {
            // Se culpa al primer operando que no es número
            LuaValue bad = isNumber(a) ? b : a;
            throw new LuaError("attempt to perform bitwise operation on a " + bad.typeName() + " value");
        }
        // Un string que no representa un número también tiene la culpa
        LuaValue bad = toNumber(a) == null ? a : b;
        throw new LuaError("attempt to perform arithmetic on a " + bad.typeName() + " value");
    }

    /** Nombre del metamétodo que corresponde a cada operador. */
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

    /** Indica si el operador trabaja sobre los bits del número. */
    private static boolean isBitwise(String op) {
        return op.equals("&") || op.equals("|") || op.equals("~") || op.equals("<<")
                || op.equals(">>") || op.equals("bnot");
    }

    /** Indica si el valor es un número, entero o decimal. */
    private static boolean isNumber(LuaValue v) {
        return v instanceof LuaInteger || v instanceof LuaFloat;
    }

    /** Valor de un número como decimal de Java. */
    private static double toDouble(LuaValue n) {
        return n instanceof LuaInteger ? (double) ((LuaInteger) n).value : ((LuaFloat) n).value;
    }

    /** Valor entero de un número; un decimal solo se acepta si no tiene parte fraccionaria, como 10.0. */
    private static long toIntegerStrict(LuaValue n) {
        if (n instanceof LuaInteger) return ((LuaInteger) n).value;
        double d = ((LuaFloat) n).value;
        if (d != Math.floor(d) || d < -TWO_POW_63 || d >= TWO_POW_63) {
            throw new LuaError("number has no integer representation");
        }
        return (long) d;
    }

    /** Desplaza los bits de x n posiciones a la izquierda; un n negativo desplaza a la derecha. */
    private static long shiftLeft(long x, long n) {
        if (n <= -64 || n >= 64) return 0; // todos los bits salen del número
        // >>> rellena con ceros, no con el signo, como hace Lua
        return n >= 0 ? x << n : x >>> -n;
    }

    /** Desplaza los bits de x n posiciones a la derecha; un n negativo desplaza a la izquierda. */
    private static long shiftRight(long x, long n) {
        if (n <= -64 || n >= 64) return 0;
        return shiftLeft(x, -n);
    }

    /** El número tal cual, o el string convertido a número (" -10 " da -10); null si no se puede convertir. */
    private static LuaValue toNumber(LuaValue v) {
        if (isNumber(v)) return v;
        if (!(v instanceof LuaString)) return null;
        String s = ((LuaString) v).value;
        int i = 0;
        int j = s.length();
        while (i < j && isLuaSpace(s.charAt(i))) i++;
        while (j > i && isLuaSpace(s.charAt(j - 1))) j--;
        // Literals no acepta signo, porque en el código fuente el signo es un operador aparte
        boolean neg = false;
        if (i < j && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        if (i >= j) return null;
        String body = s.substring(i, j);
        char first = body.charAt(0);
        // Java acepta "NaN", "Infinity" y terminaciones como "1d" o "1f"; Lua no
        if (!(first >= '0' && first <= '9') && first != '.') return null;
        boolean hex = body.length() > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X');
        char last = body.charAt(body.length() - 1);
        if (!hex && (last == 'd' || last == 'D' || last == 'f' || last == 'F')) return null;
        LuaValue n;
        try {
            n = Literals.parseNumber(body);
        } catch (RuntimeException e) {
            try {
                // Un entero decimal demasiado grande para un long pasa a decimal, como en Lua
                if (hex) return null;
                n = LuaValue.of(Double.parseDouble(body));
            } catch (RuntimeException e2) {
                return null;
            }
        }
        if (!neg) return n;
        return n instanceof LuaInteger ? LuaValue.of(-((LuaInteger) n).value) : LuaValue.of(-((LuaFloat) n).value);
    }

    /** Indica si el carácter es un espacio en blanco para Lua, incluidos tabuladores y saltos de línea. */
    private static boolean isLuaSpace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }

    // ---- Conversión a texto ----

    /** Texto de un valor tal como lo muestran print y tostring. */
    public static String tostring(LuaValue v) {
        if (v instanceof LuaNil) return "nil";
        if (v instanceof LuaBoolean) return v.isTruthy() ? "true" : "false";
        if (v instanceof LuaInteger) return Long.toString(((LuaInteger) v).value);
        if (v instanceof LuaFloat) return NumberFormat.format(((LuaFloat) v).value);
        if (v instanceof LuaString) return ((LuaString) v).value;
        // Tablas, funciones y demás se muestran con su tipo y un número que identifica al objeto
        return String.format("%s: 0x%08x", v.typeName(), System.identityHashCode(v));
    }

    // ---- Comparaciones ----

    /** Igualdad de Lua (a == b), incluido el metamétodo __eq. */
    public static boolean eq(LuaValue a, LuaValue b) {
        if (a == b) {
            // NaN ("no es un número", por ejemplo 0/0) nunca es igual a nada, ni a sí mismo
            return !(a instanceof LuaFloat && Double.isNaN(((LuaFloat) a).value));
        }
        if (isNumber(a) && isNumber(b)) return numEquals(a, b);
        if (a instanceof LuaString && b instanceof LuaString) {
            return ((LuaString) a).value.equals(((LuaString) b).value);
        }
        // Lua solo consulta __eq cuando compara dos tablas distintas
        if (a instanceof LuaTable && b instanceof LuaTable) {
            LuaValue h = binaryMetamethod("__eq", a, b);
            return h != LuaNil.NIL && firstResult(call(h, new LuaValue[] { a, b })).isTruthy();
        }
        // nil y los booleanos son instancias únicas; funciones, hilos y userdata se comparan por referencia
        return false;
    }

    /** Menor que (a < b), incluido el metamétodo __lt. */
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
            // Cada carácter es un byte, así que comparar los textos ordena igual que Lua
            return ((LuaString) a).value.compareTo(((LuaString) b).value) < 0;
        }
        LuaValue h = binaryMetamethod("__lt", a, b);
        if (h != LuaNil.NIL) return firstResult(call(h, new LuaValue[] { a, b })).isTruthy();
        throw compareError(a, b);
    }

    /** Menor o igual (a <= b), incluido el metamétodo __le. */
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
        // Desde Lua 5.4, si falta __le ya no se usa "not __lt" en su lugar
        LuaValue h = binaryMetamethod("__le", a, b);
        if (h != LuaNil.NIL) return firstResult(call(h, new LuaValue[] { a, b })).isTruthy();
        throw compareError(a, b);
    }

    /** Error de comparación entre tipos que no se pueden ordenar. */
    private static LuaError compareError(LuaValue a, LuaValue b) {
        String ta = a.typeName(), tb = b.typeName();
        if (ta.equals(tb)) return new LuaError("attempt to compare two " + ta + " values");
        return new LuaError("attempt to compare " + ta + " with " + tb);
    }

    /** Igualdad exacta entre dos números, sean enteros o decimales. */
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
        // El decimal debe ser entero y caber en un long; así no se pierde precisión al compararlos
        return d >= -TWO_POW_63 && d < TWO_POW_63 && d == Math.floor(d) && (long) d == i;
    }

    // Los cuatro métodos siguientes comparan un entero con un decimal sin convertir el entero
    // a decimal, porque los enteros muy grandes pierden sus últimas cifras al convertirse.
    // En su lugar se redondea el decimal a entero hacia el lado que no cambia el resultado.

    /** i < d sin perder precisión; NaN da false. */
    private static boolean intLtFloat(long i, double d) {
        if (Double.isNaN(d)) return false;
        if (d >= TWO_POW_63) return true;
        if (d < -TWO_POW_63) return false;
        return i < (long) Math.ceil(d);
    }

    /** i <= d sin perder precisión; NaN da false. */
    private static boolean intLeFloat(long i, double d) {
        if (Double.isNaN(d)) return false;
        if (d >= TWO_POW_63) return true;
        if (d < -TWO_POW_63) return false;
        return i <= (long) Math.floor(d);
    }

    /** d < i sin perder precisión; NaN da false. */
    private static boolean floatLtInt(double d, long i) {
        if (Double.isNaN(d)) return false;
        if (d >= TWO_POW_63) return false;
        if (d < -TWO_POW_63) return true;
        return (long) Math.floor(d) < i;
    }

    /** d <= i sin perder precisión; NaN da false. */
    private static boolean floatLeInt(double d, long i) {
        if (Double.isNaN(d)) return false;
        if (d >= TWO_POW_63) return false;
        if (d < -TWO_POW_63) return true;
        return (long) Math.ceil(d) <= i;
    }

    // ---- Concatenación y longitud ----

    /** Concatenación (a .. b), incluido el metamétodo __concat. */
    public static LuaValue concat(LuaValue a, LuaValue b) {
        String sa = concatText(a), sb = concatText(b);
        if (sa == null || sb == null) {
            LuaValue h = binaryMetamethod("__concat", a, b);
            if (h != LuaNil.NIL) return firstResult(call(h, new LuaValue[] { a, b }));
            LuaValue bad = (sa == null) ? a : b;
            throw new LuaError("attempt to concatenate a " + bad.typeName() + " value");
        }
        return LuaValue.of(sa + sb);
    }

    /** Texto de un operando de la concatenación, o null si no es string ni número. */
    private static String concatText(LuaValue v) {
        if (v instanceof LuaString) return ((LuaString) v).value;
        if (v instanceof LuaInteger) return Long.toString(((LuaInteger) v).value);
        if (v instanceof LuaFloat) return NumberFormat.format(((LuaFloat) v).value);
        return null;
    }

    /** Longitud (#a) de un string o de una tabla, incluido el metamétodo __len. */
    public static LuaValue len(LuaValue a) {
        if (a instanceof LuaString) return LuaValue.of((long) ((LuaString) a).value.length());
        if (a instanceof LuaTable) {
            LuaValue h = metamethod(a, "__len");
            if (h != LuaNil.NIL) return firstResult(call(h, new LuaValue[] { a }));
            return LuaValue.of(((LuaTable) a).length());
        }
        throw new LuaError("attempt to get length of a " + a.typeName() + " value");
    }

    // ---- Indexación y llamadas ----

    /** Lectura t[k], siguiendo la cadena de __index cuando la clave no existe. */
    public static LuaValue index(LuaValue t, LuaValue k) {
        // Se usa un bucle y no recursión para que una cadena larga no agote la memoria de llamadas
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
            // __index puede ser una función que calcula el valor, o una tabla donde seguir buscando
            if (h instanceof LuaFunction) return firstResult(call(h, new LuaValue[] { t, k }));
            t = h;
        }
        throw new LuaError("'__index' chain too long; possible loop");
    }

    /** Escritura t[k] = v, siguiendo la cadena de __newindex cuando la clave no existe. */
    public static void setIndex(LuaValue t, LuaValue k, LuaValue v) {
        for (int i = 0; i < META_CHAIN_LIMIT; i++) {
            LuaValue h;
            if (t instanceof LuaTable) {
                LuaTable tt = (LuaTable) t;
                // __newindex solo se consulta cuando la clave todavía no existe
                h = tt.rawget(k) != LuaNil.NIL ? LuaNil.NIL : metamethod(t, "__newindex");
                if (h == LuaNil.NIL) {
                    tt.rawset(k, v); // rawset ya rechaza las claves nil y NaN
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

    /** Llama a f con los argumentos dados; si f no es función, usa su metamétodo __call. */
    public static LuaValue[] call(LuaValue f, LuaValue[] args) {
        // __call puede ser a su vez una tabla con __call, por eso es un bucle
        for (int i = 0; i < META_CHAIN_LIMIT; i++) {
            if (f instanceof LuaFunction) {
                LuaValue[] r = ((LuaFunction) f).call(args);
                // El contrato no permite null, pero así un error en una función nativa no se propaga
                return r == null ? LuaValue.NONE : r;
            }
            LuaValue h = metamethod(f, "__call");
            if (h == LuaNil.NIL) throw callError(f);
            // El objeto que se llamó pasa como primer argumento de __call
            LuaValue[] withSelf = new LuaValue[args.length + 1];
            withSelf[0] = f;
            System.arraycopy(args, 0, withSelf, 1, args.length);
            args = withSelf;
            f = h;
        }
        throw new LuaError("'__call' chain too long; possible loop");
    }

    /** Error al indexar un valor que no es tabla y no tiene __index ni __newindex. */
    private static LuaError indexError(LuaValue t) {
        return new LuaError("attempt to index a " + t.typeName() + " value");
    }

    /** Error al llamar un valor que no es función y no tiene __call. */
    private static LuaError callError(LuaValue f) {
        return new LuaError("attempt to call a " + f.typeName() + " value");
    }
}
