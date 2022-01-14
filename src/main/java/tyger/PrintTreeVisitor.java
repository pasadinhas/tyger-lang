package tyger;

import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.GroupedExpressionContext;
import tyger.TygerParser.IdentifierExpressionContext;
import tyger.TygerParser.IfExpressionContext;
import tyger.TygerParser.LiteralExpressionContext;
import tyger.TygerParser.PrefixUnaryExpressionContext;
import tyger.TygerParser.ProgContext;

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

    @Override
    public StringBuilder visitProg(ProgContext ctx) {
        write("<Program>");
        indent();
        ctx.blockExpression().accept(this);
        outdent();
        write("</Program>");
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
        indent();
        write("<Condition>");
        indent();
        ctx.expression().accept(this);
        outdent();
        write("</Condition>");
        write("<Block>");
        indent();
        ctx.block.accept(this);
        outdent();
        write("</Block>");
        write("<Else>");
        indent();
        if (ctx.elseif != null) {
            ctx.elseif.accept(this);
        } else {
            ctx.elseBlock.accept(this);
        }
        outdent();
        write("</Else>");
        outdent();
        write("</If>");

        return builder;
    }
}
