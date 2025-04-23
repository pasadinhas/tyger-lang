import fs from "fs";
import url from "url";
import path from "path";
import { lex } from "../../src/lexer/lexer.ts";
import { token, TokenKinds } from "../../src/lexer/tokens.ts";
import type { TokenKind } from "../../src/lexer/tokens.ts";

import { expect, test } from "vitest";

function readTestResourceFile(file: string) {
  const __filename = url.fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);

  return fs.readFileSync(path.join(__dirname, "resources", file), "utf8");
}

const SingleTokenTests: Record<TokenKind, string> = {
  [TokenKinds.Let]: "let",
  [TokenKinds.Equal]: "=",
  [TokenKinds.Identifier]: "abc",
  [TokenKinds.LParen]: "(",
  [TokenKinds.RParen]: ")",
  [TokenKinds.Minus]: "-",
  [TokenKinds.Number]: "42",
  [TokenKinds.Semicolon]: ";",
  [TokenKinds.Star]: "*",
  [TokenKinds.Plus]: "+",
  [TokenKinds.Slash]: "/",
};

for (const key of Object.keys(SingleTokenTests)) {
  const kind = key as TokenKind;
  const source = SingleTokenTests[kind];

  test(`single token lex: ${kind}`, () => {
    expect(lex(source)).toStrictEqual([token(kind, source)]);
  });
}

const testFile = "test1.ty";

test(`resource file ${testFile}`, () => {
  expect(lex(readTestResourceFile(testFile))).toStrictEqual([
    { kind: TokenKinds.Let, value: "let" },
    { kind: TokenKinds.Identifier, value: "x" },
    { kind: TokenKinds.Equal, value: "=" },
    { kind: TokenKinds.LParen, value: "(" },
    { kind: TokenKinds.Number, value: "21" },
    { kind: TokenKinds.Plus, value: "+" },
    { kind: TokenKinds.Number, value: "21" },
    { kind: TokenKinds.RParen, value: ")" },
    { kind: TokenKinds.Slash, value: "/" },
    { kind: TokenKinds.Number, value: "2" },
    { kind: TokenKinds.Semicolon, value: ";" },
    { kind: TokenKinds.Let, value: "let" },
    { kind: TokenKinds.Identifier, value: "y" },
    { kind: TokenKinds.Equal, value: "=" },
    { kind: TokenKinds.Identifier, value: "x" },
    { kind: TokenKinds.Star, value: "*" },
    { kind: TokenKinds.Number, value: "2" },
    { kind: TokenKinds.Minus, value: "-" },
    { kind: TokenKinds.Number, value: "1" },
    { kind: TokenKinds.Semicolon, value: ";" },
  ]);
});
