/*

MIT license

Author: Ken Domino, October 2023

Based on previous work of: Kazunori Sakamoto, Alexander Alexeev

*/

// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

parser grammar LuaParser;

options {
    tokenVocab = LuaLexer;
}

// Insert here @header for parser.

start_
    : chunk EOF
    ;

chunk
    : block
    ;

block
    : stat* retstat?
    ;

stat
    : ';'                                                                     # EmptyStat
    | varlist '=' explist                                                     # AssignStat
    | functioncall                                                            # CallStat
    | label                                                                   # LabelStat
    | 'break'                                                                 # BreakStat
    | 'goto' NAME                                                             # GotoStat
    | 'do' block 'end'                                                        # DoStat
    | 'while' exp 'do' block 'end'                                            # WhileStat
    | 'repeat' block 'until' exp                                              # RepeatStat
    | 'if' exp 'then' block ('elseif' exp 'then' block)* ('else' block)? 'end' # IfStat
    | 'for' NAME '=' exp ',' exp (',' exp)? 'do' block 'end'                  # NumericForStat
    | 'for' namelist 'in' explist 'do' block 'end'                            # GenericForStat
    | 'function' funcname funcbody                                            # FunctionStat
    | 'local' 'function' NAME funcbody                                        # LocalFunctionStat
    | 'local' attnamelist ('=' explist)?                                      # LocalStat
    ;

attnamelist
    : NAME attrib (',' NAME attrib)*
    ;

attrib
    : ('<' NAME '>')?
    ;

retstat
    : 'return' explist? ';'?
    ;

label
    : '::' NAME '::'
    ;

funcname
    : NAME ('.' NAME)* (':' NAME)?
    ;

varlist
    : var (',' var)*
    ;

namelist
    : NAME (',' NAME)*
    ;

explist
    : exp (',' exp)*
    ;

// Earlier alternatives bind tighter (Lua 5.4 manual, section 3.4.8).
// '^' goes before the unary operators so that -2^2 == -(2^2).
exp
    : 'nil'                                                # NilExp
    | 'false'                                              # FalseExp
    | 'true'                                               # TrueExp
    | number                                               # NumberExp
    | string                                               # StringExp
    | '...'                                                # VarargExp
    | functiondef                                          # FunctionDefExp
    | prefixexp                                            # PrefixExpExp
    | tableconstructor                                     # TableConstructorExp
    | <assoc = right> exp op = '^' exp                     # PowExp
    | op = ('not' | '#' | '-' | '~') exp                   # UnaryExp
    | exp op = ('*' | '/' | '//' | '%') exp                # MulExp
    | exp op = ('+' | '-') exp                             # AddExp
    | <assoc = right> exp op = '..' exp                    # ConcatExp
    | exp op = ('<<' | '>>') exp                           # ShiftExp
    | exp op = '&' exp                                     # BitAndExp
    | exp op = '~' exp                                     # BitXorExp
    | exp op = '|' exp                                     # BitOrExp
    | exp op = ('<' | '>' | '<=' | '>=' | '~=' | '==') exp # CompareExp
    | exp op = 'and' exp                                   # AndExp
    | exp op = 'or' exp                                    # OrExp
    ;

// prefixexp ::= var | functioncall | '(' exp ')'
// var and functioncall are inlined here so the left recursion is direct
// (ANTLR rewrites it) and the tree nests: a -> a.b -> a.b[c] -> a.b[c](x).
prefixexp
    : NAME                    # NameExp
    | '(' exp ')'             # ParenExp
    | prefixexp '[' exp ']'   # IndexExp
    | prefixexp '.' NAME      # FieldExp
    | prefixexp args          # CallExp
    | prefixexp ':' NAME args # MethodCallExp
    ;

// var ::= Name | prefixexp '[' exp ']' | prefixexp '.' Name
// Only these forms may be assigned to, so (a) = 1 stays a syntax error.
var
    : NAME                  # NameVar
    | prefixexp '[' exp ']' # IndexVar
    | prefixexp '.' NAME    # FieldVar
    ;

// functioncall ::= prefixexp args | prefixexp ':' Name args
// Only calls may be statements, so `a.b` alone stays a syntax error.
functioncall
    : prefixexp args          # PlainCall
    | prefixexp ':' NAME args # MethodCall
    ;

args
    : '(' explist? ')'
    | tableconstructor
    | string
    ;

functiondef
    : 'function' funcbody
    ;

funcbody
    : '(' parlist ')' block 'end'
    ;

/* lparser.c says "is 'parlist' not empty?"
 * That code does so by checking la(1) == ')'.
 * This means that parlist can derive empty.
 */
parlist
    : namelist (',' '...')?
    | '...'
    |
    ;

tableconstructor
    : '{' fieldlist? '}'
    ;

fieldlist
    : field (fieldsep field)* fieldsep?
    ;

field
    : '[' exp ']' '=' exp # KeyedField
    | NAME '=' exp        # NamedField
    | exp                 # PositionalField
    ;

fieldsep
    : ','
    | ';'
    ;

number
    : INT
    | HEX
    | FLOAT
    | HEX_FLOAT
    ;

string
    : NORMALSTRING
    | CHARSTRING
    | LONGSTRING
    ;