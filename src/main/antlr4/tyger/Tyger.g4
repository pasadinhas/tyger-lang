// Define a grammar called Hello
grammar Tyger;

prog : 'func' 'int' 'main' '(' ')' blockExpression EOF;

blockExpression
    : '{' expression* '}'
    ;

expression
   : literalExpression                                                              # LiteralExpression_
   | identifier                                                                     # IdentifierExpression
   | 'while' condition=expression blockExpression                                   # WhileExpression
   | blockExpression                                                                # GroupedExpression 
   | '(' expression ')'                                                             # GroupedExpression 
   | op=('-' | 'not') expression                                                    # PrefixUnaryExpression
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
   ;

ifExpression
   : 'if' condition=expression block=blockExpression
   (
      'else' (elseBlock=blockExpression | elseif=ifExpression)
   )//? else is currently mandatory, due to lack of optionals. 
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

DEC_LITERAL: DEC_DIGIT (DEC_DIGIT | '_')*;

BOOLEAN_LITERAL
    : 'true'
    | 'false'
    ;

IDENTIFIER: [a-zA-Z_]+;

fragment DEC_DIGIT : [0-9] ;

WS : [ \t\r\n]+ -> skip ; // skip spaces, tabs, newlines
