export type Type =
  | { kind: "int"; signed: boolean; bits: 8 | 16 | 32 | 64 | 128 }
  | { kind: "float"; bits: 8 | 16 | 32 | 64 | 128 }
  | { kind: "boolean" }
  | { kind: "Function"; param: Type; return: Type }
  | { kind: "TypeVar"; id: string }; // for inference, optional

export type TypeKind = Type["kind"];

export const Types: Record<string, Type> = {
  // Signed ints
  i8: { kind: "int", signed: true, bits: 8 },
  i16: { kind: "int", signed: true, bits: 16 },
  i32: { kind: "int", signed: true, bits: 32 },
  i64: { kind: "int", signed: true, bits: 64 },
  i128: { kind: "int", signed: true, bits: 128 },

  // Unsigned ints
  u8: { kind: "int", signed: false, bits: 8 },
  u16: { kind: "int", signed: false, bits: 16 },
  u32: { kind: "int", signed: false, bits: 32 },
  u64: { kind: "int", signed: false, bits: 64 },
  u128: { kind: "int", signed: false, bits: 128 },

  // Floats
  f8: { kind: "float", bits: 8 },
  f16: { kind: "float", bits: 16 },
  f32: { kind: "float", bits: 32 },
  f64: { kind: "float", bits: 64 },
  f128: { kind: "float", bits: 128 },

  // Boolean
  boolean: { kind: "boolean" } as Type,
};

export function coerceTypes(left: Type, right: Type): Type | undefined {
  if ((left.kind === "int" || left.kind === "float") && left === right) return left;

  // Float + float → wider float
  if (left.kind === "float" && right.kind === "float") {
    return left.bits >= right.bits ? left : right;
  }

  // Int + int (same signedness)
  if (left.kind === "int" && right.kind === "int" && left.signed === right.signed) {
    return left.bits >= right.bits ? left : right;
  }

  // Int + int (different signedness)
  if (left.kind === "int" && right.kind === "int" && left.signed === right.signed) {
    return undefined; // TODO: as of now we don't allow automatic coercion of different signedness ints
  }

  if ((left.kind === "float" && right.kind === "int") || (left.kind === "int" && right.kind === "float")) {
    return {
      kind: "float",
      bits: left.bits > right.bits ? left.bits : right.bits,
    };
  }
}

export function isAssignable(left: Type, right: Type): boolean {
  if (left === right) return true;
  if (left.kind === "float") {
    if (right.kind === "float" && left.bits >= right.bits) return true;
    if (right.kind === "int" && left.bits >= right.bits) return true;
  }
  if (left.kind === "int" && right.kind === "int") {
    if (left.signed === right.signed && left.bits >= right.bits) return true;
    if (left.signed === true && right.signed === false && left.bits > right.bits) return true;
  }
  return false;
}

export function typeToString(type: Type): string {
  switch (type.kind) {
    case "int":
      return `${type.signed ? "i" : "u"}${type.bits}`;
    case "float":
      return `f${type.bits}`;
    case "boolean":
      return "boolean";
    case "Function":
      return `(${typeToString(type.param)} -> ${typeToString(type.return)})`;
    case "TypeVar":
      return `'${type.id}`;
    default:
      return "unknown";
  }
}

export function typeFromString(str: string): Type {
  // Boolean
  if (str === "boolean") return Types.boolean;

  // Int or float
  const intMatch = /^(i|u)(8|16|32|64|128)$/.exec(str);
  if (intMatch) {
    const [, signed, bits] = intMatch;
    return {
      kind: "int",
      signed: signed === "i",
      bits: Number(bits) as 8 | 16 | 32 | 64 | 128,
    };
  }

  const floatMatch = /^f(8|16|32|64|128)$/.exec(str);
  if (floatMatch) {
    const [, bits] = floatMatch;
    return {
      kind: "float",
      bits: Number(bits) as 8 | 16 | 32 | 64 | 128,
    };
  }

  throw new Error(`Unknown type: ${str}`);
}