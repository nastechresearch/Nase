import { describe, expect, it } from "vitest";

import { WIRE_OPS } from "@nase/shared";

import { MockPhone } from "./mock-phone.ts";
import type { AppMessage } from "./session.ts";

describe("MockPhone wire surface", () => {
  it("answers every wire op the descriptor declares", async () => {
    for (const op of WIRE_OPS) {
      const phone = new MockPhone();
      const message: AppMessage = { id: `wire-${op}`, kind: "req", op, params: { timeoutMs: 0 } };
      const result = await phone.handle(message);
      expect(result.error, `MockPhone has no handler for wire op "${op}"`).not.toBe(`unknown_op:${op}`);
    }
  });

  it("acknowledges cancel with the reference carried in result", async () => {
    const phone = new MockPhone();
    const message: AppMessage = { id: "cancel-1", kind: "evt", op: "cancel", result: { ref: "req-9" } };
    expect(await phone.handle(message)).toEqual({ ok: true, result: { cancelled: true, ref: "req-9" } });
  });
});
