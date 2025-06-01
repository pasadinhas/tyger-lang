import fs from "fs";
import url from "url";
import path from "path";
import { expect, test } from "vitest";
import { lex, token, type TokenType } from "../src/frontend/lexer.ts";

function readTestResourceFile(file: string) {
  const __filename = url.fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);

  return fs.readFileSync(path.join(__dirname, "resources", file), "utf8");
}

const SingleTokenTests: Record<TokenType, [source: string, value?: string]> = {
  ">=": [">=", undefined],
  ">": [">", undefined],
  "<=": ["<=", undefined],
  "<": ["<", undefined],
  "==": ["==", undefined],
  "!=": ["!=", undefined],
  "+=": ["+=", undefined],
  "-=": ["-=", undefined],
  "*=": ["*=", undefined],
  "/=": ["/=", undefined],
  "%=": ["%=", undefined],
  "=": ["=", undefined],
  "(": ["(", undefined],
  ")": [")", undefined],
  "-": ["-", undefined],
  ";": [";", undefined],
  "*": ["*", undefined],
  "+": ["+", undefined],
  "/": ["/", undefined],
  "%": ["%", undefined],
  ":": [":", undefined],
  let: ["let", undefined],
  mut: ["mut", undefined],
  true: ["true", undefined],
  false: ["false", undefined],
  Identifier: ["abc", "abc"],
  Number: ["42", "42"],
  EOF: ["", undefined],
};

for (const key of Object.keys(SingleTokenTests)) {
  const type = key as TokenType;
  const [source, value] = SingleTokenTests[type];

  test(`single token lex: ${type}`, () => {
    const expectedTokens = [token(type, value)];
    if (type !== "EOF") {
      expectedTokens.push(token("EOF"));
    }
    expect(lex(source)).toStrictEqual(expectedTokens);
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
    token("EOF"),
  ]);
});
