import org.antlr.v4.runtime.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Must stay Java 8 compatible: the INGInious grader compiles with JDK 1.8 (no `var`).
public class Main {
    public static void main(String[] args) throws Exception {
        // Read the file given as argument, or stdin if there is none.
        // ISO-8859-1 keeps one Java char per byte (Lua strings are byte strings).
        CharStream input = args.length > 0
                ? CharStreams.fromPath(Paths.get(args[0]), StandardCharsets.ISO_8859_1)
                : CharStreams.fromStream(System.in, StandardCharsets.ISO_8859_1);
        LuaLexer lexer = new LuaLexer(input);
        LuaParser parser = new LuaParser(new CommonTokenStream(lexer));
        LuaParser.Start_Context tree = parser.start_();   // start_ = chunk EOF, so trailing garbage is an error
        System.out.println("syntax errors: " + parser.getNumberOfSyntaxErrors());
        if (args.length > 1) System.out.println(tree.toStringTree(parser));  // optional debug dump
    }
}
