import java.util.concurrent.SynchronousQueue;

// Dueño: C. Tipo thread de Lua (§6): cada corrutina corre en su propio hilo de
// plataforma (daemon), y el que llama y la corrutina se pasan el control con dos
// SynchronousQueue, así en cada momento solo uno de los dos está corriendo.
// Igualdad por referencia (no se sobrescribe equals/hashCode).
public class LuaCoroutine extends LuaValue {
    private static final long STACK_SIZE = 64L * 1024 * 1024;

    // Corrutina que está corriendo en este hilo; null en el hilo principal.
    private static final ThreadLocal<LuaCoroutine> CURRENT = new ThreadLocal<LuaCoroutine>();

    private enum Status { SUSPENDED, RUNNING, NORMAL, DEAD }

    // Lo que la corrutina le devuelve a resume.
    private static final class Transfer {
        final boolean finished;       // true: terminó (con return o con error)
        final boolean error;
        final LuaValue[] values;      // valores de yield/return, o {valor del error}

        Transfer(boolean finished, boolean error, LuaValue[] values) {
            this.finished = finished;
            this.error = error;
            this.values = values;
        }
    }

    private final LuaFunction body;
    private final String chunkName;   // chunk que la creó, para los prefijos de error()
    private final SynchronousQueue<LuaValue[]> toCoroutine = new SynchronousQueue<LuaValue[]>();
    private final SynchronousQueue<Transfer> toCaller = new SynchronousQueue<Transfer>();
    private volatile Status status = Status.SUSPENDED;
    private Thread thread;

    public LuaCoroutine(LuaFunction body) {
        this.body = body;
        this.chunkName = Interpreter.currentChunk();
    }

    @Override public String typeName() { return "thread"; }

    // coroutine.resume: {true, valores...} o {false, error}. Nunca lanza.
    public LuaValue[] resume(LuaValue[] args) {
        if (status == Status.DEAD) return failure(LuaValue.of("cannot resume dead coroutine"));
        if (status != Status.SUSPENDED) return failure(LuaValue.of("cannot resume non-suspended coroutine"));

        LuaCoroutine caller = CURRENT.get();
        if (caller != null) caller.status = Status.NORMAL;
        status = Status.RUNNING;
        if (thread == null) start();

        put(toCoroutine, args);
        Transfer t = take(toCaller);

        if (caller != null) caller.status = Status.RUNNING;
        status = t.finished ? Status.DEAD : Status.SUSPENDED;
        if (t.error) return failure(t.values[0]);

        LuaValue[] r = new LuaValue[t.values.length + 1];
        r[0] = LuaBoolean.TRUE;
        System.arraycopy(t.values, 0, r, 1, t.values.length);
        return r;
    }

    // coroutine.yield: suspende la corrutina en curso y devuelve los argumentos
    // del siguiente resume.
    public static LuaValue[] yield(LuaValue[] values) {
        LuaCoroutine co = CURRENT.get();
        if (co == null) throw new LuaError("attempt to yield from outside a coroutine");
        put(co.toCaller, new Transfer(false, false, values));
        return take(co.toCoroutine);
    }

    private void start() {
        thread = new Thread(null, new Runnable() {
            @Override public void run() { runBody(); }
        }, "lua-coroutine", STACK_SIZE);
        thread.setDaemon(true);   // una corrutina suspendida no impide que el programa termine
        thread.start();
    }

    private void runBody() {
        CURRENT.set(this);
        Interpreter.CHUNK.set(chunkName);
        LuaValue[] args = take(toCoroutine);
        Transfer result;
        try {
            result = new Transfer(true, false, Ops.call(body, args));
        } catch (LuaError e) {
            result = new Transfer(true, true, new LuaValue[] { e.value });
        } catch (StackOverflowError e) {
            result = new Transfer(true, true, new LuaValue[] { LuaValue.of("stack overflow") });
        } catch (RuntimeException e) {
            result = new Transfer(true, true, new LuaValue[] { LuaValue.of("internal error: " + e) });
        }
        put(toCaller, result);
    }

    private static LuaValue[] failure(LuaValue err) {
        return new LuaValue[] { LuaBoolean.FALSE, err };
    }

    // put/take sin InterruptedException comprobada: nadie interrumpe estos hilos.
    private static <T> void put(SynchronousQueue<T> q, T v) {
        try {
            q.put(v);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LuaError("coroutine interrupted");
        }
    }

    private static <T> T take(SynchronousQueue<T> q) {
        try {
            return q.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LuaError("coroutine interrupted");
        }
    }
}
