#!/usr/bin/env node
import { parseArgs } from "node:util";

import { StdioServerTransport } from "@modelcontextprotocol/server/stdio";
import { DEFAULT_RELAY } from "@nase/shared";
import { defaultStatePath } from "@nase/client/listen";

import { createNaseMcp, listenMcp } from "./server.ts";

declare const __NASE_MCP_VERSION__: string | undefined;
const NASE_MCP_VERSION = typeof __NASE_MCP_VERSION__ === "string" ? __NASE_MCP_VERSION__ : "dev";

const { values } = parseArgs({
  options: {
    http: { type: "string" },
    relay: { type: "string" },
    client: { type: "string" },
    listen: { type: "boolean" },
    state: { type: "string" },
    help: { type: "boolean", short: "h" },
    version: { type: "boolean", short: "v" },
  },
  strict: true,
});

if (values.version) {
  console.log(NASE_MCP_VERSION);
  process.exit(0);
}

if (values.help) {
  console.log(`nase-mcp ${NASE_MCP_VERSION}: MCP server for the Nase Android app

Usage: nase-mcp [options]

Runs over stdio by default.

Options:
  --listen          keep the pairing and accept asks from the phone (NASE_LISTEN=1)
  --http <port>     serve streamable HTTP on 127.0.0.1 instead of stdio
  --relay <url>     relay URL (NASE_RELAY, default ${DEFAULT_RELAY})
  --client <name>   name shown on the phone (NASE_CLIENT_NAME)
  --state <path>    pairing file for listen mode (NASE_STATE)
  -h, --help        show this help
  -v, --version     print the version`);
  process.exit(0);
}

const relayHttp = values.relay ?? process.env.NASE_RELAY ?? DEFAULT_RELAY;
const clientName = values.client ?? process.env.NASE_CLIENT_NAME ?? "Grok Bot";
const listen = values.listen === true || process.env.NASE_LISTEN === "1";
const statePath = listen ? values.state ?? process.env.NASE_STATE ?? defaultStatePath("mcp.json") : values.state;
const options = { relayHttp, clientName, listen, statePath };

if (values.http !== undefined) {
  const port = Number(values.http);
  if (!Number.isInteger(port) || port < 0 || port > 65535) {
    console.error("--http expects a port number");
    process.exit(1);
  }
  const listening = await listenMcp({ ...options, port, host: "127.0.0.1" });
  console.error(`nase-mcp streamable HTTP on ${listening.url} (localhost only, one phone)`);
  if (listen) console.error(`Listen mode on. The pairing is kept in ${statePath}.`);
  const stop = () => void listening.close().finally(() => process.exit(0));
  process.once("SIGINT", stop);
  process.once("SIGTERM", stop);
} else {
  const { server, controller } = createNaseMcp(options);
  if (listen) console.error(`nase-mcp listen mode. The pairing is kept in ${statePath}.`);
  let stopping = false;
  const stop = () => {
    if (stopping) return;
    stopping = true;
    void controller.shutdown().finally(() => process.exit(0));
  };
  process.stdin.once("end", stop);
  process.once("SIGINT", stop);
  process.once("SIGTERM", stop);
  await server.connect(new StdioServerTransport());
}
