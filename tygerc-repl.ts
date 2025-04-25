#!/usr/bin/env -S node --no-warnings=ExperimentalWarning

import readline from "readline";
import { lex } from "./src/lexer/lexer.ts";
import { parse } from "./src/parser/parser.ts";
import { evaluate } from "./src/interpreter/interpreter.ts";
import util from "util";

// change default depth of objects in console.log
util.inspect.defaultOptions.depth = 10;

type Mode = "lexer" | "parser" | "interpreter";
let mode: Mode = "interpreter";

const handlers: Record<Mode, (string) => any> = {
  lexer: (line) => lex(line),
  parser: (line) => parse(lex(line)),
  interpreter: (line) => evaluate(parse(lex(line))),
};

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  prompt: "> ",
});

console.log("Tyger REPL v.0.0.1");
console.log("Use :mode [lexer|parser] to switch.");

rl.prompt();
rl.on("line", (line) => {
  const trimmed = line.trim();

  if (trimmed.startsWith(":mode")) {
    const [, newMode] = trimmed.split(" ");
    if (newMode === "lexer" || newMode === "parser") {
      mode = newMode;
      console.log(`Mode switched to: ${mode}`);
    } else {
      console.log("Unknown mode. Available modes: lexer, parser");
    }
  } else {
    try {
      console.log(handlers[mode](line));
    } catch (err) {
      console.error("Error:", err);
    }
  }

  rl.prompt();
}).on("close", () => {
  console.log("Good-bye!");
  process.exit(0);
});
