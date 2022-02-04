package tyger;

import org.antlr.v4.runtime.ParserRuleContext;
import tyger.ast.AstNode;
import tyger.ast.Expression;
import tyger.ast.FunctionDeclaration;
import tyger.ast.FunctionDeclaration.Argument;
import tyger.ast.Module;
import tyger.ast.expressions.Block;
import tyger.ast.expressions.NameExpression;
import tyger.ast.expressions.VariableDeclaration;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.BooleanLiteral;
import tyger.ast.expressions.literals.DecimalLiteral;
import tyger.ast.types.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CreateAstVisitor extends TygerBaseVisitor<AstNode> {

    private AstNode.Loc locFromCtx(ParserRuleContext ctx) {
        int start_line = ctx.start.getLine();
        int start_line_char = ctx.start.getCharPositionInLine();
        int stop_line = ctx.stop.getLine();
        int stop_line_char = ctx.stop.getCharPositionInLine() + ctx.stop.getText().length();

        return new AstNode.Loc(start_line, start_line_char, stop_line, stop_line_char);
    }

    @Override
    public AstNode visitModule(final TygerParser.ModuleContext ctx) {
        var functions = ctx.functionDeclarationExpression().stream()
                .map(functionDeclarationExpressionContext -> functionDeclarationExpressionContext.accept(this))
                .map(FunctionDeclaration.class::cast)
                .collect(Collectors.toList());

        final String module_name = ctx.moduleDeclaration().moduleIdentifier().getText();

        return new Module(locFromCtx(ctx), module_name, functions);
    }

    @Override
    public AstNode visitFunctionDeclarationExpression(final TygerParser.FunctionDeclarationExpressionContext ctx) {
        final String name = ctx.identifier().getText();
        final Type return_type = Type.from_source_code(ctx.typeIdentifier().getText());
        final Expression body = (Expression) ctx.blockExpression().accept(this);
        List<Argument> arguments = new ArrayList<>();
        TygerParser.ArgsListContext argsList = ctx.argsList();
        while (argsList != null) {
            arguments.add(new Argument(argsList.identifier().getText(), Type.from_source_code(argsList.typeIdentifier().getText())));
            argsList = argsList.argsList();
        }
        return new FunctionDeclaration(locFromCtx(ctx), name, return_type, arguments, body);
    }

    @Override
    public AstNode visitBlockExpression(final TygerParser.BlockExpressionContext ctx) {
        final List<Expression> expressions = ctx.expression().stream()
                .map(expressionContext -> expressionContext.accept(this))
                .map(Expression.class::cast)
                .collect(Collectors.toList());

        return new Block(locFromCtx(ctx), expressions);
    }

    @Override
    public AstNode visitBinaryExpression(final TygerParser.BinaryExpressionContext ctx) {
        final AstNode.Loc loc = locFromCtx(ctx);
        final Expression left = (Expression) ctx.left.accept(this);
        final Expression right = (Expression) ctx.right.accept(this);
        final String op = ctx.op.getText();

        return switch (op) {
            case "*" -> new Multiplication(loc, left, right);
            case "/" -> new Division(loc, left, right);
            case "%" -> new Modulo(loc, left, right);
            case "+" -> new Addition(loc, left, right);
            case "-" -> new Subtraction(loc, left, right);
            case "<<" -> new LeftShift(loc, left, right);
            case ">>" -> new RightShift(loc, left, right);
            case "&" -> new BitAnd(loc, left, right);
            case "^" -> new BitXor(loc, left, right);
            case "|" -> new BitOr(loc, left, right);
            case "==" -> new Equals(loc, left, right);
            case "!=" -> new NotEquals(loc, left, right);
            case "<" -> new LessThan(loc, left, right);
            case "<=" -> new LessThanOrEquals(loc, left, right);
            case ">" -> new GreaterThan(loc, left, right);
            case ">=" -> new GreaterThanOrEquals(loc, left, right);
            case "and" -> new And(loc, left, right);
            case "or" -> new Or(loc, left, right);
            default -> throw new RuntimeException("Operator is not implemented yet: " + op);
        };
    }

    @Override
    public AstNode visitIdentifierExpression(final TygerParser.IdentifierExpressionContext ctx) {
        return new NameExpression(locFromCtx(ctx), ctx.identifier().getText());
    }

    @Override
    public AstNode visitLiteralExpression(final TygerParser.LiteralExpressionContext ctx) {
        final AstNode.Loc loc = locFromCtx(ctx);

        if (ctx.INTEGER_LITERAL() != null) {
            return new DecimalLiteral(loc, Long.valueOf(ctx.INTEGER_LITERAL().getText()));
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return new BooleanLiteral(loc, Boolean.valueOf(ctx.BOOLEAN_LITERAL().getText()));
        }

        throw new RuntimeException("Literal not implemented yet: " + ctx.getText());
    }

    @Override
    public AstNode visitVariableDeclarationExpression(final TygerParser.VariableDeclarationExpressionContext ctx) {
        final Expression expression = (Expression) ctx.expression().accept(this);
        final String name = ctx.identifier().getText();
        final Type declared_type = Type.from_source_code(ctx.typeIdentifier().getText());
        return new VariableDeclaration(locFromCtx(ctx), declared_type, name, expression);
    }

    @Override
    public AstNode visitGroupedExpression(final TygerParser.GroupedExpressionContext ctx) {
        return ctx.expression().accept(this);
    }

    @Override
    public AstNode visitAssignmentExpression(final TygerParser.AssignmentExpressionContext ctx) {
        final Expression left = (Expression) ctx.identifier().accept(this);
        final Expression right = (Expression) ctx.expression().accept(this);
        return new Assignment(locFromCtx(ctx), left, right);
    }
}
