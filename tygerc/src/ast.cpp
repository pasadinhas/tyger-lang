#include "ast.h"
#include <cstdio>
#include <cstdarg>
#include <cstring>

const char *node_kind_name(NodeKind kind) {
    static_assert(NK_COUNT == 14, "node_kind_name: NodeKind changed, update this switch");
    switch (kind) {
        case NK_PROGRAM:              return "Program";
        case NK_VAR_DECL:             return "VarDecl";
        case NK_FUNCTION_DECL:        return "FunctionDecl";
        case NK_EXTERN_FUNCTION_DECL: return "ExternFunctionDecl";
        case NK_BLOCK:                return "Block";
        case NK_RETURN:               return "Return";
        case NK_IF:                   return "If";
        case NK_IDENTIFIER:           return "Identifier";
        case NK_NUMERIC_LITERAL:      return "NumericLiteral";
        case NK_STRING_LITERAL:       return "StringLiteral";
        case NK_BOOLEAN_LITERAL:      return "BooleanLiteral";
        case NK_BINARY_EXPR:          return "BinaryExpr";
        case NK_ASSIGNMENT_EXPR:      return "AssignmentExpr";
        case NK_CALL_EXPR:            return "CallExpr";
        case NK_COUNT:                break;
    }
    return "<unknown>";
}

// ---------------------------------------------------------------------------
// Debug printer
// ---------------------------------------------------------------------------

// Writes `indent * 2` spaces into buf at offset *pos.
static void write_indent(char *buf, int buf_size, int *pos, int indent) {
    for (int i = 0; i < indent * 2 && *pos < buf_size - 1; i++) {
        buf[(*pos)++] = ' ';
    }
}

// printf-style append into buf at offset *pos.
static void write_fmt(char *buf, int buf_size, int *pos, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int remaining = buf_size - *pos - 1;
    if (remaining > 0) {
        int written = vsnprintf(buf + *pos, (size_t)remaining, fmt, ap);
        if (written > 0) *pos += (written < remaining ? written : remaining);
    }
    va_end(ap);
}

static void node_to_str(const Node *node, char *buf, int buf_size, int *pos, int indent);

static void nodelist_to_str(const NodeList *list, char *buf, int buf_size, int *pos, int indent) {
    for (size_t i = 0; i < list->len; i++) {
        node_to_str(list->data[i], buf, buf_size, pos, indent);
    }
}

static void node_to_str(const Node *node, char *buf, int buf_size, int *pos, int indent) {
    if (!node) {
        write_indent(buf, buf_size, pos, indent);
        write_fmt(buf, buf_size, pos, "(null)\n");
        return;
    }

    static_assert(NK_COUNT == 14, "node_to_str: NodeKind changed, update this switch");

    write_indent(buf, buf_size, pos, indent);

    switch (node->kind) {
        case NK_PROGRAM: {
            AstProgram *n = (AstProgram *)node;
            write_fmt(buf, buf_size, pos, "Program [%zu statements]\n", n->body.len);
            nodelist_to_str(&n->body, buf, buf_size, pos, indent + 1);
            break;
        }
        case NK_VAR_DECL: {
            AstVarDecl *n = (AstVarDecl *)node;
            write_fmt(buf, buf_size, pos, "VarDecl %s" SV_FMT " [%s]\n",
                      n->mutable_ ? "mut " : "",
                      SV_ARG(n->name),
                      n->type_name.len ? "typed" : "inferred");
            node_to_str(n->init, buf, buf_size, pos, indent + 1);
            break;
        }
        case NK_FUNCTION_DECL: {
            AstFunctionDecl *n = (AstFunctionDecl *)node;
            write_fmt(buf, buf_size, pos, "FunctionDecl " SV_FMT " -> " SV_FMT " (%zu params)\n",
                      SV_ARG(n->name), SV_ARG(n->return_type_name), n->params.len);
            node_to_str(n->body, buf, buf_size, pos, indent + 1);
            break;
        }
        case NK_EXTERN_FUNCTION_DECL: {
            AstExternFunctionDecl *n = (AstExternFunctionDecl *)node;
            write_fmt(buf, buf_size, pos, "ExternFunctionDecl " SV_FMT " -> " SV_FMT " (%zu params%s)\n",
                      SV_ARG(n->name), SV_ARG(n->return_type_name),
                      n->params.len, n->is_variadic ? ", variadic" : "");
            break;
        }
        case NK_BLOCK: {
            AstBlock *n = (AstBlock *)node;
            write_fmt(buf, buf_size, pos, "Block [%zu statements]\n", n->body.len);
            nodelist_to_str(&n->body, buf, buf_size, pos, indent + 1);
            break;
        }
        case NK_RETURN: {
            AstReturn *n = (AstReturn *)node;
            write_fmt(buf, buf_size, pos, "Return\n");
            node_to_str(n->expr, buf, buf_size, pos, indent + 1);
            break;
        }
        case NK_IF: {
            AstIf *n = (AstIf *)node;
            write_fmt(buf, buf_size, pos, "If\n");
            write_indent(buf, buf_size, pos, indent + 1);
            write_fmt(buf, buf_size, pos, "cond:\n");
            node_to_str(n->cond, buf, buf_size, pos, indent + 2);
            write_indent(buf, buf_size, pos, indent + 1);
            write_fmt(buf, buf_size, pos, "then:\n");
            node_to_str(n->then, buf, buf_size, pos, indent + 2);
            if (n->else_) {
                write_indent(buf, buf_size, pos, indent + 1);
                write_fmt(buf, buf_size, pos, "else:\n");
                node_to_str(n->else_, buf, buf_size, pos, indent + 2);
            }
            break;
        }
        case NK_IDENTIFIER: {
            AstIdentifier *n = (AstIdentifier *)node;
            write_fmt(buf, buf_size, pos, "Identifier(" SV_FMT ")\n", SV_ARG(n->name));
            break;
        }
        case NK_NUMERIC_LITERAL: {
            AstNumericLiteral *n = (AstNumericLiteral *)node;
            write_fmt(buf, buf_size, pos, "NumericLiteral(%lld)\n", (long long)n->value);
            break;
        }
        case NK_STRING_LITERAL: {
            AstStringLiteral *n = (AstStringLiteral *)node;
            write_fmt(buf, buf_size, pos, "StringLiteral(\"" SV_FMT "\")\n", SV_ARG(n->value));
            break;
        }
        case NK_BOOLEAN_LITERAL: {
            AstBooleanLiteral *n = (AstBooleanLiteral *)node;
            write_fmt(buf, buf_size, pos, "BooleanLiteral(%s)\n", n->value ? "true" : "false");
            break;
        }
        case NK_BINARY_EXPR: {
            AstBinaryExpr *n = (AstBinaryExpr *)node;
            write_fmt(buf, buf_size, pos, "BinaryExpr(" SV_FMT ")\n", SV_ARG(n->op));
            node_to_str(n->left,  buf, buf_size, pos, indent + 1);
            node_to_str(n->right, buf, buf_size, pos, indent + 1);
            break;
        }
        case NK_ASSIGNMENT_EXPR: {
            AstAssignmentExpr *n = (AstAssignmentExpr *)node;
            write_fmt(buf, buf_size, pos, "AssignmentExpr(" SV_FMT ")\n", SV_ARG(n->op));
            node_to_str(n->left,  buf, buf_size, pos, indent + 1);
            node_to_str(n->right, buf, buf_size, pos, indent + 1);
            break;
        }
        case NK_CALL_EXPR: {
            AstCallExpr *n = (AstCallExpr *)node;
            write_fmt(buf, buf_size, pos, "CallExpr [%zu args]\n", n->args.len);
            write_indent(buf, buf_size, pos, indent + 1);
            write_fmt(buf, buf_size, pos, "callee:\n");
            node_to_str(n->callee, buf, buf_size, pos, indent + 2);
            write_indent(buf, buf_size, pos, indent + 1);
            write_fmt(buf, buf_size, pos, "args:\n");
            nodelist_to_str(&n->args, buf, buf_size, pos, indent + 2);
            break;
        }
        case NK_COUNT:
            write_fmt(buf, buf_size, pos, "<invalid NK_COUNT>\n");
            break;
    }
}

int node_to_string(const Node *node, char *buf, int buf_size, int indent) {
    int pos = 0;
    node_to_str(node, buf, buf_size, &pos, indent);
    if (pos < buf_size) buf[pos] = '\0';
    return pos;
}
