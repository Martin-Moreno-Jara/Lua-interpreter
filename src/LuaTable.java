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

    // Imita luaH_getn: si el último elemento de la "parte array" (lo que puso el
    // constructor) es nil, búsqueda binaria de un borde; si no, sigue contando.
    public long length() {
        long n = nextIndex - 1;
        if (n > 0 && rawget(LuaValue.of(n)) == LuaNil.NIL) {
            long lo = 0, hi = n;
            while (hi - lo > 1) {
                long m = (lo + hi) >>> 1;
                if (rawget(LuaValue.of(m)) == LuaNil.NIL) hi = m; else lo = m;
            }
            return lo;
        }
        while (map.containsKey(LuaValue.of(n + 1))) n++;
        return n;
    }

    public LuaTable getMetatable() { return meta; }

    public void setMetatable(LuaTable mt) { meta = mt; }
}
