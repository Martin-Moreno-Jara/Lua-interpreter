import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Interno de A. Visitor sobre el árbol generado por LuaParser.
// El tipo de retorno del visitor y la organización interna los decide A.
public class Evaluator extends LuaParserBaseVisitor<Object> {
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
    // restaura el anterior, así los locales del bloque dejan de verse. También
    // cierra sus variables <close> (ver inNewBlock).
    @Override
    public Object visitBlock(final LuaParser.BlockContext ctx) {
        return inNewBlock(new Supplier<Object>() {
            @Override public Object get() {
                runStatements(ctx);
                return null;
            }
        });
    }

    // Ejecuta las sentencias del bloque en el scope ACTUAL, sin abrir uno nuevo.
    // Lo usan visitBlock y repeat/until (cuya condición ve los locales del cuerpo).
    //
    // Va por índice para soportar goto (§6): un 'goto x' lanza ControlSignal.Goto,
    // que sube por los bloques anidados (if, while, do...) hasta el primero que
    // tenga '::x::' entre sus sentencias. Ese bloque sigue desde la sentencia
    // siguiente a la etiqueta, hacia adelante o hacia atrás.
    private void runStatements(LuaParser.BlockContext ctx) {
        List<LuaParser.StatContext> stats = ctx.stat();
        int i = 0;
        while (i < stats.size()) {
            try {
                exec(stats.get(i));
                i++;
            } catch (ControlSignal.Goto g) {
                int target = findLabel(stats, g.label);
                if (target < 0) throw g;               // no es de este bloque: sigue subiendo
                i = target + 1;
            }
        }
        if (ctx.retstat() != null) exec(ctx.retstat());
    }

    // Índice de '::name::' entre las sentencias de un bloque, o -1.
    private static int findLabel(List<LuaParser.StatContext> stats, String name) {
        for (int i = 0; i < stats.size(); i++) {
            LuaParser.StatContext st = stats.get(i);
            if (st instanceof LuaParser.LabelStatContext
                    && ((LuaParser.LabelStatContext) st).label().NAME().getText().equals(name)) {
                return i;
            }
        }
        return -1;
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
    // local y asignación
    // ------------------------------------------------------------------

    // 'local' attnamelist ('=' explist)?
    // Primero se evalúa el lado derecho y DESPUÉS se declaran los nombres, así
    // en 'local x = x' el x de la derecha es el de afuera.
    // Si faltan valores, los nombres sobrantes quedan en nil; si sobran, se descartan.
    @Override
    public Object visitLocalStat(LuaParser.LocalStatContext ctx) {
        LuaValue[] vs = evalExpList(ctx.explist());       // sin '=' da NONE: todos nil
        LuaParser.AttnamelistContext names = ctx.attnamelist();
        for (int i = 0; i < names.NAME().size(); i++) {
            String name = names.NAME(i).getText();
            LuaValue v = i < vs.length ? vs[i] : LuaNil.NIL;
            // <const> solo se lee en los casos, así que es un local normal.
            scope.declare(name, v);
            LuaParser.AttribContext attr = names.attrib(i);
            if (attr.NAME() != null && attr.NAME().getText().equals("close")) registerClose(name, v);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // <close>
    // ------------------------------------------------------------------
    // 'local x <close> = v' registra v en el bloque actual. Cuando el bloque
    // termina, por la razón que sea (fin normal, return, break, goto o error),
    // se llama __close(v, err) en orden inverso al de declaración. err es nil,
    // salvo si se sale por un error: entonces es el valor del error (§6).
    // __close no está en Ops (§5.4): esta lógica es de A.

    private List<LuaValue> toClose;   // valores <close> del bloque en curso; null si no hay

    private void registerClose(String name, LuaValue v) {
        if (v == LuaNil.NIL || v == LuaBoolean.FALSE) return;          // se permiten y se ignoran
        if (closeHandler(v) == LuaNil.NIL) {
            throw new LuaError("variable '" + name + "' got a non-closable value");
        }
        if (toClose == null) toClose = new ArrayList<LuaValue>();
        toClose.add(v);
    }

    private static LuaValue closeHandler(LuaValue v) {
        if (v instanceof LuaTable) {
            LuaTable mt = ((LuaTable) v).getMetatable();
            if (mt != null) return mt.rawget(LuaValue.of("__close"));
        }
        return LuaNil.NIL;
    }

    private void closePending(LuaValue err) {
        List<LuaValue> list = toClose;
        toClose = null;
        if (list == null) return;
        for (int i = list.size() - 1; i >= 0; i--) {
            LuaValue v = list.get(i);
            Ops.call(closeHandler(v), new LuaValue[] { v, err });
        }
    }

    // Corre body en un bloque nuevo: scope nuevo y lista de <close> propia.
    // Al salir cierra lo pendiente y restaura ambos, pase lo que pase.
    private <T> T inNewBlock(Supplier<T> body) {
        Scope savedScope = scope;
        List<LuaValue> savedClose = toClose;
        scope = new Scope(savedScope);
        toClose = null;
        try {
            T result;
            try {
                result = body.get();
            } catch (LuaError e) {
                closePending(e.value);                 // salida por error: __close(v, err)
                throw e;
            } catch (ControlSignal s) {
                closePending(LuaNil.NIL);              // return, break o goto: salida normal
                throw s;
            }
            closePending(LuaNil.NIL);
            return result;
        } finally {
            scope = savedScope;
            toClose = savedClose;
        }
    }

    // varlist '=' explist, en tres fases (así 'i, t[i] = i+1, 20' usa el i viejo):
    //   1. evaluar los destinos: la tabla y la clave de cada t[k] / t.k
    //   2. evaluar todos los valores de la derecha
    //   3. asignar
    @Override
    public Object visitAssignStat(LuaParser.AssignStatContext ctx) {
        List<LuaParser.VarContext> vars = ctx.varlist().var();
        Target[] targets = new Target[vars.size()];
        for (int i = 0; i < targets.length; i++) targets[i] = target(vars.get(i));   // fase 1

        LuaValue[] vs = evalExpList(ctx.explist());                                 // fase 2

        for (int i = targets.length - 1; i >= 0; i--) {                             // fase 3
            assign(targets[i], i < vs.length ? vs[i] : LuaNil.NIL);
        }
        return null;
    }

    // Un destino de asignación ya evaluado: o un nombre, o una tabla con su clave.
    private static final class Target {
        final String name;      // NameVar (table y key en null)
        final LuaValue table;   // IndexVar / FieldVar
        final LuaValue key;
        Target(String name, LuaValue table, LuaValue key) {
            this.name = name; this.table = table; this.key = key;
        }
    }

    private Target target(LuaParser.VarContext v) {
        if (v instanceof LuaParser.NameVarContext) {
            return new Target(((LuaParser.NameVarContext) v).NAME().getText(), null, null);
        }
        if (v instanceof LuaParser.FieldVarContext) {
            LuaParser.FieldVarContext f = (LuaParser.FieldVarContext) v;
            return new Target(null, evalOne(f.prefixexp()), LuaValue.of(f.NAME().getText()));
        }
        LuaParser.IndexVarContext ix = (LuaParser.IndexVarContext) v;
        LuaValue t = evalOne(ix.prefixexp());
        LuaValue k = evalOne(ix.exp());
        return new Target(null, t, k);
    }

    // Igual que al leer: si el nombre es local, se cambia su celda; si no, es global.
    private void assign(Target t, LuaValue v) {
        if (t.name == null) {
            Ops.setIndex(t.table, t.key, v);               // __newindex lo resuelve Ops
            return;
        }
        Scope.Cell c = scope.lookup(t.name);
        if (c != null) c.value = v;
        else Ops.setIndex(env, LuaValue.of(t.name), v);
    }

    // ------------------------------------------------------------------
    // Funciones
    // ------------------------------------------------------------------
    // Definir una función NO ejecuta su cuerpo: solo crea un LuaClosure con el
    // cuerpo y el scope actual (de ahí salen sus upvalues).

    // function (...) ... end usada como expresión: local f = function(x) ... end
    @Override
    public Object visitFunctionDefExp(LuaParser.FunctionDefExpContext ctx) {
        return new LuaClosure(ctx.functiondef().funcbody(), scope, env, chunkName, false);
    }

    // local function f(...) ... end
    // Se declara f ANTES de crear la closure, para que el cuerpo vea a f y la
    // recursión funcione. (Con 'local f = function...' el cuerpo no vería a f.)
    @Override
    public Object visitLocalFunctionStat(LuaParser.LocalFunctionStatContext ctx) {
        Scope.Cell c = scope.declare(ctx.NAME().getText(), LuaNil.NIL);
        c.value = new LuaClosure(ctx.funcbody(), scope, env, chunkName, false);
        return null;
    }

    // function a.b.c:m(...) ... end
    //   'function f'      es  f = function...         (local o global, como una asignación)
    //   'function a.b.c'  es  a.b.c = function...
    //   'function a.b:m'  es  a.b.m = function(self, ...)
    @Override
    public Object visitFunctionStat(LuaParser.FunctionStatContext ctx) {
        LuaParser.FuncnameContext fn = ctx.funcname();
        List<TerminalNode> names = fn.NAME();
        boolean isMethod = fn.COL() != null;
        LuaClosure f = new LuaClosure(ctx.funcbody(), scope, env, chunkName, isMethod);

        if (names.size() == 1) {
            assign(new Target(names.get(0).getText(), null, null), f);
            return null;
        }
        LuaValue t = readName(names.get(0).getText());
        for (int i = 1; i < names.size() - 1; i++) {
            t = Ops.index(t, LuaValue.of(names.get(i).getText()));
        }
        Ops.setIndex(t, LuaValue.of(names.get(names.size() - 1).getText()), f);
        return null;
    }

    // '...': todos los argumentos extra. Devuelve un arreglo (varios valores),
    // y evalOne/evalExpList deciden si se trunca o se expande.
    @Override
    public Object visitVarargExp(LuaParser.VarargExpContext ctx) {
        return varargs.clone();
    }

    // ------------------------------------------------------------------
    // Constructores de tabla
    // ------------------------------------------------------------------
    // Aparecen como expresión ({...}) y como argumento (f{...}); ambos llegan
    // a visitTableconstructor.

    @Override
    public Object visitTableConstructorExp(LuaParser.TableConstructorExpContext ctx) {
        return visit(ctx.tableconstructor());
    }

    // '{' fieldlist? '}', con tres tipos de campo:
    //   [k] = v   KeyedField       name = v   NamedField       v   PositionalField
    // Los campos se evalúan en orden, de izquierda a derecha.
    // Los posicionales van con appendArray (contrato §5.5), INCLUIDOS los nil:
    // así #{10,20,30,nil,50} da 5. Si el ÚLTIMO campo es posicional y da varios
    // valores (una llamada o '...'), se agregan todos: {f()} guarda todo f().
    //
    // Como Lua real, los posicionales se acumulan y se guardan en tandas de 50
    // (LFIELDS_PER_FLUSH), DESPUÉS de los campos con clave de su tanda. Por eso
    // en {10, 20, [2] = "x"} gana el 20 aunque [2] esté escrito después.
    private static final int FIELDS_PER_FLUSH = 50;

    @Override
    public Object visitTableconstructor(LuaParser.TableconstructorContext ctx) {
        LuaTable t = new LuaTable();
        if (ctx.fieldlist() == null) return t;
        List<LuaParser.FieldContext> fields = ctx.fieldlist().field();
        List<LuaValue> pending = new ArrayList<LuaValue>();
        for (int i = 0; i < fields.size(); i++) {
            LuaParser.FieldContext f = fields.get(i);
            if (f instanceof LuaParser.KeyedFieldContext) {
                LuaParser.KeyedFieldContext kf = (LuaParser.KeyedFieldContext) f;
                LuaValue k = evalOne(kf.exp(0));
                LuaValue v = evalOne(kf.exp(1));
                t.rawset(k, v);
            } else if (f instanceof LuaParser.NamedFieldContext) {
                LuaParser.NamedFieldContext nf = (LuaParser.NamedFieldContext) f;
                t.rawset(LuaValue.of(nf.NAME().getText()), evalOne(nf.exp()));
            } else {
                LuaParser.ExpContext e = ((LuaParser.PositionalFieldContext) f).exp();
                if (i == fields.size() - 1) {
                    for (LuaValue v : evalMulti(e)) pending.add(v);    // último: se expande
                } else {
                    pending.add(evalOne(e));                           // los demás: un valor
                    if (pending.size() == FIELDS_PER_FLUSH) flush(t, pending);
                }
            }
        }
        flush(t, pending);
        return t;
    }

    private static void flush(LuaTable t, List<LuaValue> pending) {
        for (LuaValue v : pending) t.appendArray(v);
        pending.clear();
    }

    // ------------------------------------------------------------------
    // Control de flujo
    // ------------------------------------------------------------------
    // break lanza ControlSignal.Break y lo atrapa el bucle más cercano. Los
    // finally de visitBlock restauran el scope en el camino.

    // if e1 then b1 elseif e2 then b2 ... else bN end
    // ctx.exp(i) es la condición de ctx.block(i); si hay un bloque más que
    // condiciones, ese último es el else.
    @Override
    public Object visitIfStat(LuaParser.IfStatContext ctx) {
        List<LuaParser.ExpContext> conds = ctx.exp();
        for (int i = 0; i < conds.size(); i++) {
            if (evalOne(conds.get(i)).isTruthy()) {
                visit(ctx.block(i));
                return null;
            }
        }
        if (ctx.block().size() > conds.size()) visit(ctx.block(conds.size()));   // else
        return null;
    }

    @Override
    public Object visitWhileStat(LuaParser.WhileStatContext ctx) {
        try {
            while (evalOne(ctx.exp()).isTruthy()) visit(ctx.block());
        } catch (ControlSignal.Break b) {
            // sale del bucle
        }
        return null;
    }

    // repeat b until e: la condición se evalúa DENTRO del scope del cuerpo,
    // así puede usar sus locales:  repeat local x = f() until x > 10
    @Override
    public Object visitRepeatStat(final LuaParser.RepeatStatContext ctx) {
        Supplier<Boolean> iteration = new Supplier<Boolean>() {
            @Override public Boolean get() {
                runStatements(ctx.block());
                return evalOne(ctx.exp()).isTruthy();   // mismo scope que el cuerpo
            }
        };
        try {
            while (!inNewBlock(iteration)) {
                // otra vuelta
            }
        } catch (ControlSignal.Break b) {
            // sale del bucle
        }
        return null;
    }

    @Override
    public Object visitBreakStat(LuaParser.BreakStatContext ctx) {
        throw ControlSignal.Break.INSTANCE;
    }

    // goto x: lo resuelve runStatements del bloque que tenga '::x::'.
    // Limitación conocida: en un salto hacia atrás, los locales declarados después
    // de la etiqueta siguen visibles hasta que se vuelven a declarar (en Lua
    // real salen de scope). Ningún caso depende de eso.
    @Override
    public Object visitGotoStat(LuaParser.GotoStatContext ctx) {
        throw new ControlSignal.Goto(ctx.NAME().getText());
    }

    // ::x:: no hace nada al ejecutarse; solo marca una posición del bloque.
    @Override
    public Object visitLabelStat(LuaParser.LabelStatContext ctx) {
        return null;
    }

    // for v = e1, e2, e3 do b end  (Lua 5.4)
    // - Si el inicio y el paso son enteros, el bucle es entero: el límite float
    //   se redondea hacia adentro y se precalcula cuántas vueltas hay, así no
    //   hay desbordamiento cerca de math.maxinteger.
    // - Si no, todo se convierte a float.
    // - Los strings numéricos se convierten, y el bucle queda float. Paso 0 es error.
    // Cada vuelta declara v en un scope NUEVO: las closures del cuerpo capturan
    // un v distinto en cada iteración.
    @Override
    public Object visitNumericForStat(LuaParser.NumericForStatContext ctx) {
        String var = ctx.NAME().getText();
        LuaValue init = forNumber(evalOne(ctx.exp(0)), "initial value");
        LuaValue limit = forNumber(evalOne(ctx.exp(1)), "limit");
        LuaValue step = ctx.exp().size() > 2 ? forNumber(evalOne(ctx.exp(2)), "step") : LuaValue.of(1L);

        if (init instanceof LuaInteger && step instanceof LuaInteger) {
            long i = ((LuaInteger) init).value;
            long st = ((LuaInteger) step).value;
            if (st == 0) throw new LuaError("'for' step is zero");
            Long lim = forLimit(limit, st);
            if (lim == null || (st > 0 ? i > lim : i < lim)) return null;   // no entra
            // Número de vueltas restantes, como entero sin signo (igual que lvm.c).
            long count = st > 0 ? Long.divideUnsigned(lim - i, st)
                                : Long.divideUnsigned(i - lim, -(st + 1) + 1L);
            while (true) {
                if (!loopBody(var, LuaValue.of(i), ctx.block())) break;
                if (count-- == 0) break;
                i += st;
            }
        } else {
            double i = toDouble(init), lim = toDouble(limit), st = toDouble(step);
            if (st == 0) throw new LuaError("'for' step is zero");
            for (; st > 0 ? i <= lim : i >= lim; i += st) {
                if (!loopBody(var, LuaValue.of(i), ctx.block())) break;
            }
        }
        return null;
    }

    // Valor de control del for. Un string se convierte con la coerción de Ops
    // ("1" + 0) y, como en Lua, queda float: for k = "1", 2 da 1.0 y 2.0.
    private static LuaValue forNumber(LuaValue v, String what) {
        if (v instanceof LuaInteger || v instanceof LuaFloat) return v;
        if (v instanceof LuaString) {
            try {
                return LuaValue.of(toDouble(Ops.arith("+", v, LuaValue.of(0L))));
            } catch (LuaError e) {
                // no es un número: cae al error de abajo
            }
        }
        throw new LuaError("bad 'for' " + what + " (number expected, got " + v.typeName() + ")");
    }

    private static double toDouble(LuaValue n) {
        return n instanceof LuaInteger ? ((LuaInteger) n).value : ((LuaFloat) n).value;
    }

    // Límite de un for entero. null si el bucle no debe ejecutarse.
    private static Long forLimit(LuaValue limit, long step) {
        if (limit instanceof LuaInteger) return ((LuaInteger) limit).value;
        double d = ((LuaFloat) limit).value;
        double f = step > 0 ? Math.floor(d) : Math.ceil(d);
        if (Double.isNaN(f)) return null;
        if (f >= 9.223372036854775807E18) return step > 0 ? Long.MAX_VALUE : null;
        if (f < -9.223372036854775808E18) return step < 0 ? Long.MIN_VALUE : null;
        return (long) f;
    }

    // Una vuelta de un for: scope nuevo con la variable del bucle.
    // Devuelve false si el cuerpo hizo break.
    private boolean loopBody(String var, LuaValue value, LuaParser.BlockContext body) {
        Scope saved = scope;
        scope = new Scope(saved);
        try {
            scope.declare(var, value);
            visit(body);
            return true;
        } catch (ControlSignal.Break b) {
            return false;
        } finally {
            scope = saved;
        }
    }

    // for k, v in explist do b end
    // explist da (f, s, control). Cada vuelta llama f(s, control); si el primer
    // resultado es nil termina, si no pasa a ser el nuevo control.
    // Así funcionan pairs/ipairs: devuelven (next, t, nil) o similar.
    @Override
    public Object visitGenericForStat(LuaParser.GenericForStatContext ctx) {
        List<TerminalNode> names = ctx.namelist().NAME();
        LuaValue[] init = evalExpList(ctx.explist());
        LuaValue f = init.length > 0 ? init[0] : LuaNil.NIL;
        LuaValue s = init.length > 1 ? init[1] : LuaNil.NIL;
        LuaValue control = init.length > 2 ? init[2] : LuaNil.NIL;
        int line = ctx.getStart().getLine();

        while (true) {
            Interpreter.LINE.set(line);                  // el cuerpo movió la línea
            LuaValue[] rs = Ops.call(f, new LuaValue[] { s, control });
            LuaValue first = rs.length > 0 ? rs[0] : LuaNil.NIL;
            if (first == LuaNil.NIL) break;
            control = first;

            Scope saved = scope;
            scope = new Scope(saved);
            try {
                for (int i = 0; i < names.size(); i++) {
                    scope.declare(names.get(i).getText(), i < rs.length ? rs[i] : LuaNil.NIL);
                }
                visit(ctx.block());
            } catch (ControlSignal.Break b) {
                break;
            } finally {
                scope = saved;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Sentencias y expresiones aún no implementadas
    // ------------------------------------------------------------------
    // Sin estos métodos, el visitor por defecto recorre los hijos en vez de
    // fallar: por ejemplo, ejecutaría el cuerpo de un while una sola vez.

    @Override
    public Object visitDoStat(LuaParser.DoStatContext ctx) {
        visit(ctx.block());
        return null;
    }


    private Object todo(ParserRuleContext ctx) {
        throw new UnsupportedOperationException("TODO A: "
                + ctx.getClass().getSimpleName().replaceFirst("Context$", ""));
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
        return readName(ctx.NAME().getText());
    }

    private LuaValue readName(String name) {
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
