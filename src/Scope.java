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

    // Añadir una variable a la tabla de símbolos
    public Cell declare(String name, LuaValue v) {
        Cell c = new Cell(v);
        this.symbolTable.put(name, c);
        return c;
     }

    // Buscar una variable en la tabla de símbolos, un scope a la vez
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