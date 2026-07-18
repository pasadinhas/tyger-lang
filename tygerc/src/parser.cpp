#include "parser.h"
#include <cstdio>
#include <cstring>
#include <cstdlib>

// ---------------------------------------------------------------------------
// Parser state
// ---------------------------------------------------------------------------

typedef struct {
    TokenList *tokens;
    size_t     current;
    Arena     *arena;
    char       errbuf[512];
} Parser;

static inline Token *parser_peek(Parser *p, int offset) {
    size_t idx = p->current + (size_t)offset;
    // Always return the last token (EOF) if out of bounds
    if (idx >= p->tokens->len) return &p->tokens->data[p->tokens->len - 1];
    return &p->tokens->data[idx];
}

static inline Token *parser_eat(Parser *p) {
    Token *t = parser_peek(p, 0);
    if (p->current < p->tokens->len) p->current++;
    return t;
}

static inline bool parser_match(Parser *p, TokenType type) {
    return parser_peek(p, 0)->type == type;
}

// Consume a token of the expected type. On mismatch, write to errbuf and
// return NULL.
static Token *parser_expect(Parser *p, TokenType type, const char *msg) {
    if (!parser_match(p, type)) {
        Token *got = parser_peek(p, 0);
        snprintf(p->errbuf, sizeof(p->errbuf),
                 "%s (got '%s' at line %u, col %u)",
                 msg, token_type_name(got->type), got->loc.line, got->loc.col);
        return NULL;
    }
    return parser_eat(p);
}

// Forward declarations
static Node *parse_statement(Parser *p);
static Node *parse_expression(Parser *p);
static Node *parse_block(Parser *p);

// ---------------------------------------------------------------------------
// Error propagation: if a sub-call returns NULL, errbuf is already set.
// Callers just return NULL to bubble the error up.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Params list: (name: type, name: type [, ...])
// Used by both fn and extern fn.
// is_extern: allows '...' variadic marker.
// out_variadic: set to true if '...' was found.
// ---------------------------------------------------------------------------

static bool parse_params(Parser *p, ParamList *out, bool is_extern, bool *out_variadic) {
    if (!parser_expect(p, TK_LPAREN, "expected '(' to open parameter list")) return false;

    if (out_variadic) *out_variadic = false;
    bool trailing_comma = false;

    while (!parser_match(p, TK_RPAREN)) {
        trailing_comma = false;

        // Variadic marker '...' only allowed in extern fn
        if (is_extern && parser_match(p, TK_DOTDOTDOT)) {
            parser_eat(p);
            if (out_variadic) *out_variadic = true;
            if (!parser_match(p, TK_RPAREN)) {
                Token *got = parser_peek(p, 0);
                snprintf(p->errbuf, sizeof(p->errbuf),
                         "'...' must be the last parameter (got '%s' at line %u, col %u)",
                         token_type_name(got->type), got->loc.line, got->loc.col);
                return false;
            }
            break;
        }

        Token *name_tok = parser_expect(p, TK_IDENTIFIER, "expected parameter name");
        if (!name_tok) return false;

        if (!parser_expect(p, TK_COLON, "expected ':' after parameter name")) return false;

        Token *type_tok = parser_expect(p, TK_IDENTIFIER, "expected type name after ':'");
        if (!type_tok) return false;

        Param param;
        param.name      = name_tok->value;
        param.type_name = type_tok->value;
        param.loc       = name_tok->loc;
        da_push(out, param);

        if (parser_match(p, TK_COMMA)) {
            trailing_comma = true;
            parser_eat(p);
        }
    }

    if (trailing_comma) {
        snprintf(p->errbuf, sizeof(p->errbuf), "trailing comma in parameter list");
        return false;
    }

    if (!parser_expect(p, TK_RPAREN, "expected ')' to close parameter list")) return false;
    return true;
}

// ---------------------------------------------------------------------------
// Statements
// ---------------------------------------------------------------------------

// extern fn <name>(<params>) -> <type>;
static Node *parse_extern_function_decl(Parser *p) {
    Token *extern_tok = parser_eat(p); // extern
    if (!parser_expect(p, TK_FN, "expected 'fn' after 'extern'")) return NULL;

    Token *name_tok = parser_expect(p, TK_IDENTIFIER, "expected function name");
    if (!name_tok) return NULL;

    AstExternFunctionDecl *node = ast_alloc(p->arena, AstExternFunctionDecl,
                                            NK_EXTERN_FUNCTION_DECL, extern_tok->loc);
    node->name        = name_tok->value;
    node->is_variadic = false;

    if (!parse_params(p, &node->params, true, &node->is_variadic)) return NULL;
    if (!parser_expect(p, TK_ARROW, "expected '->' after parameter list")) return NULL;

    Token *ret_tok = parser_expect(p, TK_IDENTIFIER, "expected return type");
    if (!ret_tok) return NULL;
    node->return_type_name = ret_tok->value;

    if (!parser_expect(p, TK_SEMICOLON, "expected ';' after extern function declaration")) return NULL;
    return (Node *)node;
}

// fn <name>(<params>) -> <type> <block>
static Node *parse_function_decl(Parser *p) {
    Token *fn_tok = parser_eat(p); // fn

    Token *name_tok = parser_expect(p, TK_IDENTIFIER, "expected function name");
    if (!name_tok) return NULL;

    AstFunctionDecl *node = ast_alloc(p->arena, AstFunctionDecl, NK_FUNCTION_DECL, fn_tok->loc);
    node->name = name_tok->value;

    if (!parse_params(p, &node->params, false, NULL)) return NULL;
    if (!parser_expect(p, TK_ARROW, "expected '->' after parameter list")) return NULL;

    Token *ret_tok = parser_expect(p, TK_IDENTIFIER, "expected return type");
    if (!ret_tok) return NULL;
    node->return_type_name = ret_tok->value;

    node->body = parse_block(p);
    if (!node->body) return NULL;

    return (Node *)node;
}

// let [mut] <name> [: <type>] = <expr>;
static Node *parse_var_decl(Parser *p) {
    Token *let_tok = parser_eat(p); // let

    bool mutable_ = false;
    if (parser_match(p, TK_MUT)) {
        parser_eat(p);
        mutable_ = true;
    }

    Token *name_tok = parser_expect(p, TK_IDENTIFIER, "expected variable name");
    if (!name_tok) return NULL;

    AstVarDecl *node = ast_alloc(p->arena, AstVarDecl, NK_VAR_DECL, let_tok->loc);
    node->name     = name_tok->value;
    node->mutable_ = mutable_;

    // Optional type hint: ': type'
    if (parser_match(p, TK_COLON)) {
        parser_eat(p);
        Token *type_tok = parser_expect(p, TK_IDENTIFIER, "expected type name after ':'");
        if (!type_tok) return NULL;
        node->type_name = type_tok->value;
    }

    if (!parser_expect(p, TK_EQ, "expected '=' in variable declaration")) return NULL;

    node->init = parse_expression(p);
    if (!node->init) return NULL;

    if (!parser_expect(p, TK_SEMICOLON, "expected ';' after variable declaration")) return NULL;
    return (Node *)node;
}

// return <expr>;
static Node *parse_return(Parser *p) {
    Token *ret_tok = parser_eat(p); // return

    AstReturn *node = ast_alloc(p->arena, AstReturn, NK_RETURN, ret_tok->loc);
    node->expr = parse_expression(p);
    if (!node->expr) return NULL;

    if (!parser_expect(p, TK_SEMICOLON, "expected ';' after return statement")) return NULL;
    return (Node *)node;
}

// if <cond> <stmt> [else <stmt>]
static Node *parse_if(Parser *p) {
    Token *if_tok = parser_eat(p); // if

    AstIf *node = ast_alloc(p->arena, AstIf, NK_IF, if_tok->loc);

    node->cond = parse_expression(p);
    if (!node->cond) return NULL;

    node->then = parse_statement(p);
    if (!node->then) return NULL;

    if (parser_match(p, TK_ELSE)) {
        parser_eat(p);
        node->else_ = parse_statement(p);
        if (!node->else_) return NULL;
    }

    return (Node *)node;
}

// { <stmts> }
static Node *parse_block(Parser *p) {
    Token *lbrace = parser_expect(p, TK_LBRACE, "expected '{' to open block");
    if (!lbrace) return NULL;

    AstBlock *node = ast_alloc(p->arena, AstBlock, NK_BLOCK, lbrace->loc);

    while (!parser_match(p, TK_RBRACE) && !parser_match(p, TK_EOF)) {
        Node *stmt = parse_statement(p);
        if (!stmt) return NULL;
        da_push(&node->body, stmt);
    }

    if (!parser_expect(p, TK_RBRACE, "expected '}' to close block")) return NULL;
    return (Node *)node;
}

// <expr>;   (expression used as a statement)
static Node *parse_expression_stmt(Parser *p) {
    Node *expr = parse_expression(p);
    if (!expr) return NULL;
    if (!parser_expect(p, TK_SEMICOLON, "expected ';' after expression statement")) return NULL;
    return expr;
}

static Node *parse_statement(Parser *p) {
    switch (parser_peek(p, 0)->type) {
        case TK_EXTERN: return parse_extern_function_decl(p);
        case TK_FN:     return parse_function_decl(p);
        case TK_LET:    return parse_var_decl(p);
        case TK_RETURN: return parse_return(p);
        case TK_IF:     return parse_if(p);
        case TK_LBRACE: return parse_block(p);
        default:        return parse_expression_stmt(p);
    }
}

// ---------------------------------------------------------------------------
// Expressions — standard precedence chain (lowest to highest)
//
//   assignment  (right-assoc)
//   equality    == !=
//   relational  < > <= >=
//   additive    + -
//   multiplicative  * / %
//   unary       (passthrough for now)
//   postfix     call()
//   primary     literals, identifiers, (grouped)
// ---------------------------------------------------------------------------

static Node *parse_primary(Parser *p);
static Node *parse_postfix(Parser *p);
static Node *parse_multiplicative(Parser *p);
static Node *parse_additive(Parser *p);
static Node *parse_relational(Parser *p);
static Node *parse_equality(Parser *p);
static Node *parse_assignment(Parser *p);

static Node *parse_primary(Parser *p) {
    Token *t = parser_peek(p, 0);

    switch (t->type) {
        case TK_IDENTIFIER: {
            parser_eat(p);
            AstIdentifier *node = ast_alloc(p->arena, AstIdentifier, NK_IDENTIFIER, t->loc);
            node->name = t->value;
            return (Node *)node;
        }
        case TK_NUMBER: {
            parser_eat(p);
            AstNumericLiteral *node = ast_alloc(p->arena, AstNumericLiteral, NK_NUMERIC_LITERAL, t->loc);
            node->raw   = t->value;
            node->value = (int64_t)strtoll(t->value.data, NULL, 10);
            return (Node *)node;
        }
        case TK_STRING: {
            parser_eat(p);
            AstStringLiteral *node = ast_alloc(p->arena, AstStringLiteral, NK_STRING_LITERAL, t->loc);
            node->value = t->value;
            return (Node *)node;
        }
        case TK_TRUE:
        case TK_FALSE: {
            parser_eat(p);
            AstBooleanLiteral *node = ast_alloc(p->arena, AstBooleanLiteral, NK_BOOLEAN_LITERAL, t->loc);
            node->value = (t->type == TK_TRUE);
            return (Node *)node;
        }
        case TK_LPAREN: {
            parser_eat(p); // (
            Node *inner = parse_expression(p);
            if (!inner) return NULL;
            if (!parser_expect(p, TK_RPAREN, "expected ')' to close grouped expression")) return NULL;
            return inner;
        }
        default: {
            snprintf(p->errbuf, sizeof(p->errbuf),
                     "unexpected token '%s' at line %u, col %u",
                     token_type_name(t->type), t->loc.line, t->loc.col);
            return NULL;
        }
    }
}

// <expr>(<args>)  — chained: f(1)(2) works naturally via the loop
static Node *parse_postfix(Parser *p) {
    Node *left = parse_primary(p);
    if (!left) return NULL;

    while (parser_match(p, TK_LPAREN)) {
        Token *lparen = parser_eat(p); // (
        AstCallExpr *call = ast_alloc(p->arena, AstCallExpr, NK_CALL_EXPR, left->loc);
        call->callee = left;

        bool trailing_comma = false;
        while (!parser_match(p, TK_RPAREN) && !parser_match(p, TK_EOF)) {
            trailing_comma = false;
            Node *arg = parse_expression(p);
            if (!arg) return NULL;
            da_push(&call->args, arg);

            if (parser_match(p, TK_COMMA)) {
                trailing_comma = true;
                parser_eat(p);
            } else {
                break;
            }
        }

        if (trailing_comma) {
            snprintf(p->errbuf, sizeof(p->errbuf),
                     "trailing comma in argument list at line %u, col %u",
                     lparen->loc.line, lparen->loc.col);
            return NULL;
        }

        if (!parser_expect(p, TK_RPAREN, "expected ')' to close argument list")) return NULL;
        left = (Node *)call;
    }

    return left;
}

static Node *parse_multiplicative(Parser *p) {
    Node *left = parse_postfix(p);
    if (!left) return NULL;

    while (parser_match(p, TK_STAR) || parser_match(p, TK_SLASH) || parser_match(p, TK_PERCENT)) {
        Token *op = parser_eat(p);
        Node *right = parse_postfix(p);
        if (!right) return NULL;
        AstBinaryExpr *node = ast_alloc(p->arena, AstBinaryExpr, NK_BINARY_EXPR, left->loc);
        node->left  = left;
        node->right = right;
        node->op    = sv_from_cstr(token_type_name(op->type));
        left = (Node *)node;
    }
    return left;
}

static Node *parse_additive(Parser *p) {
    Node *left = parse_multiplicative(p);
    if (!left) return NULL;

    while (parser_match(p, TK_PLUS) || parser_match(p, TK_MINUS)) {
        Token *op = parser_eat(p);
        Node *right = parse_multiplicative(p);
        if (!right) return NULL;
        AstBinaryExpr *node = ast_alloc(p->arena, AstBinaryExpr, NK_BINARY_EXPR, left->loc);
        node->left  = left;
        node->right = right;
        node->op    = sv_from_cstr(token_type_name(op->type));
        left = (Node *)node;
    }
    return left;
}

static Node *parse_relational(Parser *p) {
    Node *left = parse_additive(p);
    if (!left) return NULL;

    while (parser_match(p, TK_LT)  || parser_match(p, TK_GT) ||
           parser_match(p, TK_LEQ) || parser_match(p, TK_GEQ)) {
        Token *op = parser_eat(p);
        Node *right = parse_additive(p);
        if (!right) return NULL;
        AstBinaryExpr *node = ast_alloc(p->arena, AstBinaryExpr, NK_BINARY_EXPR, left->loc);
        node->left  = left;
        node->right = right;
        node->op    = sv_from_cstr(token_type_name(op->type));
        left = (Node *)node;
    }
    return left;
}

static Node *parse_equality(Parser *p) {
    Node *left = parse_relational(p);
    if (!left) return NULL;

    while (parser_match(p, TK_EQEQ) || parser_match(p, TK_NEQ)) {
        Token *op = parser_eat(p);
        Node *right = parse_relational(p);
        if (!right) return NULL;
        AstBinaryExpr *node = ast_alloc(p->arena, AstBinaryExpr, NK_BINARY_EXPR, left->loc);
        node->left  = left;
        node->right = right;
        node->op    = sv_from_cstr(token_type_name(op->type));
        left = (Node *)node;
    }
    return left;
}

static Node *parse_assignment(Parser *p) {
    Node *left = parse_equality(p);
    if (!left) return NULL;

    TokenType t = parser_peek(p, 0)->type;
    if (t == TK_EQ || t == TK_PLUS_EQ || t == TK_MINUS_EQ ||
        t == TK_STAR_EQ || t == TK_SLASH_EQ || t == TK_PERCENT_EQ) {
        Token *op = parser_eat(p);
        Node *right = parse_assignment(p); // right-associative
        if (!right) return NULL;
        AstAssignmentExpr *node = ast_alloc(p->arena, AstAssignmentExpr, NK_ASSIGNMENT_EXPR, left->loc);
        node->left  = left;
        node->right = right;
        node->op    = sv_from_cstr(token_type_name(op->type));
        return (Node *)node;
    }

    return left;
}

static Node *parse_expression(Parser *p) {
    return parse_assignment(p);
}

// ---------------------------------------------------------------------------
// Top-level
// ---------------------------------------------------------------------------

static AstProgram *parse_program(Parser *p) {
    Loc loc = parser_peek(p, 0)->loc;
    AstProgram *program = ast_alloc(p->arena, AstProgram, NK_PROGRAM, loc);

    while (!parser_match(p, TK_EOF)) {
        Node *stmt = parse_statement(p);
        if (!stmt) return NULL;
        da_push(&program->body, stmt);
    }

    return program;
}

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

ParseResult parse(TokenList *tokens, Arena *arena) {
    ParseResult result;
    result.program = NULL;
    result.error   = NULL;

    Parser p;
    p.tokens   = tokens;
    p.current  = 0;
    p.arena    = arena;
    p.errbuf[0] = '\0';

    AstProgram *program = parse_program(&p);

    if (!program || p.errbuf[0] != '\0') {
        result.error = arena_copy_str(arena, p.errbuf, strlen(p.errbuf));
        return result;
    }

    result.program = program;
    return result;
}
