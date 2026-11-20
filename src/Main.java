import org.antlr.v4.runtime.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Main {
    public static void main(String[] args) throws Exception {
        CharStream input = CharStreams.fromPath(Paths.get(args[0]), StandardCharsets.ISO_8859_1);
        LuaLexer lexer = new LuaLexer(input);
        LuaParser parser = new LuaParser(new CommonTokenStream(lexer));
        var tree = parser.start_();               // start_ = chunk EOF, so trailing garbage is an error
        System.out.println("syntax errors: " + parser.getNumberOfSyntaxErrors());
        if (args.length > 1) System.out.println(tree.toStringTree(parser));  // optional debug dump
    }
}
