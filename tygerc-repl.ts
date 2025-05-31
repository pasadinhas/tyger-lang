#!/usr/bin/env -S node --no-warnings=ExperimentalWarning

import readline from "readline";
import { lex } from "./src/frontend/lexer.ts";
import { parse } from "./src/frontend/parser.ts";
import { typecheck } from "./src/frontend/typechecker.ts";
import { evaluate, RuntimeScope } from "./src/interpreter/interpreter.ts";
import util from "util";
import { Types } from "./src/frontend/types.ts";

// change default depth of objects in console.log
util.inspect.defaultOptions.depth = 10;

type Mode = "lexer" | "parser" | "typecheck" | "interpreter" | "eval";
let mode: Mode = "interpreter";

const scope = new RuntimeScope();
scope.declareVariable("pi", {type: "number", value: Math.PI, mutable: false})

const globalTypeEnv = new Map();
globalTypeEnv.set("pi", Types.f64);

const handlers: Record<Mode, (string) => any> = {
  lexer: (line) => lex(line),
  parser: (line) => parse(handlers.lexer(line)),
  typecheck: (line) => typecheck(handlers.parser(line), globalTypeEnv),
  interpreter: (line) => evaluate(handlers.typecheck(line), scope),
  eval: (line) => handlers.interpreter(line),
};

const availableModes = Object.keys(handlers);

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  prompt: "> ",
});

console.log("Tyger REPL v.0.0.1");
console.log(`Use :mode [${availableModes.join("|")}] to switch.`);

rl.prompt();
rl.on("line", (line) => {
  const trimmed = line.trim();

  if (trimmed.startsWith(":mode")) {
    const [, newMode] = trimmed.split(" ");
    if (availableModes.includes(newMode)) {
      mode = newMode as Mode;
      console.log(`Mode switched to: ${mode}`);
    } else {
      console.log(`Unknown mode. Available modes: ${availableModes.join(", ")}`);
    }
  } else {
    try {
      console.log(handlers[mode](line));
    } catch (err) {
      console.error(err.message);
    }
  }

  rl.prompt();
}).on("close", () => {
  console.log("Good-bye!");
  process.exit(0);
});
