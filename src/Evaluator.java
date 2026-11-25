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
            for (LuaParser.StatContext st : ctx.stat()) exec(st);
            if (ctx.retstat() != null) exec(ctx.retstat());
        } finally {
            scope = saved;
        }
        return null;
    }

    // Ejecuta una sentencia: fija la línea y, si sale un error de runtime sin
    // posición (los de Ops), le antepone "chunk:línea: " una sola vez (§5.3).
    private void exec(ParserRuleContext st) {
        Interpreter.LINE.set(st.getStart().getLine());
        try {
            visit(st);
        } catch (LuaError e) {
            throw positioned(e);
        }
    }

    // Solo los mensajes string llevan posición; error(tabla) no. Si el error
    // viene de una función llamada, su propio bloque ya lo posicionó.
    private LuaError positioned(LuaError e) {
        if (e.positioned || !(e.value instanceof LuaString)) return e;
        String msg = ((LuaString) e.value).value;
        LuaError p = new LuaError(LuaValue.of(chunkName + ":" + Interpreter.currentLine() + ": " + msg));
        p.positioned = true;
        return p;
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
    // Operadores
    // ------------------------------------------------------------------
    // Toda la semántica (tipos, coerciones, metamétodos, errores) vive en Ops (B).
    // Aquí solo se evalúan los operandos, de izquierda a derecha, y se delega.

    // Aritméticos y de bits: el texto del operador es justo lo que espera Ops.arith.
    // El '~' binario es XOR (el unario está en visitUnaryExp).
    @Override public Object visitPowExp(LuaParser.PowExpContext ctx)       { return arith(ctx.op, ctx.exp(0), ctx.exp(1)); }
    @Override public Object visitMulExp(LuaParser.MulExpContext ctx)       { return arith(ctx.op, ctx.exp(0), ctx.exp(1)); }
    @Override public Object visitAddExp(LuaParser.AddExpContext ctx)       { return arith(ctx.op, ctx.exp(0), ctx.exp(1)); }
    @Override public Object visitShiftExp(LuaParser.ShiftExpContext ctx)   { return arith(ctx.op, ctx.exp(0), ctx.exp(1)); }
    @Override public Object visitBitAndExp(LuaParser.BitAndExpContext ctx) { return arith(ctx.op, ctx.exp(0), ctx.exp(1)); }
    @Override public Object visitBitXorExp(LuaParser.BitXorExpContext ctx) { return arith(ctx.op, ctx.exp(0), ctx.exp(1)); }
    @Override public Object visitBitOrExp(LuaParser.BitOrExpContext ctx)   { return arith(ctx.op, ctx.exp(0), ctx.exp(1)); }

    private LuaValue arith(Token op, LuaParser.ExpContext left, LuaParser.ExpContext right) {
        LuaValue a = evalOne(left);
        LuaValue b = evalOne(right);
        return Ops.arith(op.getText(), a, b);
    }

    @Override
    public Object visitConcatExp(LuaParser.ConcatExpContext ctx) {
        LuaValue a = evalOne(ctx.exp(0));
        LuaValue b = evalOne(ctx.exp(1));
        return Ops.concat(a, b);
    }

    // Ops solo tiene eq, lt y le. Los demás se traducen (contrato §5.4):
    //   a > b  ->  lt(b, a)      a >= b  ->  le(b, a)      a ~= b  ->  !eq(a, b)
    @Override
    public Object visitCompareExp(LuaParser.CompareExpContext ctx) {
        LuaValue a = evalOne(ctx.exp(0));
        LuaValue b = evalOne(ctx.exp(1));
        boolean r;
        switch (ctx.op.getText()) {
            case "==": r = Ops.eq(a, b);  break;
            case "~=": r = !Ops.eq(a, b); break;
            case "<":  r = Ops.lt(a, b);  break;
            case "<=": r = Ops.le(a, b);  break;
            case ">":  r = Ops.lt(b, a);  break;
            case ">=": r = Ops.le(b, a);  break;
            default: throw new IllegalStateException("operador de comparación: " + ctx.op.getText());
        }
        return LuaValue.of(r);
    }

    // Unarios: not lo resuelve A (es solo veracidad); el resto va a Ops.
    @Override
    public Object visitUnaryExp(LuaParser.UnaryExpContext ctx) {
        LuaValue a = evalOne(ctx.exp());
        switch (ctx.op.getText()) {
            case "not": return LuaValue.of(!a.isTruthy());
            case "-":   return Ops.unm(a);
            case "~":   return Ops.bnot(a);
            case "#":   return Ops.len(a);
            default: throw new IllegalStateException("operador unario: " + ctx.op.getText());
        }
    }

    // and / or: cortocircuito. El lado derecho solo se evalúa si hace falta, y
    // el resultado es uno de los operandos, no un booleano:
    //   nil and f()  -> nil (f no se llama)      1 or f()  -> 1
    //   false or "x" -> "x"                      1 and 2   -> 2
    @Override
    public Object visitAndExp(LuaParser.AndExpContext ctx) {
        LuaValue a = evalOne(ctx.exp(0));
        if (!a.isTruthy()) return a;
        return evalOne(ctx.exp(1));
    }

    @Override
    public Object visitOrExp(LuaParser.OrExpContext ctx) {
        LuaValue a = evalOne(ctx.exp(0));
        if (a.isTruthy()) return a;
        return evalOne(ctx.exp(1));
    }

    // ------------------------------------------------------------------
    // Indexación: a.b y a[b]
    // ------------------------------------------------------------------
    // Hacen falta para llamar io.write(...) u obj:m(...). __index lo resuelve Ops.

    @Override
    public Object visitFieldExp(LuaParser.FieldExpContext ctx) {
        LuaValue t = evalOne(ctx.prefixexp());
        return Ops.index(t, LuaValue.of(ctx.NAME().getText()));
    }

    @Override
    public Object visitIndexExp(LuaParser.IndexExpContext ctx) {
        LuaValue t = evalOne(ctx.prefixexp());
        LuaValue k = evalOne(ctx.exp());
        return Ops.index(t, k);
    }

    // ------------------------------------------------------------------
    // Llamadas
    // ------------------------------------------------------------------
    // Hay dos lugares en la gramática con la misma forma:
    //   prefixexp:    CallExp / MethodCallExp   (llamada usada como expresión)
    //   functioncall: PlainCall / MethodCall    (llamada usada como sentencia)
    // Los cuatro terminan en call() o methodCall(). Todos devuelven LuaValue[],
    // y evalOne/evalExpList deciden si se trunca o se expande.

    @Override
    public Object visitCallExp(LuaParser.CallExpContext ctx) {
        return call(ctx, ctx.prefixexp(), ctx.args());
    }

    @Override
    public Object visitMethodCallExp(LuaParser.MethodCallExpContext ctx) {
        return methodCall(ctx, ctx.prefixexp(), ctx.NAME().getText(), ctx.args());
    }

    @Override
    public Object visitPlainCall(LuaParser.PlainCallContext ctx) {
        return call(ctx, ctx.prefixexp(), ctx.args());
    }

    @Override
    public Object visitMethodCall(LuaParser.MethodCallContext ctx) {
        return methodCall(ctx, ctx.prefixexp(), ctx.NAME().getText(), ctx.args());
    }

    // Sentencia que es solo una llamada: f(x). Los resultados se descartan.
    @Override
    public Object visitCallStat(LuaParser.CallStatContext ctx) {
        visit(ctx.functioncall());
        return null;
    }

    // f(args): se evalúa la función, luego los argumentos, y se llama.
    private LuaValue[] call(ParserRuleContext ctx, LuaParser.PrefixexpContext fn, LuaParser.ArgsContext args) {
        LuaValue f = evalOne(fn);
        LuaValue[] a = evalArgs(args);
        return invoke(ctx, f, a);
    }

    // obj:m(args) equivale a obj.m(obj, args), pero obj se evalúa UNA sola vez.
    private LuaValue[] methodCall(ParserRuleContext ctx, LuaParser.PrefixexpContext objExp,
                                  String name, LuaParser.ArgsContext args) {
        LuaValue obj = evalOne(objExp);
        LuaValue f = Ops.index(obj, LuaValue.of(name));
        LuaValue[] rest = evalArgs(args);
        LuaValue[] a = new LuaValue[rest.length + 1];
        a[0] = obj;                                        // self (contrato §5.2)
        System.arraycopy(rest, 0, a, 1, rest.length);
        return invoke(ctx, f, a);
    }

    // Fija la línea de la llamada justo antes de llamar (los argumentos pudieron
    // contener otras llamadas que la cambiaron) y la restaura al volver, porque
    // el cuerpo de la función llamada la movió a sus propias líneas.
    private LuaValue[] invoke(ParserRuleContext ctx, LuaValue f, LuaValue[] args) {
        int line = ctx.getStart().getLine();
        Interpreter.LINE.set(line);
        LuaValue[] r = Ops.call(f, args);
        Interpreter.LINE.set(line);
        return r;
    }

    // args: '(' explist? ')' | tableconstructor | string
    // f"s" y f{...} son llamadas con un único argumento.
    private LuaValue[] evalArgs(LuaParser.ArgsContext ctx) {
        if (ctx.tableconstructor() != null) return new LuaValue[] { evalOne(ctx.tableconstructor()) };
        if (ctx.string() != null) return new LuaValue[] { LuaValue.of(Literals.parseString(ctx.string().getText())) };
        return evalExpList(ctx.explist());                 // null si es (), y da NONE
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
