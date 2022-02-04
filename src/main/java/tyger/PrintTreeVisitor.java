package tyger;

import tyger.TygerParser.ArgsListContext;
import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.FunctionDeclarationExpressionContext;
import tyger.TygerParser.GroupedExpressionContext;
import tyger.TygerParser.IdentifierExpressionContext;
import tyger.TygerParser.IfExpressionContext;
import tyger.TygerParser.LiteralExpressionContext;
import tyger.TygerParser.PostfixUnaryExpressionContext;
import tyger.TygerParser.PrefixUnaryExpressionContext;
import tyger.TygerParser.PrintExpressionContext;
import tyger.TygerParser.ModuleContext;
import tyger.TygerParser.VariableDeclarationExpressionContext;
import tyger.TygerParser.WhileExpressionContext;

public class PrintTreeVisitor extends TygerBaseVisitor<StringBuilder> {

    private final StringBuilder builder = new StringBuilder();
    private int indentation = 0;
    private static final int STEP = 4;

    private void write(String format, Object... args) {
        for (int i = 0; i < indentation; ++i) {
            builder.append(' ');
        }

        builder.append(String.format(format, args));
        builder.append('\n');
    }

    private void indent() {
        indentation += STEP;
    }

    private void outdent() {
        indentation -= STEP;
    }

    private void withIndentation(Runnable runnable) {
        indent();
        runnable.run();
        outdent();
    }

    @Override
    public StringBuilder visitModule(ModuleContext ctx) {
        write("<Module>");
        withIndentation(() ->
                ctx.functionDeclarationExpression().forEach(function -> function.accept(this)));
        write("</Module>");
        return builder;
    }    

    @Override
    public StringBuilder visitFunctionDeclarationExpression(FunctionDeclarationExpressionContext ctx) {
        write("<Function name=\"%s\" type=\"%s\">", ctx.identifier().getText(), ctx.typeIdentifier().getText());
        withIndentation(() -> {
            if (ctx.argsList() != null) {
                ctx.argsList().accept(this);
            }
            ctx.blockExpression().accept(this);
        });
        write("</Function>");

        return builder;
    }

    @Override
    public StringBuilder visitArgsList(ArgsListContext ctx) {
        write("<Argument type=\"%s\">%s</Argument>", ctx.typeIdentifier().getText(), ctx.identifier().getText());
        
        if (ctx.argsList() != null) {
            ctx.argsList().accept(this);
        }

        return builder;
    }

    @Override
    public StringBuilder visitBlockExpression(BlockExpressionContext ctx) {
        write("<Block numberOfExpressions=\"%s\">", ctx.expression().size());
        indent();
        ctx.expression().forEach(expression -> expression.accept(this));
        outdent();
        write("</Block>");
        return builder;
    }
    
    @Override
    public StringBuilder visitGroupedExpression(GroupedExpressionContext ctx) {
        return ctx.expression().accept(this);
    }

    @Override
    public StringBuilder visitAssignmentExpression(AssignmentExpressionContext ctx) {
        write("<Assignment label=\"%s\">", ctx.identifier().getText());
        indent();
        ctx.expression().accept(this);
        outdent();
        write("</Assignment>");
        
        return builder;
    }

    @Override
    public StringBuilder visitIdentifierExpression(IdentifierExpressionContext ctx) {
        write("<Variable identifier=\"%s\"/>", ctx.identifier().getText());
        return builder;
    }

    @Override
    public StringBuilder visitPrefixUnaryExpression(PrefixUnaryExpressionContext ctx) {
        write("<PrefixUnaryExpression operator=\"%s\">", ctx.op.getText());
        indent();
        ctx.expression().accept(this);
        outdent();
        write("</PrefixUnaryExpression>");
        
        return builder;
    }

    @Override
    public StringBuilder visitPostfixUnaryExpression(PostfixUnaryExpressionContext ctx) {
        write("<PostfixUnaryExpression operator=\"%s\">", ctx.op.getText());
        indent();
        ctx.expression().accept(this);
        outdent();
        write("</PostfixUnaryExpression>");
        
        return builder;
    }

    @Override
    public StringBuilder visitBinaryExpression(BinaryExpressionContext ctx) {
        write("<BinaryExpression operator=\"%s\" expression=\"%s\">", ctx.op.getText(), ctx.getText());
        indent();
            write("<LeftOperand>");
            indent();
            ctx.left.accept(this);
            outdent();
            write("</LeftOperand>");
            write("<RightOperand>");
            indent();
            ctx.right.accept(this);
            outdent();
            write("</RightOperand>");
        outdent();
        write("</BinaryExpression>");
        return builder;
    }

    @Override
    public StringBuilder visitLiteralExpression(LiteralExpressionContext ctx) {
        write("<Literal value=\"%s\"/>", ctx.getText());
        return builder;
    }

    @Override
    public StringBuilder visitIfExpression(IfExpressionContext ctx) {
        write("<If>");
        withIndentation(() -> {
            write("<Condition>");
            withIndentation(() -> ctx.expression().accept(this));
            write("</Condition>");
            write("<Block>");
            withIndentation(() -> ctx.block.accept(this));
            write("</Block>");
            if (ctx.elseBlock == null) { return; }
            write("<Else>");
            withIndentation(() -> ctx.elseBlock.accept(this));
            write("</Else>");
        });
        write("</If>");

        return builder;
    }

    @Override
    public StringBuilder visitVariableDeclarationExpression(VariableDeclarationExpressionContext ctx) {
        write("<VariableDeclaration label=\"%s\" type=\"%s\">", ctx.identifier().getText(), ctx.typeIdentifier().getText());
        indent();
        ctx.expression().accept(this);
        outdent();
        write("</VariableDeclaration>");

        return builder;
    }

    @Override
    public StringBuilder visitWhileExpression(WhileExpressionContext ctx) {
        write("<While>");
        indent();
            write("<Condition>");
            indent();
            ctx.condition.accept(this);
            outdent();
            write("</Condition>");
            ctx.blockExpression().accept(this);
        outdent();
        write("</While>");
        return builder;
    }

    @Override
    public StringBuilder visitPrintExpression(PrintExpressionContext ctx) {
        write("<Print>");
        indent();
        ctx.expression().accept(this);
        outdent();
        write("</Print>");

        return builder;
    }

    @Override
    public StringBuilder visitFunctionCallExpression(final TygerParser.FunctionCallExpressionContext ctx) {
        write("<FunctionCall name=\"%s\">", ctx.identifier().getText());
        withIndentation(() -> {
            if (ctx.params != null) {
                ctx.params.accept(this);
            }
        });
        write("</FunctionCall>");
        return builder;
    }

    @Override
    public StringBuilder visitExpressionList(final TygerParser.ExpressionListContext ctx) {
        ctx.expression().accept(this);
        if (ctx.expressionList() != null) {
            ctx.expressionList().accept(this);
        }
        return builder;
    }
}
