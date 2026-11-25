// Contrato §5.1 (dueño: B). Instancia única: LuaNil.NIL.
public final class LuaNil extends LuaValue {
    public static final LuaNil NIL = new LuaNil();

    private LuaNil() {}

    @Override public String typeName() { return "nil"; }

    @Override public boolean isTruthy() { return false; }

    @Override public String toString() { return "nil"; }
}
