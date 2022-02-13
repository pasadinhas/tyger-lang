package tyger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tyger.TygerParser.ModuleContext;
import tyger.ast.AstNode;
import tyger.binder.Binder;
import tyger.codegen.JvmClassVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class Tyger {
    
    private static final Logger logger = LoggerFactory.getLogger(Tyger.class);
    public static void main(String[] args) {
        if (args.length != 2) {
            logger.error("Invalid number of arguments.");
            usage();
            return;
        }

        if (!Set.of("run", "tree", "type-check", "com", "compile", "ast", "bind").contains(args[0])) {
            logger.error("Invalid <action>: {}", args[0]);
            usage();
            return;
        }

        try {
            new Tyger().run(args[0], args[1]);
        } catch (IOException e) {
            logger.error("Interpreter error", e);
            return;
        }
    }

    public void run(String action, String filepath) throws IOException {
        try {
            if (!filepath.endsWith(".ty")) {
                logger.error("ERROR: Source file must have extension .ty");
                System.exit(1);
                return;
            }

            final Path path = Paths.get(filepath);
            final String fileName = path.getFileName().toString();
            final String fileNameWithoutExtension = fileName.substring(0, fileName.length() - 3); // remove .ty

            if (!fileNameWithoutExtension.matches("^[a-zA-Z]+$")) {
                logger.error("ERROR: Tyger source code file name must only contain letters, got: " + fileNameWithoutExtension);
                System.exit(1);
                return;
            }

            final String source = Files.readString(path);
            final var stream = CharStreams.fromString(source);
            final var lexer = new TygerLexer(stream);
            final var tokens = new CommonTokenStream(lexer);
            final var parser = new TygerParser(tokens);

            final ModuleContext antlr4_module = parser.module();

            switch (action) {
                case "run":
                    antlr4_module.accept(new TypeCheckVisitor(filepath, source));
                    final Object result = antlr4_module.accept(new InterpreterVisitor());
                    logger.info("Output:\n{}", result);
                    break;
                case "tree":
                    final StringBuilder xml = antlr4_module.accept(new PrintTreeVisitor());
                    logger.info("\n{}", xml.toString());
                    break;
                case "type-check":
                    TypeCheckVisitor.Type type = antlr4_module.accept(new TypeCheckVisitor(filepath, source));
                    logger.info("Type: {}", type);
                    break;
                case "com":
                case "compile":
                    antlr4_module.accept(new TypeCheckVisitor(filepath, source));
                    antlr4_module.accept(new JvmClassVisitor(fileNameWithoutExtension));
                    break;
                case "ast":
                    var ast = antlr4_module.accept(new CreateAstVisitor());
                    logger.info(ast.toString());
                    break;
                case "bind":
                    AstNode tyger_ast = antlr4_module.accept(new CreateAstVisitor());
                    final Binder binder = new Binder(fileName, source);
                    tyger_ast.accept(binder);
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
        logger.info("         com[pile]   compiles the program to a .class file");
        logger.info("         ast         generates the module's AST");
        logger.info("");
        logger.info("    file : the source code file");
        logger.info("");
    }
}
