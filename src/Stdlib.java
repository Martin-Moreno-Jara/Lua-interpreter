// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de C.
// Solo print, type, tostring, setmetatable, error, pcall, io.write y math.pi.
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

        LuaTable io = new LuaTable();
        g.rawset(LuaValue.of("io"), io);
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
