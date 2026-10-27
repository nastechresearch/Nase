// GENERATED FILE — DO NOT EDIT. Source: descriptors/capabilities.yaml. Regenerate: npm run describe
import type { CommandOp } from "../protocol.ts";

/** Wire op names declared in the descriptor. */
export type GeneratedWireOp =
  | "ask_user"
  | "cancel"
  | "confirm"
  | "disconnect"
  | "done"
  | "drag"
  | "info"
  | "long_press"
  | "open_app"
  | "open_settings"
  | "pinch"
  | "ping"
  | "press"
  | "screenshot"
  | "speak"
  | "swipe"
  | "tap"
  | "type"
  | "ui_tree"
  | "wait_for_request"
  | "wait_idle";

export interface OpSpec {
  readonly id: string;
  readonly summary: string;
  readonly wire?: GeneratedWireOp;
  readonly mcp?: string;
  readonly kotlin?: string;
  readonly cli?: readonly string[];
}

export const OPS: readonly OpSpec[] = [
  {
    id: "screenshot",
    summary: "Capture the screen as a JPEG and return its size.",
    wire: "screenshot",
    mcp: "screenshot",
    cli: ["screenshot"],
  },
  {
    id: "ui_tree",
    summary: "Read the accessibility tree of the current screen.",
    wire: "ui_tree",
    mcp: "ui_tree",
    cli: ["ui_tree","tree"],
  },
  {
    id: "wait_idle",
    summary: "Wait for the screen to stop changing instead of guessing.",
    wire: "wait_idle",
    mcp: "wait_idle",
    cli: ["wait_idle","wait"],
  },
  {
    id: "tap",
    summary: "Tap at screen coordinates, preferring a labelled target.",
    wire: "tap",
    mcp: "tap",
    kotlin: "tap",
    cli: ["tap"],
  },
  {
    id: "swipe",
    summary: "Swipe between two points, optionally with a duration.",
    wire: "swipe",
    mcp: "swipe",
    kotlin: "swipe",
    cli: ["swipe"],
  },
  {
    id: "long_press",
    summary: "Press and hold a point for a duration.",
    wire: "long_press",
    mcp: "long_press",
    kotlin: "long_press",
    cli: ["long_press","longpress"],
  },
  {
    id: "drag",
    summary: "Drag from one point to another, slowly enough to register.",
    wire: "drag",
    mcp: "drag",
    kotlin: "drag",
    cli: ["drag"],
  },
  {
    id: "pinch",
    summary: "Two-finger pinch to zoom, from one span to another.",
    wire: "pinch",
    mcp: "pinch",
    kotlin: "pinch",
    cli: ["pinch"],
  },
  {
    id: "type",
    summary: "Type into the focused field; replace by default, append on request. Password fields are refused.",
    wire: "type",
    mcp: "type",
    kotlin: "type",
    cli: ["type"],
  },
  {
    id: "press",
    summary: "Press a navigation or submit key.",
    wire: "press",
    mcp: "key",
    kotlin: "key",
    cli: ["press"],
  },
  {
    id: "open_app",
    summary: "Open an installed app by package name or by name; the phone resolves names locally.",
    wire: "open_app",
    mcp: "open_app",
    kotlin: "open_app",
    cli: ["open_app","open"],
  },
  {
    id: "open_settings",
    summary: "Jump straight to a settings screen instead of tapping through menus.",
    wire: "open_settings",
    mcp: "open_settings",
    kotlin: "open_settings",
    cli: ["open_settings","settings"],
  },
  {
    id: "speak",
    summary: "Say something on the phone.",
    wire: "speak",
    mcp: "speak",
    kotlin: "speak",
    cli: ["speak"],
  },
  {
    id: "ask_user",
    summary: "Ask the owner a question and wait for the answer.",
    wire: "ask_user",
    mcp: "ask_user",
    kotlin: "ask",
    cli: ["ask_user","ask"],
  },
  {
    id: "confirm",
    summary: "Request the owner's confirmation for a risky action.",
    wire: "confirm",
    mcp: "confirm",
  },
  {
    id: "done",
    summary: "Finish the task and report whether it succeeded.",
    wire: "done",
    mcp: "done",
    kotlin: "done",
  },
  {
    id: "wait_for_request",
    summary: "Long-poll for the next request from the owner.",
    wire: "wait_for_request",
    mcp: "wait_for_request",
  },
  {
    id: "ping",
    summary: "Liveness check.",
    wire: "ping",
    cli: ["ping"],
  },
  {
    id: "info",
    summary: "Report app name, version and features.",
    wire: "info",
  },
  {
    id: "disconnect",
    summary: "End the session.",
    wire: "disconnect",
    mcp: "disconnect",
  },
  {
    id: "cancel",
    summary: "Cancel the current operation.",
    wire: "cancel",
  },
  {
    id: "remember",
    summary: "Store a fact in the phone-side memory.",
    kotlin: "remember",
  },
  {
    id: "forget",
    summary: "Delete a stored fact.",
    kotlin: "forget",
  },
  {
    id: "recall_text",
    summary: "Read back previously copied text.",
    kotlin: "recall_text",
  },
  {
    id: "copy_text",
    summary: "Copy text to the phone clipboard.",
    kotlin: "copy_text",
  },
  {
    id: "schedule_task",
    summary: "Schedule a task to run later on the phone.",
    kotlin: "schedule_task",
  },
  {
    id: "pair",
    summary: "Pair the MCP host with a phone using a one-time code.",
    mcp: "pair",
  },
  {
    id: "status",
    summary: "Report connection, display and brain status.",
    mcp: "status",
  },
];

export const WIRE_OPS: readonly GeneratedWireOp[] = [
  "ask_user",
  "cancel",
  "confirm",
  "disconnect",
  "done",
  "drag",
  "info",
  "long_press",
  "open_app",
  "open_settings",
  "pinch",
  "ping",
  "press",
  "screenshot",
  "speak",
  "swipe",
  "tap",
  "type",
  "ui_tree",
  "wait_for_request",
  "wait_idle",
];

export const KOTLIN_TOOLS: readonly string[] = [
  "ask",
  "copy_text",
  "done",
  "drag",
  "forget",
  "key",
  "long_press",
  "open_app",
  "open_settings",
  "pinch",
  "recall_text",
  "remember",
  "schedule_task",
  "speak",
  "swipe",
  "tap",
  "type",
];

export const MCP_TOOLS: readonly string[] = [
  "ask_user",
  "confirm",
  "disconnect",
  "done",
  "drag",
  "key",
  "long_press",
  "open_app",
  "open_settings",
  "pair",
  "pinch",
  "screenshot",
  "speak",
  "status",
  "swipe",
  "tap",
  "type",
  "ui_tree",
  "wait_for_request",
  "wait_idle",
];

// Compile-time drift gate: the descriptor must list exactly the wire ops that
// shared/src/protocol.ts accepts. If this line errors, the two disagree.
type Equal<A, B> = (<T>() => T extends A ? 1 : 2) extends (<T>() => T extends B ? 1 : 2) ? true : false;
const _wireOpsMatchProtocol: Equal<GeneratedWireOp, CommandOp> = true;
void _wireOpsMatchProtocol;
