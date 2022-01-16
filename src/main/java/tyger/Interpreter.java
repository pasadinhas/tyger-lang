package tyger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tyger.TygerParser.ProgContext;

public class Interpreter {
    
    private static final Logger logger = LoggerFactory.getLogger(Interpreter.class);
    public static void main(String[] args) {
        if (args.length != 1) {
            logger.error("The Interpreter receives a single argument. Received: {}", Arrays.asList(args));
            return;
        }

        try {
            new Interpreter().run(args[0]);
        } catch (IOException e) {
            logger.error("Interpreter error", e);
            return;
        }
    }

    public void run(String filepath) throws IOException {
        logger.info("Source file: {}", filepath);
        
        final String source = Files.readString(Paths.get(filepath));
        logger.info("Source code:\n{}", source);

        final var stream = CharStreams.fromString(source);
        final var lexer = new TygerLexer(stream);
        final var tokens = new CommonTokenStream(lexer);
        final var parser = new TygerParser(tokens);

        final ProgContext prog = parser.prog();
        logger.info("Parsed program:\n{}", prog.getText());

        final StringBuilder xml = prog.accept(new PrintTreeVisitor());
        logger.info("XML Parse Tree: \n{}", xml.toString());

        final Object resultType = prog.accept(new TypeCheckVisitor());
        logger.info("Output type: {}", resultType);
        final Object result = prog.accept(new InterpreterVisitor());
        logger.info("Output:\n{}", result);
    }

}
