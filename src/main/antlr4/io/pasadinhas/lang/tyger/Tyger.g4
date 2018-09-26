grammar Tyger;

prog                    :   declarations+
                        ;

declarations
    :   functionDeclaration
    |   variableDeclaration
    ;

variableDeclaration
    :   'let' Identifier '=' expr
    ;

expr                    :   functionDeclaration
                        |   typeDeclaration
                        |   variableDeclaration
                        |   funcCall
                        |   ifExpr
                        |   block
                        |   literal
                        |   Identifier
                        |   expr ';'
                        |   expr binaryOperator expr
                        |   '(' expr ')'
                        ;

funcCall                :   Identifier '(' funcCallArgs ')'
                        ;

funcCallArgs            :   (expr (',' expr)*)?
                        ;

assignment
    :   Identifier '=' expr
    ;

ifExpr                  :   'if' expr 'then' expr ('else' expr)?
                        ;

typeDeclaration         :   'type' Identifier '{' (functionDeclaration | fieldDeclaration)* '}'
                        ;

fieldDeclaration        :   'let' Identifier expressionTypeHint? ('=' expr)? ';'?
                        ;

block                   :   '{' (expr)+ '}'
                        ;

expressionTypeHint
    :   ':' primitiveType
    // |   ':' referenceType
    ;

binaryOperator
    :   '+'
    |   '-'
    |   '*'
    |   '/'
    |   '<'
    |   '<='
    |   '>'
    |   '>='
    |   '=='
    |   '!='
    ;

primitiveType
    :   'int'
    |   'char'
    |   'bool'
    |   'long'
    |   'double'
    ;

/**************************************************************
 ** Function Declarations
 **************************************************************/

functionDeclaration
    :   'func' name=Identifier '(' functionFormalParameters? ')' expressionTypeHint? functionBody
    ;

functionFormalParameters
    :   functionFormalParameter (',' functionFormalParameter)*
    ;

functionFormalParameter
    :   name=Identifier expressionTypeHint?
    ;

functionBody
    :   block
    |   '=' expr
    ;

/**************************************************************
 ** Lexical Structure
 **************************************************************/

literal
	:	IntegerLiteral
	|	FloatingPointLiteral
	|	BooleanLiteral
	|	CharacterLiteral
	|	StringLiteral
	;

/**************************************************************
 ** Keywords
 **************************************************************/

// ABSTRACT : 'abstract';
// ASSERT : 'assert';
BOOLEAN : 'boolean';
BREAK : 'break';
BYTE : 'byte';
CASE : 'case';
CATCH : 'catch';
CHAR : 'char';
CLASS : 'class';
CONST : 'const';
CONTINUE : 'continue';
DEFAULT : 'default';
DO : 'do';
DOUBLE : 'double';
ELSE : 'else';
ENUM : 'enum';
EXTENDS : 'extends';
FINAL : 'final';
FINALLY : 'finally';
FLOAT : 'float';
FOR : 'for';
IF : 'if';
GOTO : 'goto';
IMPLEMENTS : 'implements';
IMPORT : 'import';
INSTANCEOF : 'instanceof';
INT : 'int';
INTERFACE : 'interface';
LONG : 'long';
NATIVE : 'native';
NEW : 'new';
PACKAGE : 'package';
PRIVATE : 'private';
PROTECTED : 'protected';
PUBLIC : 'public';
RETURN : 'return';
SHORT : 'short';
STATIC : 'static';
STRICTFP : 'strictfp';
SUPER : 'super';
SWITCH : 'switch';
SYNCHRONIZED : 'synchronized';
THIS : 'this';
THROW : 'throw';
THROWS : 'throws';
TRANSIENT : 'transient';
TRY : 'try';
VOID : 'void';
VOLATILE : 'volatile';
WHILE : 'while';

/**************************************************************
 ** Integer Literals
 **************************************************************/

IntegerLiteral
	:	DecimalIntegerLiteral
	;

fragment
DecimalIntegerLiteral
	:	DecimalNumeral IntegerTypeSuffix?
	;

fragment
IntegerTypeSuffix
	:	[lL]
	;

fragment
DecimalNumeral
	:	'0'
	|	NonZeroDigit (Digits? | Underscores Digits)
	;

fragment
Digits
	:	Digit (DigitsAndUnderscores? Digit)?
	;

fragment
Digit
	:	'0'
	|	NonZeroDigit
	;

fragment
NonZeroDigit
	:	[1-9]
	;

fragment
DigitsAndUnderscores
	:	DigitOrUnderscore+
	;

fragment
DigitOrUnderscore
	:	Digit
	|	'_'
	;

fragment
Underscores
	:	'_'+
	;

/**************************************************************
 ** Floating-Point Literals
 **************************************************************/

FloatingPointLiteral
	:	Digits '.' Digits? FloatTypeSuffix?
    |	'.' Digits FloatTypeSuffix?
    |	Digits FloatTypeSuffix
    ;

fragment
FloatTypeSuffix
	:	[fFdD]
	;

/**************************************************************
 ** Boolean Literals
 **************************************************************/

BooleanLiteral
	:	'true'
	|   'True'
	|	'false'
	|   'False'
	;

/**************************************************************
 ** Character Literals
 **************************************************************/

CharacterLiteral
	:	'\'' SingleCharacter '\''
	|	'\'' EscapeSequence '\''
	;

fragment
SingleCharacter
	:	~['\\\r\n]
	;

/**************************************************************
 ** String Literals
 **************************************************************/

StringLiteral
	:	'"' StringCharacters? '"'
	;

fragment
StringCharacters
	:	StringCharacter+
	;

fragment
StringCharacter
	:	~["\\\r\n]
	|	EscapeSequence
	;

/**************************************************************
 ** Escape Sequences for Character and String Literals
 **************************************************************/

fragment
EscapeSequence
	:	'\\' [btnfr"'\\]
	;

/**************************************************************
 ** Separators
 **************************************************************/

LPAREN : '(';
RPAREN : ')';
LBRACE : '{';
RBRACE : '}';
LBRACK : '[';
RBRACK : ']';
SEMI : ';';
COMMA : ',';
DOT : '.';

/**************************************************************
 ** Operators
 **************************************************************/
ASSIGN : '=';
GT : '>';
LT : '<';
BANG : '!';
TILDE : '~';
QUESTION : '?';
COLON : ':';
EQUAL : '==';
LE : '<=';
GE : '>=';
NOTEQUAL : '!=';
AND : '&&';
OR : '||';
INC : '++';
DEC : '--';
ADD : '+';
SUB : '-';
MUL : '*';
DIV : '/';
BITAND : '&';
BITOR : '|';
CARET : '^';
MOD : '%';
ARROW : '->';
COLONCOLON : '::';

ADD_ASSIGN : '+=';
SUB_ASSIGN : '-=';
MUL_ASSIGN : '*=';
DIV_ASSIGN : '/=';
AND_ASSIGN : '&=';
OR_ASSIGN : '|=';
XOR_ASSIGN : '^=';
MOD_ASSIGN : '%=';
LSHIFT_ASSIGN : '<<=';
RSHIFT_ASSIGN : '>>=';
URSHIFT_ASSIGN : '>>>=';

/**************************************************************
 ** Identifiers
 **************************************************************/

Identifier
	:	Letter LetterOrDigit*
	;

fragment
Letter
	:	[a-zA-Z$_]
	;

fragment
LetterOrDigit
	:	[a-zA-Z0-9_]
	;

/**************************************************************
 ** Whitespace and Comments
 **************************************************************/

WS
    :   [ \t\r\n\u000C]+ -> skip
    ;

COMMENT
    :   '/*' .*? '*/' -> skip
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> skip
    ;
