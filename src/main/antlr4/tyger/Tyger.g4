// Define a grammar called Hello
grammar Tyger;

module : moduleDeclaration functionDeclarationExpression+ EOF;

moduleDeclaration
    : 'module' moduleIdentifier ';'?
    ;

moduleIdentifier : IDENTIFIER ('.' IDENTIFIER)* ;

blockExpression
    : '{' expression* '}'
    ;

functionDeclarationExpression
    : 'func' typeIdentifier identifier '(' argsList? ')' blockExpression
    ;

argsList
    : typeIdentifier identifier
    | typeIdentifier identifier ',' argsList
    ;

expression
   : literalExpression                                                              # LiteralExpression_
   | 'print' '(' expression ')'                                                     # PrintExpression // Temporary
   | identifier '(' params=expressionList? ')'                                      # FunctionCallExpression
   | identifier                                                                     # IdentifierExpression
   | 'while' condition=expression blockExpression                                   # WhileExpression
// TODO: Add support for breaking out of loops.
//   | 'break' expression?                                                            # BreakExpression
   | blockExpression                                                                # BlockExpression_
   | '(' expression ')'                                                             # GroupedExpression 
   | expression op=('--' | '++')                                                    # PostfixUnaryExpression
   | op=('-' | 'not' | '--' | '++') expression                                      # PrefixUnaryExpression
   | left=expression op=('*' | '/' | '%') right=expression                          # BinaryExpression
   | left=expression op=('+' | '-') right=expression                                # BinaryExpression
   | left=expression op=('>>' | '<<') right=expression                              # BinaryExpression
   | left=expression op='&' right=expression                                        # BinaryExpression
   | left=expression op='^' right=expression                                        # BinaryExpression
   | left=expression op='|' right=expression                                        # BinaryExpression
   | left=expression op=('==' | '!=' | '<' | '<=' | '>' | '>=') right=expression    # BinaryExpression
   | left=expression op='and' right=expression                                      # BinaryExpression
   | left=expression op='or' right=expression                                       # BinaryExpression
   | type=typeIdentifier identifier '=' expression                                  # VariableDeclarationExpression
   | identifier '=' expression                                                      # AssignmentExpression
   | ifExpression                                                                   # IfExpression_
   | expression ';'                                                                 # GroupedExpression
   ;

expressionList
   : expression
   | expression ',' expressionList
   ;

ifExpression
   : 'if' condition=expression block=blockExpression
    ('else' elseBlock=blockExpression)?
 // TODO: Add support for else if
 // ('else' (elseBlock=blockExpression | elseif=ifExpression))
   ;

identifier : IDENTIFIER;

typeIdentifier 
    : 'int' '?'?
    | 'bool' '?'?
    ;

literalExpression
   : INTEGER_LITERAL
   | BOOLEAN_LITERAL
   | NONE_LITERAL
   ;

NONE_LITERAL
    : 'None'
    ;

INTEGER_LITERAL 
    : DEC_LITERAL 
    ;

DEC_LITERAL: DecimalDigit (DecimalDigit | '_')*;

BOOLEAN_LITERAL
    : 'true'
    | 'false'
    ;

IDENTIFIER: Letter LetterOrDigit*;

fragment DecimalDigit : [0-9] ;
fragment Letter : [a-zA-Z_];
fragment LetterOrDigit: Letter | DecimalDigit;

WS : [ \t\r\n]+ -> skip ; // skip spaces, tabs, newlines
