import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Interno de A. Visitor sobre el árbol generado por LuaParser.
// El tipo de retorno del visitor y la organización interna los decide A.
public class Evaluator extends LuaParserBaseVisitor<Object> {
    // TODO A: sentencias, expresiones, scopes, closures, control de flujo,
    // multirresultados, constructores de tabla, <const>/<close>, goto.
    private final LuaTable env;
    private final String chunkName;
    private Scope scope;                    // scope actual; cambia al entrar/salir de bloques
    private final LuaValue[] varargs;       // argumentos extra (...) de la función en curso

    public Evaluator(LuaTable env, String chunkName, Scope scope, LuaValue[] varargs) {
        this.env = env;
        this.chunkName = chunkName;
        this.scope = scope;
        this.varargs = varargs;
    }

    // ------------------------------------------------------------------
    // Bloques y return
    // ------------------------------------------------------------------

    // block: stat* retstat?
    // Cada bloque abre un scope hijo; al salir (normal o por excepción) se
    // restaura el anterior, así los locales del bloque dejan de verse.
    @Override
    public Object visitBlock(LuaParser.BlockContext ctx) {
        Scope saved = scope;
        scope = new Scope(saved);
        try {
            for (LuaParser.StatContext st : ctx.stat()) {
                Interpreter.LINE.set(st.getStart().getLine());   // para mensajes de error
                visit(st);
            }
            if (ctx.retstat() != null) {
                Interpreter.LINE.set(ctx.retstat().getStart().getLine());
                visit(ctx.retstat());
            }
        } finally {
            scope = saved;
        }
        return null;
    }

    // retstat: 'return' explist? ';'?
    // Evalúa los valores y sale con una señal; la atrapa LuaClosure.call o
    // el chunk en Interpreter.load.
    @Override
    public Object visitRetstat(LuaParser.RetstatContext ctx) {
        throw new ControlSignal.Return(evalExpList(ctx.explist()));
    }

    // ------------------------------------------------------------------
    // Expresiones simples
    // ------------------------------------------------------------------

    @Override public Object visitNilExp(LuaParser.NilExpContext ctx)     { return LuaNil.NIL; }
    @Override public Object visitTrueExp(LuaParser.TrueExpContext ctx)   { return LuaBoolean.TRUE; }
    @Override public Object visitFalseExp(LuaParser.FalseExpContext ctx) { return LuaBoolean.FALSE; }

    // B decodifica el texto del token (INT, HEX, FLOAT, HEX_FLOAT).
    @Override
    public Object visitNumberExp(LuaParser.NumberExpContext ctx) {
        return Literals.parseNumber(ctx.getText());
    }

    // B decodifica el token completo, con comillas o corchetes y escapes.
    @Override
    public Object visitStringExp(LuaParser.StringExpContext ctx) {
        return LuaValue.of(Literals.parseString(ctx.getText()));
    }

    // Un nombre suelto: primero local (Scope), si no, global (_ENV).
    @Override
    public Object visitNameExp(LuaParser.NameExpContext ctx) {
        String name = ctx.NAME().getText();
        Scope.Cell c = scope.lookup(name);
        if (c != null) return c.value;                    // local o upvalue
        if (name.equals("_ENV")) return env;              // _ENV no es una global: es la tabla misma
        return Ops.index(env, LuaValue.of(name));         // global (nil si no existe)
    }

    // '(' exp ')': los paréntesis truncan a un valor, así (f()) da solo el primero.
    @Override
    public Object visitParenExp(LuaParser.ParenExpContext ctx) {
        return evalOne(ctx.exp());
    }

    // ------------------------------------------------------------------
    // Helpers de evaluación
    // ------------------------------------------------------------------
    // Convención: visitar una expresión devuelve un LuaValue (un valor) o un
    // LuaValue[] (llamadas y '...', que pueden dar 0 o más valores).

    // Evalúa e y se queda con UN valor: el primero, o nil si no hay ninguno.
    private LuaValue evalOne(ParserRuleContext e) {
        Object r = visit(e);
        if (r instanceof LuaValue[]) {
            LuaValue[] vs = (LuaValue[]) r;
            return vs.length > 0 ? vs[0] : LuaNil.NIL;
        }
        return checkValue(r, e);
    }

    // Evalúa e y devuelve TODOS sus valores.
    private LuaValue[] evalMulti(ParserRuleContext e) {
        Object r = visit(e);
        if (r instanceof LuaValue[]) return (LuaValue[]) r;
        return new LuaValue[] { checkValue(r, e) };
    }

    // Evalúa una lista de expresiones: solo la ÚLTIMA se expande a todos sus
    // valores; las demás se truncan a uno. Ej.: f(), g() -> {f()[0], g()...}
    private LuaValue[] evalExpList(LuaParser.ExplistContext list) {
        if (list == null) return LuaValue.NONE;
        List<LuaParser.ExpContext> exps = list.exp();
        int n = exps.size();
        LuaValue[] first = new LuaValue[n - 1];
        for (int i = 0; i < n - 1; i++) first[i] = evalOne(exps.get(i));   // izquierda a derecha
        LuaValue[] last = evalMulti(exps.get(n - 1));
        LuaValue[] out = new LuaValue[n - 1 + last.length];
        System.arraycopy(first, 0, out, 0, n - 1);
        System.arraycopy(last, 0, out, n - 1, last.length);
        return out;
    }

    // Si un visit devolvió null es porque ese nodo aún no está implementado.
    private LuaValue checkValue(Object r, ParserRuleContext e) {
        if (r == null) {
            throw new UnsupportedOperationException("TODO A: "
                    + e.getClass().getSimpleName().replaceFirst("Context$", ""));
        }
        return (LuaValue) r;
    }
}
