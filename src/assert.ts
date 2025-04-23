export function assert(
  condition: unknown,
  message = "<no message>"
): asserts condition {
  if (!condition) {
    throw new Error(`Assertion failed: ${message}`);
  }
}