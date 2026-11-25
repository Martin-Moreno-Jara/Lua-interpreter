// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de C.
// Lo que usan los casos, en versión simple: sin __tostring, __pairs ni modo débil.
public class Stdlib {
    private Stdlib() {}

    private static void fn(LuaTable t, String name, LuaFunction f) { t.rawset(LuaValue.of(name), f); }

    public static LuaTable createGlobals() {
        LuaTable g = new LuaTable();
        g.rawset(LuaValue.of("_G"), g);

        fn(g, "print", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append('\t');
                    sb.append(Ops.tostring(args[i]));
                }
                System.out.println(sb);
                return LuaValue.NONE;
            }
        });
        fn(g, "type", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                return new LuaValue[] { LuaValue.of(arg(args, 0).typeName()) };
            }
        });
        fn(g, "tostring", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                return new LuaValue[] { LuaValue.of(Ops.tostring(arg(args, 0))) };
            }
        });
        fn(g, "setmetatable", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaTable t = (LuaTable) arg(args, 0);
                LuaValue mt = arg(args, 1);
                t.setMetatable(mt == LuaNil.NIL ? null : (LuaTable) mt);
                return new LuaValue[] { t };
            }
        });
        fn(g, "error", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue v = arg(args, 0);
                if (v instanceof LuaString) {
                    LuaError e = new LuaError(LuaValue.of(Interpreter.currentChunk() + ":"
                            + Interpreter.currentLine() + ": " + ((LuaString) v).value));
                    e.positioned = true;
                    throw e;
                }
                throw new LuaError(v);
            }
        });
        fn(g, "pcall", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue[] rest = new LuaValue[Math.max(0, args.length - 1)];
                if (args.length > 1) System.arraycopy(args, 1, rest, 0, rest.length);
                try {
                    LuaValue[] r = Ops.call(arg(args, 0), rest);
                    LuaValue[] out = new LuaValue[r.length + 1];
                    out[0] = LuaBoolean.TRUE;
                    System.arraycopy(r, 0, out, 1, r.length);
                    return out;
                } catch (LuaError e) {
                    return new LuaValue[] { LuaBoolean.FALSE, e.value };
                }
            }
        });

        final LuaTable globals = g;
        fn(g, "rawset", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                ((LuaTable) arg(args, 0)).rawset(arg(args, 1), arg(args, 2));
                return new LuaValue[] { arg(args, 0) };
            }
        });
        fn(g, "xpcall", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue[] rest = new LuaValue[Math.max(0, args.length - 2)];
                if (args.length > 2) System.arraycopy(args, 2, rest, 0, rest.length);
                try {
                    LuaValue[] r = Ops.call(arg(args, 0), rest);
                    LuaValue[] out = new LuaValue[r.length + 1];
                    out[0] = LuaBoolean.TRUE;
                    System.arraycopy(r, 0, out, 1, r.length);
                    return out;
                } catch (LuaError e) {
                    LuaValue[] h = Ops.call(arg(args, 1), new LuaValue[] { e.value });
                    LuaValue[] out = new LuaValue[h.length + 1];
                    out[0] = LuaBoolean.FALSE;
                    System.arraycopy(h, 0, out, 1, h.length);
                    return out;
                }
            }
        });
        final LuaFunction next = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue[] kv = ((LuaTable) arg(args, 0)).next(arg(args, 1));
                return kv == null ? new LuaValue[] { LuaNil.NIL } : kv;
            }
        };
        fn(g, "next", next);
        fn(g, "pairs", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                return new LuaValue[] { next, arg(args, 0), LuaNil.NIL };
            }
        });
        final LuaFunction ipairsIter = new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                long i = ((LuaInteger) arg(args, 1)).value + 1;
                LuaValue v = Ops.index(arg(args, 0), LuaValue.of(i));
                return v == LuaNil.NIL ? LuaValue.NONE : new LuaValue[] { LuaValue.of(i), v };
            }
        };
        fn(g, "ipairs", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                return new LuaValue[] { ipairsIter, arg(args, 0), LuaValue.of(0L) };
            }
        });
        fn(g, "load", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                String code = ((LuaString) arg(args, 0)).value;
                String first = code.split("\n", 2)[0];
                return new LuaValue[] { Interpreter.load(code, "[string \"" + first + "\"]", globals) };
            }
        });
        fn(g, "collectgarbage", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                for (int i = 0; i < 3; i++) System.gc();
                return new LuaValue[] { LuaValue.of(0L) };
            }
        });

        LuaTable table = new LuaTable();
        g.rawset(LuaValue.of("table"), table);
        fn(table, "pack", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaTable t = new LuaTable();
                for (LuaValue a : args) t.appendArray(a);
                t.rawset(LuaValue.of("n"), LuaValue.of((long) args.length));
                return new LuaValue[] { t };
            }
        });
        fn(table, "unpack", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue t = arg(args, 0);
                long i = arg(args, 1) == LuaNil.NIL ? 1 : ((LuaInteger) arg(args, 1)).value;
                long j = arg(args, 2) == LuaNil.NIL ? ((LuaInteger) Ops.len(t)).value : ((LuaInteger) arg(args, 2)).value;
                if (i > j) return LuaValue.NONE;
                LuaValue[] out = new LuaValue[(int) (j - i + 1)];
                for (long k = i; k <= j; k++) out[(int) (k - i)] = Ops.index(t, LuaValue.of(k));
                return out;
            }
        });

        LuaTable co = new LuaTable();
        g.rawset(LuaValue.of("coroutine"), co);
        fn(co, "create", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                return new LuaValue[] { new LuaCoroutine((LuaFunction) arg(args, 0)) };
            }
        });
        fn(co, "resume", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                LuaValue[] rest = new LuaValue[Math.max(0, args.length - 1)];
                if (args.length > 1) System.arraycopy(args, 1, rest, 0, rest.length);
                return ((LuaCoroutine) arg(args, 0)).resume(rest);
            }
        });
        fn(co, "yield", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                return LuaCoroutine.yield(args);
            }
        });

        LuaTable io = new LuaTable();
        g.rawset(LuaValue.of("io"), io);
        io.rawset(LuaValue.of("stdin"), new LuaUserdata());
        fn(io, "write", new LuaFunction() {
            @Override public LuaValue[] call(LuaValue[] args) {
                for (LuaValue a : args) {
                    String s = Ops.tostring(a);
                    if (a instanceof LuaFloat && s.endsWith(".0")) s = s.substring(0, s.length() - 2);  // %.14g sin ".0"
                    System.out.print(s);
                }
                return LuaValue.NONE;
            }
        });

        LuaTable math = new LuaTable();
        g.rawset(LuaValue.of("math"), math);
        math.rawset(LuaValue.of("pi"), LuaValue.of(Math.PI));
        return g;
    }
}
