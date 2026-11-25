// Interno de C. Tipo thread: un hilo de plataforma (daemon) por corrutina,
// con traspaso síncrono. Igualdad por referencia.
public class LuaCoroutine extends LuaValue {
    @Override public String typeName() { return "thread"; }

    // TODO C: create/resume/yield y "cannot resume dead coroutine".
}
