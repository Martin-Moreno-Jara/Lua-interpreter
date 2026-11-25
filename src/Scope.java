import java.util.HashMap;

public class Scope {
    private final Scope parent;
    private final HashMap<String, Cell> symbolTable = new HashMap<String, Cell>();

    public Scope(Scope parent) { this.parent = parent; }

    public static class Cell { 
        public LuaValue value; 
        public Cell(LuaValue value){
            this.value = value;
        }

    }

    // 'local x = v': crea una variable NUEVA en este scope
    public Cell declare(String name, LuaValue v) {
        Cell c = new Cell(v);
        this.symbolTable.put(name, c);
        return c;
     }

    // busca x en este scope y luego hacia arriba; null si no existe
    public Cell lookup(String name) { 
        Scope curScope = this;
        Cell search;
        while (curScope !=null){
            search = curScope.symbolTable.get(name);
            if(search !=null){return search;}
            curScope = curScope.parent;
        }
        return null;

     }
}