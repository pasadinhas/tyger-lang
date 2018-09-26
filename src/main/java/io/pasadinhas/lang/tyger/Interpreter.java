/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger;

import io.pasadinhas.lang.tyger.ast.FunctionDeclaration;
import io.pasadinhas.lang.tyger.ast.Program;
import io.pasadinhas.lang.tyger.typechecker.Scope;
import io.pasadinhas.lang.tyger.typechecker.TypeChecker;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Interpreter {

    public static void main(String[] args) throws IOException {
        final byte[] encoded = Files.readAllBytes(Paths.get("ex.ty"));
        final String source = new String(encoded);
        final Program program = parse(source);
        System.out.println(program.typeCheck(new TypeChecker(), new Scope()));
        System.out.println(program.eval(new ExecutionContext()));
    }

    public static Program parse(String someLangSourceCode) {
        CharStream charStream = new ANTLRInputStream(someLangSourceCode);
        TygerLexer lexer = new TygerLexer(charStream);
        TokenStream tokens = new CommonTokenStream(lexer);
        TygerParser parser = new TygerParser(tokens);

        ProgVisitor classVisitor = new ProgVisitor();
        return classVisitor.visit(parser.prog());
    }

    public static class ProgVisitor extends TygerBaseVisitor<Program> {
        @Override
        public Program visitProg(final TygerParser.ProgContext ctx) {
            final Program program = new Program();

            ctx.declarations().stream()
                    .filter(expr -> expr.functionDeclaration() != null)
                    .map(expr -> new FunctionDeclaration(expr.functionDeclaration()))
                    .forEach(functionDeclaration -> program.registerFunction(functionDeclaration));

            return program;
        }
    }
}
