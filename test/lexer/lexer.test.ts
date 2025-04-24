import fs from "fs";
import url from "url";
import path from "path";
import { lex } from "../../src/lexer/lexer.ts";
import { token } from "../../src/lexer/tokens.ts";
import type { TokenType } from "../../src/lexer/tokens.ts";

import { expect, test } from "vitest";

function readTestResourceFile(file: string) {
  const __filename = url.fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);

  return fs.readFileSync(path.join(__dirname, "resources", file), "utf8");
}

const SingleTokenTests: Record<TokenType, [source: string, value?: string]> = {
  let: ["let", undefined],
  "=": ["=", undefined],
  Identifier: ["abc", "abc"],
  "(": ["(", undefined],
  ")": [")", undefined],
  "-": ["-", undefined],
  Number: ["42", "42"],
  ";": [";", undefined],
  "*": ["*", undefined],
  "+": ["+", undefined],
  "/": ["/", undefined],
};

for (const key of Object.keys(SingleTokenTests)) {
  const type = key as TokenType;
  const [source, value] = SingleTokenTests[type];

  test(`single token lex: ${type}`, () => {
    expect(lex(source)).toStrictEqual([token(type, value)]);
  });
}

const testFile = "test1.ty";

test(`resource file ${testFile}`, () => {
  expect(lex(readTestResourceFile(testFile))).toStrictEqual([
    token("let"),
    token("Identifier", "x"),
    token("="),
    token("("),
    token("Number", "21"),
    token("+"),
    token("Number", "21"),
    token(")"),
    token("/"),
    token("Number", "2"),
    token(";"),
    token("let"),
    token("Identifier", "y"),
    token("="),
    token("Identifier", "x"),
    token("*"),
    token("Number", "2"),
    token("-"),
    token("Number", "1"),
    token(";"),
  ]);
});
