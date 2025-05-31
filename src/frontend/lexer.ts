import { assert } from "../assert.ts";

export type TokenType =
  | "let"
  | "mut"
  | "true"
  | "false"
  | ">"
  | ">="
  | "=="
  | "<="
  | "<"
  | "!="
  | "+="
  | "-="
  | "*="
  | "/="
  | "%="
  | "="
  | "+"
  | "-"
  | "*"
  | "/"
  | "%"
  | "("
  | ")"
  | ";"
  | "Identifier"
  | "Number"
  | "EOF";

export interface Token {
  type: TokenType;
  value: string;
}

export function token(
  type: TokenType,
  value: string = type
): Token {
  return { type, value };
}

class Source {
  private source: string;
  private current = 0;

  constructor(source: string) {
    this.source = source;
  }

  peek(offset: number = 0): string {
    return this.source[this.current + offset] ?? "\0";
  }

  advance(n: number): boolean {
    assert(n > 0, "Must advance at least one character");
    this.current += n;
    return true;
  }

  take(n: number): string {
    this.advance(n);
    return this.source.slice(this.current - n, this.current);
  }

  match(expected: string): boolean {
    for (let i = 0; i < expected.length; ++i) {
      if (this.source[this.current + i] !== expected[i]) {
        return false;
      }
    }
    return true;
  }

  position(): number {
    return this.current;
  }
}

type Matcher = (src: Source) => Token | false;

function isIdentifierChar(char: string): boolean {
  assert(char.length === 1, "character string must have exactly length one.");

  const code = char.charCodeAt(0);

  return (
    (code >= 65 && code <= 90) || // A-Z
    (code >= 97 && code <= 122) || // a-z
    (code >= 48 && code <= 57) || // 0-9
    char === "_"
  );
}

function keywordMatcher(type: TokenType, keyword: string = type): Matcher {
  return (src) =>
    src.match(keyword) &&
    !isIdentifierChar(src.peek(keyword.length)) &&
    src.advance(keyword.length) &&
    token(type, undefined);
}

function exactMatcher(type: TokenType, value: string = type): Matcher {
  return (src) =>
    src.match(value) && src.advance(value.length) && token(type, undefined);
}

function isDigit(char: string) {
  assert(char.length === 1, "character string must have exactly length one.");

  const code = char.charCodeAt(0);
  return code >= 48 && code <= 57;
}

function matchWhile(
  src: Source,
  ...predicates: ((char: string) => boolean)[]
): number {
  let offset = 0;
  for (const predicate of predicates) {
    while (predicate(src.peek(offset))) offset++;
  }
  return offset;
}

function numberMatcher(src: Source): Token | false {
  const offset = matchWhile(src, isDigit);
  return offset > 0 && token("Number", src.take(offset));
}

function identifierMatcher(src: Source): Token | false {
  const offset = matchWhile(src, isIdentifierChar);
  return offset > 0 && token("Identifier", src.take(offset));
}

const Matchers: Record<TokenType, Matcher> = {
  "let": keywordMatcher("let"),
  "mut": keywordMatcher("mut"),
  "true": keywordMatcher("true"),
  "false": keywordMatcher("false"),
  ">=": exactMatcher(">="),
  "<=": exactMatcher("<="),
  "==": exactMatcher("=="),
  "!=": exactMatcher("!="),
  "+=": exactMatcher("+="),
  "-=": exactMatcher("-="),
  "*=": exactMatcher("*="),
  "/=": exactMatcher("/="),
  "%=": exactMatcher("%="),
  ">": exactMatcher(">"),
  "<": exactMatcher("<"),
  "=": exactMatcher("="),
  "+": exactMatcher("+"),
  "-": exactMatcher("-"),
  "*": exactMatcher("*"),
  "/": exactMatcher("/"),
  "%": exactMatcher("%"),
  "(": exactMatcher("("),
  ")": exactMatcher(")"),
  ";": exactMatcher(";"),
  "Number": numberMatcher,
  "Identifier": identifierMatcher,
  "EOF": () => false, // This token is automatically emitted at the end of input.
};

function isWhiteSpace(char: string): boolean {
  assert(char.length === 1, "char must be 1 character");
  return char === " " || char === "\n" || char === "\t";
}

export function lex(source: string): Token[] {
  const src = new Source(source);
  const tokens: Token[] = [];
  while (src.peek() !== "\0") {
    let match = false;
    for (const matcher of Object.values(Matchers)) {
      const token = matcher(src);
      if (token) {
        match = true;
        tokens.push(token);
        break;
      }
    }
    if (!match) {
      if (isWhiteSpace(src.peek())) {
        src.advance(1);
        continue;
      }
      assert(
        false,
        `Invalid input: ${src.peek()} (code: ${src
          .peek()
          .charCodeAt(0)}, at: ${src.position()})`
      );
    }
  }

  tokens.push(token("EOF"));

  return tokens;
}
