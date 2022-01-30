package tyger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tyger.TygerParser.ProgContext;
import tyger.codegen.JvmClassVisitor;

public class Interpreter {
    
    private static final Logger logger = LoggerFactory.getLogger(Interpreter.class);
    public static void main(String[] args) {
        if (args.length != 2) {
            logger.error("Invalid number of arguments.");
            usage();
            return;
        }

        if (!Set.of("run", "tree", "type-check", "com", "compile").contains(args[0])) {
            logger.error("Invalid <action>: {}", args[0]);
            usage();
            return;
        }

        try {
            new Interpreter().run(args[0], args[1]);
        } catch (IOException e) {
            logger.error("Interpreter error", e);
            return;
        }
    }

    public void run(String action, String filepath) throws IOException {
        try {
            logger.info("Source file: {}", filepath);
            
            final String source = Files.readString(Paths.get(filepath));
            final var stream = CharStreams.fromString(source);
            final var lexer = new TygerLexer(stream);
            final var tokens = new CommonTokenStream(lexer);
            final var parser = new TygerParser(tokens);

            final ProgContext prog = parser.prog();

            switch (action) {
                case "run":
                    prog.accept(new TypeCheckVisitor(filepath, source));
                    final Object result = prog.accept(new InterpreterVisitor());
                    logger.info("Output:\n{}", result);
                    break;
                case "tree":
                    final StringBuilder xml = prog.accept(new PrintTreeVisitor());
                    logger.info("\n{}", xml.toString());
                    break;
                case "type-check":
                    TypeCheckVisitor.Type type = prog.accept(new TypeCheckVisitor(filepath, source));
                    logger.info("Type: {}", type);
                    break;
                case "com":
                case "compile":
                    prog.accept(new TypeCheckVisitor(filepath, source));
                    prog.accept(new JvmClassVisitor(new File("tyger.class")));
                    break;
                default:
                    throw new RuntimeException("Compiler action is not implemented: " + action);
            }
        } catch (final Exception e) {
            StackTraceElement[] stackTrace = e.getStackTrace();
            StringBuilder at = new StringBuilder();
            for (int i = 0; i < stackTrace.length; i++) {
                if (stackTrace[i].getClassName().startsWith("tyger")) {
                    at.append("\n\tat ")
                        .append(stackTrace[i].getClassName())
                        .append('.')
                        .append(stackTrace[i].getMethodName())
                        .append('(')
                        .append(stackTrace[i].getFileName())
                        .append(':')
                        .append(stackTrace[i].getLineNumber())
                        .append(')');
                    break;
                }
            }
            logger.error("Compilation unsuccessful: {}: {}{}", e.getClass().getName(), e.getMessage(), at);
        }
    }

    public static void usage() {
        logger.info("");
        logger.info("Usage: java -jar tyger.jar <action> <file>");
        logger.info("");
        logger.info("    actions:");
        logger.info("         run         compiles, type-checks and interprets the source file");
        logger.info("         tree        prints a tree representation of the AST");
        logger.info("         type-check  type-checks the source file and outputs the return type");
        logger.info("");
        logger.info("    file : the source code file");
        logger.info("");
    }
}
