public abstract class ControlSignal extends RuntimeException {
    protected ControlSignal() { super(null, null, false, false); }

    public static final class Break extends ControlSignal {
        public static final Break INSTANCE = new Break();   
    }

    public static final class Return extends ControlSignal {
        public final LuaValue[] values;
        public Return(LuaValue[] values) { this.values = values; }
    }

    public static final class Goto extends ControlSignal {
        public final String label;
        public Goto(String label) { this.label = label; }
    }
}