import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

// Las claves 1..n van en una lista ordenada (la "parte array") y las demás en un diccionario
// (la "parte hash"). Con __mode la tabla es débil: no impide que el recolector de basura (GC),
// que libera los objetos que ya nadie usa, borre sus claves o valores.
// Dos tablas solo son iguales si son el mismo objeto, por eso no se redefine equals.
/** Tabla de Lua: el único tipo de colección del lenguaje. Contrato §5.5. */
public class LuaTable extends LuaValue {

    // 2^63 como decimal: el primer valor que ya no cabe en un entero de Java (long).
    private static final double TWO_POW_63 = 9.223372036854775808e18;

    // Claves 1..n. Puede tener NIL en medio: así {10,20,30,nil,50} mide 5, como en Lua.
    private final ArrayList<LuaValue> array = new ArrayList<>();

    // Parte hash en orden de inserción, para que next pueda seguir desde cualquier clave.
    // Se guarda como Object porque en modo débil las entradas van envueltas en referencias débiles.
    private final ArrayList<Object> hashKeys = new ArrayList<>();
    private final ArrayList<Object> hashVals = new ArrayList<>();
    private final HashMap<Object, Integer> hashIndex = new HashMap<>();
    private int deleted = 0; // entradas borradas que siguen ocupando lugar en las listas (ver rawset)

    private LuaTable metatable;
    private boolean weakKeys;
    private boolean weakValues;

    @Override public String typeName() { return "table"; }

    // ---- API del contrato ----

    /** Valor guardado en la clave k, o NIL si no existe; no consulta metamétodos. */
    public LuaValue rawget(LuaValue k) {
        k = normalize(k);
        int i = arrayIndex(k);
        if (i >= 0) return array.get(i);
        if (k == LuaNil.NIL) return LuaNil.NIL;
        Integer pos = hashIndex.get(wrapKey(k));
        return pos == null ? LuaNil.NIL : liveValue(pos);
    }

    /** Guarda v en la clave k (guardar NIL borra la clave); no consulta metamétodos. */
    public void rawset(LuaValue k, LuaValue v) {
        k = normalize(k);
        if (k == LuaNil.NIL) throw new LuaError("index is nil");
        if (k instanceof LuaFloat && Double.isNaN(((LuaFloat) k).value)) throw new LuaError("index is NaN");

        int i = arrayIndex(k);
        if (i >= 0) {
            array.set(i, v);
            return;
        }

        Integer pos = hashIndex.get(wrapKey(k));
        if (pos != null) {
            // Borrar deja la entrada con NIL en vez de quitarla: así un next() que
            // está recorriendo la tabla todavía encuentra la clave borrada.
            if (v == LuaNil.NIL && hashVals.get(pos) != LuaNil.NIL) deleted++;
            if (v != LuaNil.NIL && hashVals.get(pos) == LuaNil.NIL) deleted--;
            hashVals.set(pos, wrapValue(v));
            return;
        }
        if (v == LuaNil.NIL) return;

        // En modo débil todo va a la parte hash, que es donde están las referencias débiles.
        if (!isWeak() && k instanceof LuaInteger && ((LuaInteger) k).value == array.size() + 1) {
            array.add(v);
            moveFromHashToArray();
        } else {
            addToHash(k, v);
        }
    }

    /** Agrega un valor posicional del constructor {a, b, c}, en orden; admite NIL para respetar los huecos. */
    public void appendArray(LuaValue v) {
        array.add(v);
    }

    /** Par {clave, valor} que sigue a k (NIL para empezar), o null cuando no quedan más. */
    public LuaValue[] next(LuaValue k) {
        k = normalize(k);
        int start;
        if (k == LuaNil.NIL) {
            start = 0;
        } else {
            int i = arrayIndex(k);
            if (i >= 0) {
                start = i + 1;
            } else {
                Integer pos = hashIndex.get(wrapKey(k));
                if (pos == null) throw new LuaError("invalid key to 'next'");
                start = array.size() + pos + 1;
            }
        }

        for (int i = start; i < array.size(); i++) {
            if (array.get(i) != LuaNil.NIL) return new LuaValue[] { LuaValue.of((long) i + 1), array.get(i) };
        }
        for (int pos = Math.max(0, start - array.size()); pos < hashKeys.size(); pos++) {
            LuaValue key = liveKey(pos);
            LuaValue val = liveValue(pos);
            if (key != null && val != LuaNil.NIL) return new LuaValue[] { key, val };
        }
        return null;
    }

    /** Resultado de #t sin __len: el "borde", una posición ocupada cuya siguiente está vacía. */
    public long length() {
        int n = array.size();
        // Si el último de la parte array es nil, hay un borde dentro de ella: se busca
        // partiendo el rango a la mitad, como hace Lua; así {nil,20,30} da 3 y no 0.
        if (n > 0 && array.get(n - 1) == LuaNil.NIL) {
            int lo = 0, hi = n;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (array.get(mid - 1) == LuaNil.NIL) hi = mid;
                else lo = mid;
            }
            return lo;
        }
        // La parte array está llena: la secuencia puede seguir en la parte hash.
        long j = n;
        while (rawget(LuaValue.of(j + 1)) != LuaNil.NIL) j++;
        return j;
    }

    /** Metatabla de la tabla, o null si no tiene. */
    public LuaTable getMetatable() {
        return metatable;
    }

    /** Asigna la metatabla y activa el modo débil si su campo __mode lo pide ("k", "v" o "kv"). */
    public void setMetatable(LuaTable mt) {
        metatable = mt;
        LuaValue mode = mt == null ? LuaNil.NIL : mt.rawget(LuaValue.of("__mode"));
        String m = mode instanceof LuaString ? ((LuaString) mode).value : "";
        boolean wk = m.indexOf('k') >= 0;
        boolean wv = m.indexOf('v') >= 0;
        if (wk != weakKeys || wv != weakValues) rebuild(wk, wv);
    }

    // ---- Claves y posiciones ----

    /** Convierte un decimal con valor entero en entero, porque en Lua t[2.0] y t[2] son la misma clave. */
    private static LuaValue normalize(LuaValue k) {
        if (k instanceof LuaFloat) {
            double d = ((LuaFloat) k).value;
            // El rango excluye 2^63 porque no cabe en un long.
            if (d == Math.floor(d) && d >= -TWO_POW_63 && d < TWO_POW_63) {
                return LuaValue.of((long) d);
            }
        }
        return k;
    }

    /** Posición en la parte array para las claves 1..n, o -1 si la clave no va ahí. */
    private int arrayIndex(LuaValue k) {
        if (!(k instanceof LuaInteger)) return -1;
        long v = ((LuaInteger) k).value;
        return v >= 1 && v <= array.size() ? (int) (v - 1) : -1;
    }

    /** Tras crecer la parte array, pasa a ella las claves siguientes que estaban en la parte hash. */
    private void moveFromHashToArray() {
        while (true) {
            Integer pos = hashIndex.get(LuaValue.of((long) array.size() + 1));
            if (pos == null || hashVals.get(pos) == LuaNil.NIL) return;
            array.add(liveValue(pos));
            hashVals.set(pos, LuaNil.NIL);
            deleted++;
        }
    }

    // ---- Parte hash ----

    /** Agrega una clave nueva al final de la parte hash. */
    private void addToHash(LuaValue k, LuaValue v) {
        // Solo se limpia al agregar una clave nueva: en Lua agregar claves durante
        // un recorrido no está permitido, pero borrarlas sí.
        if (deleted > 8 && deleted * 2 > hashKeys.size()) compact();
        Object key = wrapKey(k);
        hashIndex.put(key, hashKeys.size());
        hashKeys.add(key);
        hashVals.add(wrapValue(v));
    }

    /** Quita las entradas borradas y las que el GC ya liberó. */
    private void compact() {
        ArrayList<Object> keys = new ArrayList<>(hashKeys);
        ArrayList<Object> vals = new ArrayList<>(hashVals);
        hashKeys.clear();
        hashVals.clear();
        hashIndex.clear();
        deleted = 0;
        for (int i = 0; i < keys.size(); i++) {
            Object key = keys.get(i);
            Object val = vals.get(i);
            if (unwrap(key) == null || unwrap(val) == null || val == LuaNil.NIL) continue;
            hashIndex.put(key, hashKeys.size());
            hashKeys.add(key);
            hashVals.add(val);
        }
    }

    // ---- Modo débil ----

    /** Vuelve a insertar todas las entradas cuando __mode cambia, para envolverlas con el modo nuevo. */
    private void rebuild(boolean wk, boolean wv) {
        ArrayList<LuaValue> entries = new ArrayList<>();
        for (LuaValue[] e = next(LuaNil.NIL); e != null; e = next(e[0])) {
            entries.add(e[0]);
            entries.add(e[1]);
        }
        array.clear();
        hashKeys.clear();
        hashVals.clear();
        hashIndex.clear();
        deleted = 0;
        weakKeys = wk;
        weakValues = wv;
        for (int i = 0; i < entries.size(); i += 2) rawset(entries.get(i), entries.get(i + 1));
    }

    /** Indica si la tabla tiene claves o valores débiles. */
    private boolean isWeak() {
        return weakKeys || weakValues;
    }

    /** Indica si el GC puede liberar el valor; en Lua los números, strings y booleanos nunca se liberan. */
    private static boolean collectable(LuaValue v) {
        return !(v instanceof LuaNil || v instanceof LuaBoolean || v instanceof LuaInteger
                || v instanceof LuaFloat || v instanceof LuaString);
    }

    /** Envuelve la clave en una referencia débil si la tabla lo pide. */
    private Object wrapKey(LuaValue k) {
        return weakKeys && collectable(k) ? new WeakKey(k) : k;
    }

    /** Envuelve el valor en una referencia débil si la tabla lo pide. */
    private Object wrapValue(LuaValue v) {
        return weakValues && collectable(v) ? new WeakReference<>(v) : v;
    }

    /** Valor guardado, sin la envoltura débil; null si el GC ya lo liberó. */
    @SuppressWarnings("unchecked")
    private static LuaValue unwrap(Object o) {
        return o instanceof WeakReference ? ((WeakReference<LuaValue>) o).get() : (LuaValue) o;
    }

    /** Clave en la posición pos de la parte hash, o null si el GC ya la liberó. */
    private LuaValue liveKey(int pos) {
        return unwrap(hashKeys.get(pos));
    }

    /** Valor en la posición pos de la parte hash; NIL si la clave o el valor ya fueron liberados. */
    private LuaValue liveValue(int pos) {
        LuaValue key = unwrap(hashKeys.get(pos));
        LuaValue val = unwrap(hashVals.get(pos));
        return key == null || val == null ? LuaNil.NIL : val;
    }

    // Guarda el código de búsqueda al crearse, porque después el objeto puede desaparecer
    // y la clave tiene que seguir encontrándose en el diccionario para poder borrarla.
    /** Clave débil que se compara por referencia, igual que las tablas en Lua. */
    private static final class WeakKey extends WeakReference<LuaValue> {
        private final int hash;

        /** Crea la referencia débil y guarda el código de búsqueda del objeto. */
        WeakKey(LuaValue v) {
            super(v);
            hash = System.identityHashCode(v);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof WeakKey)) return false;
            LuaValue v = get();
            return v != null && v == ((WeakKey) o).get();
        }
    }
}
