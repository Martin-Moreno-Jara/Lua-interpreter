import java.io.PrintStream;
import java.lang.ref.WeakReference;

// Contrato §5.6 (dueño: C). Biblioteca estándar: solo lo que usan los casos de prueba (§1).
// Cada función nativa es una subclase anónima de LuaFunction (§5.2). Los errores
// de argumentos se lanzan sin posición: el evaluador (A) les antepone "chunk:línea: ".
public class Stdlib {
    private Stdlib() {}

    // _G con print, type, tostring, pcall, xpcall, error, setmetatable, rawset,
    // pairs, ipairs, next, collectgarbage, load, coroutine.{create,resume,yield},
    // table.{pack,unpack}, io.{write,stdin}, math.pi. _ENV apunta a esta tabla.
    public static LuaTable createGlobals() {
        final LuaTable g = new LuaTable();
        set(g, "_G", g);

        set(g, "print", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append('\t');
                    sb.append(Ops.tostring(args[i]));
                }
                sb.append('\n');                 // nunca println: en Windows sería "\r\n"
                out().print(sb);
                return LuaValue.NONE;
            }
        });

        set(g, "type", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                checkAny(args, 0, "type");
                return one(LuaValue.of(args[0].typeName()));
            }
        });

        set(g, "tostring", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                checkAny(args, 0, "tostring");
                return one(LuaValue.of(Ops.tostring(args[0])));
            }
        });

        set(g, "setmetatable", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaTable t = checkTable(args, 0, "setmetatable");
                LuaValue mt = LuaFunction.arg(args, 1);
                if (mt != LuaNil.NIL && !(mt instanceof LuaTable)) {
                    throw new LuaError("bad argument #2 to 'setmetatable' (nil or table expected)");
                }
                t.setMetatable(mt == LuaNil.NIL ? null : (LuaTable) mt);
                return one(t);
            }
        });

        set(g, "rawset", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaTable t = checkTable(args, 0, "rawset");
                checkAny(args, 1, "rawset");
                checkAny(args, 2, "rawset");
                t.rawset(args[1], args[2]);
                return one(t);
            }
        });

        final LuaFunction next = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaTable t = checkTable(args, 0, "next");
                LuaValue[] e = t.next(LuaFunction.arg(args, 1));
                return e == null ? one(LuaNil.NIL) : e;
            }
        };
        set(g, "next", next);

        set(g, "pairs", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaTable t = checkTable(args, 0, "pairs");
                return new LuaValue[] { next, t, LuaNil.NIL };
            }
        });

        // ipairs respeta __index (Lua 5.4): recorre t[1], t[2], ... hasta el primer nil.
        final LuaFunction ipairsIter = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                long i = toInteger(LuaFunction.arg(args, 1), 2, "ipairs") + 1;
                LuaValue v = Ops.index(LuaFunction.arg(args, 0), LuaValue.of(i));
                return v == LuaNil.NIL ? one(LuaNil.NIL) : new LuaValue[] { LuaValue.of(i), v };
            }
        };
        set(g, "ipairs", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                checkAny(args, 0, "ipairs");
                return new LuaValue[] { ipairsIter, args[0], LuaValue.of(0L) };
            }
        });

        // ---- errores ----

        // error(msg [, level]): si msg es string y level > 0, antepone "chunk:línea: ".
        // Solo se soporta el nivel 1 (la línea de la llamada a error), §1.
        set(g, "error", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue msg = LuaFunction.arg(args, 0);
                LuaValue lv = LuaFunction.arg(args, 1);
                long level = lv == LuaNil.NIL ? 1 : toInteger(lv, 2, "error");
                if (msg instanceof LuaString && level > 0) {
                    msg = LuaValue.of(Interpreter.currentChunk() + ":" + Interpreter.currentLine() + ": "
                            + ((LuaString) msg).value);
                }
                LuaError e = new LuaError(msg);
                e.positioned = true;             // ya tiene la posición que le corresponde
                throw e;
            }
        });

        set(g, "pcall", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                checkAny(args, 0, "pcall");
                try {
                    return prepend(LuaBoolean.TRUE, Ops.call(args[0], rest(args, 1)));
                } catch (LuaError e) {
                    return new LuaValue[] { LuaBoolean.FALSE, e.value };
                } catch (StackOverflowError e) {
                    return new LuaValue[] { LuaBoolean.FALSE, LuaValue.of("stack overflow") };
                }
            }
        });

        // xpcall(f, msgh, ...): si f falla, devuelve false y lo que devuelva msgh(error).
        set(g, "xpcall", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                checkAny(args, 1, "xpcall");
                LuaValue handler = args[1];
                LuaValue err;
                try {
                    return prepend(LuaBoolean.TRUE, Ops.call(args[0], rest(args, 2)));
                } catch (LuaError e) {
                    err = e.value;
                } catch (StackOverflowError e) {
                    err = LuaValue.of("stack overflow");
                }
                LuaValue[] h = Ops.call(handler, new LuaValue[] { err });
                return new LuaValue[] { LuaBoolean.FALSE, h.length > 0 ? h[0] : LuaNil.NIL };
            }
        });

        // ---- load ----

        // load(chunk [, chunkname [, mode [, env]]]): solo chunks string. Comparte
        // las globales g salvo que se pase env.
        set(g, "load", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue chunk = LuaFunction.arg(args, 0);
                if (!(chunk instanceof LuaString)) {
                    throw new LuaError("bad argument #1 to 'load' (string expected, got "
                            + (args.length > 0 ? chunk.typeName() : "no value") + ")");
                }
                String code = ((LuaString) chunk).value;
                LuaValue nameArg = LuaFunction.arg(args, 1);
                String name = nameArg instanceof LuaString
                        ? ((LuaString) nameArg).value : chunkId(code);
                LuaValue envArg = LuaFunction.arg(args, 3);
                LuaTable env = envArg instanceof LuaTable ? (LuaTable) envArg : g;
                try {
                    return one(withChunk(Interpreter.load(code, name, env)));
                } catch (LuaError e) {
                    return new LuaValue[] { LuaNil.NIL, e.value };
                }
            }
        });

        // ---- collectgarbage ----

        set(g, "collectgarbage", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue opt = LuaFunction.arg(args, 0);
                String o = opt instanceof LuaString ? ((LuaString) opt).value : "collect";
                if (!o.equals("collect")) {
                    throw new LuaError("bad argument #1 to 'collectgarbage' (invalid option '" + o + "')");
                }
                fullGc();
                return one(LuaValue.of(0L));
            }
        });

        // ---- librerías ----

        LuaTable table = new LuaTable();
        set(g, "table", table);

        // table.pack(...): {...} con n = número de argumentos (incluidos los nil).
        set(table, "pack", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaTable t = new LuaTable();
                for (LuaValue v : args) t.appendArray(v);
                t.rawset(LuaValue.of("n"), LuaValue.of((long) args.length));
                return one(t);
            }
        });

        // table.unpack(t [, i [, j]]): t[i], ..., t[j]; por defecto i = 1 y j = #t.
        set(table, "unpack", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue t = LuaFunction.arg(args, 0);
                LuaValue iv = LuaFunction.arg(args, 1);
                LuaValue jv = LuaFunction.arg(args, 2);
                long i = iv == LuaNil.NIL ? 1 : toInteger(iv, 2, "unpack");
                long j = jv == LuaNil.NIL ? toInteger(Ops.len(t), 3, "unpack") : toInteger(jv, 3, "unpack");
                if (i > j) return LuaValue.NONE;
                if (j - i >= 1000000) throw new LuaError("too many results to unpack");
                LuaValue[] r = new LuaValue[(int) (j - i + 1)];
                for (int k = 0; k < r.length; k++) r[k] = Ops.index(t, LuaValue.of(i + k));
                return r;
            }
        });

        LuaTable io = new LuaTable();
        set(g, "io", io);
        set(io, "stdin", new LuaUserdata("file (stdin)"));

        // io.write(...): strings y números, sin separadores ni salto de línea.
        set(io, "write", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                PrintStream out = out();
                for (int i = 0; i < args.length; i++) {
                    LuaValue v = args[i];
                    if (v instanceof LuaString || v instanceof LuaInteger) {
                        out.print(Ops.tostring(v));
                    } else if (v instanceof LuaFloat) {
                        // io.write usa "%.14g" sin el ".0" que agrega tostring.
                        String s = Ops.tostring(v);
                        out.print(s.matches("-?[0-9]+\\.0") ? s.substring(0, s.length() - 2) : s);
                    } else {
                        throw new LuaError("bad argument #" + (i + 1) + " to 'write' (string expected, got "
                                + v.typeName() + ")");
                    }
                }
                return LuaValue.NONE;
            }
        });

        LuaTable math = new LuaTable();
        set(g, "math", math);
        set(math, "pi", LuaValue.of(Math.PI));

        LuaTable coroutine = new LuaTable();
        set(g, "coroutine", coroutine);

        set(coroutine, "create", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue f = LuaFunction.arg(args, 0);
                if (!(f instanceof LuaFunction)) {
                    throw new LuaError("bad argument #1 to 'create' (function expected)");
                }
                return one(new LuaCoroutine((LuaFunction) f));
            }
        });

        set(coroutine, "resume", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue co = LuaFunction.arg(args, 0);
                if (!(co instanceof LuaCoroutine)) {
                    throw new LuaError("bad argument #1 to 'resume' (coroutine expected)");
                }
                return ((LuaCoroutine) co).resume(rest(args, 1));
            }
        });

        set(coroutine, "yield", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                return LuaCoroutine.yield(args);
            }
        });

        return g;
    }

    // ---- utilidades ----

    private static PrintStream out() { return System.out; }

    private static void set(LuaTable t, String name, LuaValue v) {
        t.rawset(LuaValue.of(name), v);
    }

    private static LuaValue[] one(LuaValue v) { return new LuaValue[] { v }; }

    private static LuaValue[] prepend(LuaValue first, LuaValue[] rest) {
        LuaValue[] r = new LuaValue[rest.length + 1];
        r[0] = first;
        System.arraycopy(rest, 0, r, 1, rest.length);
        return r;
    }

    private static LuaValue[] rest(LuaValue[] args, int from) {
        if (args.length <= from) return LuaValue.NONE;
        LuaValue[] r = new LuaValue[args.length - from];
        System.arraycopy(args, from, r, 0, r.length);
        return r;
    }

    private static void checkAny(LuaValue[] args, int i, String fname) {
        if (i >= args.length) {
            throw new LuaError("bad argument #" + (i + 1) + " to '" + fname + "' (value expected)");
        }
    }

    private static LuaTable checkTable(LuaValue[] args, int i, String fname) {
        LuaValue v = LuaFunction.arg(args, i);
        if (v instanceof LuaTable) return (LuaTable) v;
        String got = i < args.length ? v.typeName() : "no value";
        throw new LuaError("bad argument #" + (i + 1) + " to '" + fname + "' (table expected, got " + got + ")");
    }

    // Enteros, o floats con valor entero exacto (2.0 -> 2).
    private static long toInteger(LuaValue v, int argn, String fname) {
        if (v instanceof LuaInteger) return ((LuaInteger) v).value;
        if (v instanceof LuaFloat) {
            double d = ((LuaFloat) v).value;
            if (d == Math.floor(d) && d >= -9.223372036854775808e18 && d < 9.223372036854775808e18) return (long) d;
            throw new LuaError("bad argument #" + argn + " to '" + fname + "' (number has no integer representation)");
        }
        throw new LuaError("bad argument #" + argn + " to '" + fname + "' (number expected, got " + v.typeName() + ")");
    }

    // Nombre que Lua le da a un chunk cargado desde un string: [string "primera línea..."].
    private static String chunkId(String code) {
        int nl = code.indexOf('\n');
        String first = nl >= 0 ? code.substring(0, nl) : code;
        boolean cut = nl >= 0;
        if (first.length() > 45) {
            first = first.substring(0, 45);
            cut = true;
        }
        return "[string \"" + first + (cut ? "..." : "") + "\"]";
    }

    // Al volver de un chunk cargado con load, restaura el nombre del chunk de quien
    // lo llamó; si no, los error() posteriores dirían [string "..."].
    private static LuaFunction withChunk(final LuaFunction f) {
        return new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                String saved = Interpreter.currentChunk();
                try {
                    return f.call(args);
                } finally {
                    Interpreter.CHUNK.set(saved);
                }
            }
        };
    }

    // System.gc() no garantiza nada, así que se repite hasta que una referencia
    // débil de control se borre (señal de que hubo una recolección completa).
    // Las tablas débiles de B ven sus entradas muertas como borradas (§6).
    private static void fullGc() {
        for (int round = 0; round < 2; round++) {
            WeakReference<Object> probe = new WeakReference<Object>(new Object());
            for (int i = 0; i < 20 && probe.get() != null; i++) {
                System.gc();
                try {
                    Thread.sleep(i < 5 ? 1 : 10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
