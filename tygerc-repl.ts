#!/usr/bin/env -S node --no-warnings=ExperimentalWarning

import readline from "readline";
import { lex } from "./src/lexer/lexer.ts";

type Mode = "lexer" | "parser";
let mode: Mode = "lexer";

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
      let result;
      if (mode === "lexer") {
        result = lex(line);
      } else if (mode === "parser") {
        result = "TODO: parser not implemented";
      }
      console.log(result);
    } catch (err) {
      console.error("Error:", err);
    }
  }

  rl.prompt();
}).on("close", () => {
  console.log("Good-bye!");
  process.exit(0);
});
