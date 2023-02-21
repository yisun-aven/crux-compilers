grammar Crux;


program
 : declarationList EOF
 ;

SemiColon: ';';

Integer
 : '0'
 | [1-9] [0-9]*
 ;

True: 'true';
False: 'false';


WhiteSpaces
 : [ \t\r\n]+ -> skip
 ;

Comment
 : '//' ~[\r\n]* -> skip
 ;

AND: '&&';
OR: '||';
NOT: '!';
IF: 'if';
ELSE: 'else';
FOR: 'for';
BREAK: 'break';
RETURN: 'return';


OPEN_PAREN:	'(';
CLOSE_PAREN: ')';
OPEN_BRACE: '{';
CLOSE_BRACE: '}';
OPEN_BRACKET: '[';
CLOSE_BRACKET: ']';
ADD: '+';
SUB: '-';
MUL: '*';
DIV: '/';
GREATER_EQUAL: '>=';
LESSER_EQUAL: '<=';
NOT_EQUAL: '!=';
EQUAL: '==';
GREATER_THAN: '>';
LESS_THAN: '<';
ASSIGN: '=';
COMMA: ',';

Identifier
 : [a-zA-Z] [a-zA-Z0-9_]*
 | 'void'
 | 'bool'
 | 'int'
 ;



 type
  : Identifier
  ;

 literal
  : Integer
  | True
  | False
  ;

declarationList
 : declaration*
 ;

declaration
 : variableDeclaration
 | arrayDeclaration
 | functionDefinition
 ;

variableDeclaration: type Identifier ';' ;

op0 : GREATER_EQUAL | LESSER_EQUAL | NOT_EQUAL | EQUAL | GREATER_THAN | LESS_THAN;
op1 : ADD | SUB | OR;
op2 : MUL | DIV | AND;


designator : Identifier ('[' expression0 ']')*;

expression0 : expression1 (op0 expression1)?;
expression1 : expression2 | expression1 op1 expression2;
expression2 : expression3 | expression2 op2 expression3;
expression3 : '!' expression3 | '(' expression0 ')' | designator | callExpression | literal;
callExpression : Identifier '(' expressionList ')';
expressionList : | expression0 (',' expression0)*;

parameter : type Identifier;
parameterList : | parameter (',' parameter)*;

arrayDeclaration : type Identifier '[' Integer ']' ';';
functionDefinition : type Identifier '(' parameterList ')' statementBlock;

assignmentStatement : designator '=' expression0 ';';
assignmentStatementNoSemi : designator '=' expression0;
callStatement : callExpression ';';
ifStatement : 'if' expression0 statementBlock ('else' statementBlock)?;
forStatement : 'for' '(' assignmentStatement expression0 ';' assignmentStatementNoSemi ')' statementBlock;
breakStatement : 'break' ';';
returnStatement : 'return' expression0 ';';
statement : variableDeclaration
           | callStatement
           | assignmentStatement
           | ifStatement
           | forStatement
           | breakStatement
           | returnStatement;
statementList : statement*;
statementBlock : '{' statementList '}';

