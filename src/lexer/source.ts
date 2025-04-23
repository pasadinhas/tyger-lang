import { assert } from "../assert.ts";

export class Source {
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
