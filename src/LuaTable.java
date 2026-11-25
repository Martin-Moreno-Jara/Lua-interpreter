import java.util.*;

// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de B.
// HashMap con orden de inserción; sin parte array real ni modo débil.
public class LuaTable extends LuaValue {
    private final LinkedHashMap<LuaValue, LuaValue> map = new LinkedHashMap<LuaValue, LuaValue>();
    private long nextIndex = 1;
    private LuaTable meta;

    @Override public String typeName() { return "table"; }

    private static LuaValue norm(LuaValue k) {
        if (k instanceof LuaFloat) {
            double d = ((LuaFloat) k).value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) return LuaValue.of((long) d);
        }
        return k;
    }

    public LuaValue rawget(LuaValue k) {
        LuaValue v = map.get(norm(k));
        return v == null ? LuaNil.NIL : v;
    }

    public void rawset(LuaValue k, LuaValue v) {
        if (k == LuaNil.NIL) throw new LuaError("index is nil");
        k = norm(k);
        if (v == LuaNil.NIL) map.remove(k); else map.put(k, v);
    }

    public void appendArray(LuaValue v) {
        rawset(LuaValue.of(nextIndex++), v);
    }

    public LuaValue[] next(LuaValue k) {
        Iterator<Map.Entry<LuaValue, LuaValue>> it = map.entrySet().iterator();
        if (k != LuaNil.NIL) {
            k = norm(k);
            while (it.hasNext()) if (it.next().getKey().equals(k)) break;
        }
        if (!it.hasNext()) return null;
        Map.Entry<LuaValue, LuaValue> e = it.next();
        return new LuaValue[] { e.getKey(), e.getValue() };
    }

    public long length() {
        long n = 0;
        while (map.containsKey(LuaValue.of(n + 1))) n++;
        return n;
    }

    public LuaTable getMetatable() { return meta; }

    public void setMetatable(LuaTable mt) { meta = mt; }
}
