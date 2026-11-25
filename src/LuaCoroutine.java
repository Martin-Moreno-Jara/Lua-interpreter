import java.util.concurrent.SynchronousQueue;

// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de C.
// Un hilo daemon por corrutina; resume y yield se pasan el control con dos
// SynchronousQueue, así nunca corren dos hilos a la vez.
public class LuaCoroutine extends LuaValue {
    static final ThreadLocal<LuaCoroutine> CURRENT = new ThreadLocal<LuaCoroutine>();

    private final LuaFunction body;
    private Thread thread;
    private volatile boolean dead;
    private final SynchronousQueue<Object> toCo = new SynchronousQueue<Object>();
    private final SynchronousQueue<Object> fromCo = new SynchronousQueue<Object>();

    public LuaCoroutine(LuaFunction body) { this.body = body; }

    @Override public String typeName() { return "thread"; }

    // {true, ...} si cedió o terminó; {false, error} si falló o ya estaba muerta.
    public LuaValue[] resume(LuaValue[] args) {
        if (dead) return new LuaValue[] { LuaBoolean.FALSE, LuaValue.of("cannot resume dead coroutine") };
        if (thread == null) {
            final String chunk = Interpreter.currentChunk();
            thread = new Thread(new Runnable() {
                @Override public void run() {
                    CURRENT.set(LuaCoroutine.this);
                    Interpreter.CHUNK.set(chunk);
                    Object result;
                    try {
                        result = body.call((LuaValue[]) take(toCo));
                    } catch (RuntimeException e) {
                        result = e;
                    }
                    dead = true;
                    put(fromCo, result);
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
        put(toCo, args);
        Object r = take(fromCo);
        if (r instanceof LuaError) return new LuaValue[] { LuaBoolean.FALSE, ((LuaError) r).value };
        if (r instanceof RuntimeException) throw (RuntimeException) r;   // p. ej. un TODO
        LuaValue[] vs = (LuaValue[]) r;
        LuaValue[] out = new LuaValue[vs.length + 1];
        out[0] = LuaBoolean.TRUE;
        System.arraycopy(vs, 0, out, 1, vs.length);
        return out;
    }

    public static LuaValue[] yield(LuaValue[] args) {
        LuaCoroutine co = CURRENT.get();
        if (co == null) throw new LuaError("attempt to yield from outside a coroutine");
        put(co.fromCo, args);
        return (LuaValue[]) take(co.toCo);
    }

    private static void put(SynchronousQueue<Object> q, Object o) {
        try { q.put(o); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Object take(SynchronousQueue<Object> q) {
        try { return q.take(); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }
}
