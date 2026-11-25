import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Must stay Java 8 compatible: the INGInious grader compiles with JDK 1.8 (no `var`).
public class Main {
    // Counts errors reported by the lexer (e.g. "token recognition error").
    // ANTLR still prints them to stderr through the default console listener.
    static class ErrorCounter extends BaseErrorListener {
        int count = 0;
        @Override
        public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col, String msg, RecognitionException e) {
            count++;
        }
    }

    public static void main(String[] args) throws Exception {
        // Read the file given as argument, or stdin if there is none.
        // ISO-8859-1 keeps one Java char per byte (Lua strings are byte strings).
        CharStream input = args.length > 0
                ? CharStreams.fromPath(Paths.get(args[0]), StandardCharsets.ISO_8859_1)
                : CharStreams.fromStream(System.in, StandardCharsets.ISO_8859_1);
        LuaLexer lexer = new LuaLexer(input);
        ErrorCounter lexErrors = new ErrorCounter();
        lexer.addErrorListener(lexErrors);
        LuaParser parser = new LuaParser(new CommonTokenStream(lexer));
        LuaParser.Start_Context tree = parser.start_();   // start_ = chunk EOF, so trailing garbage is an error
        System.out.println("syntax errors: " + parser.getNumberOfSyntaxErrors());
        if (args.length > 1) printTree(tree, "");  // optional debug dump
    }

    // Prints one node per line, indented. Labeled alternatives show their label
    // (e.g. BitOrExp) instead of the rule name, which is what the visitor sees.
    static void printTree(ParseTree t, String indent) {
        if (t instanceof TerminalNode) {
            System.out.println(indent + "'" + t.getText() + "'");
            return;
        }
        String name = t.getClass().getSimpleName().replaceFirst("Context$", "");
        System.out.println(indent + name);
        for (int i = 0; i < t.getChildCount(); i++) printTree(t.getChild(i), indent + "  ");
    }
}
