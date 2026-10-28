import { describe, expect, it } from "vitest";

import { MCP_TOOLS } from "@nase/shared";

import { assertDescriptorToolSurface } from "./server.ts";

describe("descriptor-driven MCP surface", () => {
  it("accepts exactly the tools the descriptor advertises on mcp:", () => {
    expect(() => assertDescriptorToolSurface(MCP_TOOLS)).not.toThrow();
  });

  it("rejects a missing tool", () => {
    const missing = MCP_TOOLS[0];
    expect(() => assertDescriptorToolSurface(MCP_TOOLS.filter((name) => name !== missing))).toThrow(
      new RegExp(`missing: ${missing}`),
    );
  });

  it("rejects a tool the descriptor does not list", () => {
    expect(() => assertDescriptorToolSurface([...MCP_TOOLS, "not_a_descriptor_tool"])).toThrow(
      /not declared on mcp: not_a_descriptor_tool/,
    );
  });

  it("rejects a tool registered twice", () => {
    expect(() => assertDescriptorToolSurface([...MCP_TOOLS, MCP_TOOLS[0]])).toThrow(
      new RegExp(`registered twice: ${MCP_TOOLS[0]}`),
    );
  });
});
