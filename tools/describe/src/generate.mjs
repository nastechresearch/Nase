#!/usr/bin/env node
// Emits every machine-readable artifact from descriptors/*.yaml.
// Usage: node tools/describe/src/generate.mjs [--check]
//   --check  regenerate in memory and fail if the committed output differs.
//
// Generated files are NO-EDIT: edit the descriptor instead, then rerun.

import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const check = process.argv.includes("--check");

const SURFACES = ["wire", "mcp", "kotlin", "cli"];
const HIDDEN = new Set(["trusted", "bounces", "unknown"]);
const PACKAGE_RE = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/;
const BANNER = "GENERATED FILE — DO NOT EDIT. Source: descriptors/capabilities.yaml. Regenerate: npm run describe";

const problems = [];
const fail = (where, message) => problems.push(`${where}: ${message}`);

function readDescriptor(name) {
  const path = join(ROOT, "descriptors", name);
  try {
    return parse(readFileSync(path, "utf8"));
  } catch (error) {
    fail(`descriptors/${name}`, `cannot parse: ${error.message}`);
    return null;
  }
}

function validateCapabilities(doc) {
  if (!doc || !Array.isArray(doc.ops)) {
    fail("capabilities.yaml", "missing `ops:` list");
    return [];
  }
  if (doc.version !== 1) fail("capabilities.yaml", "`version` must be 1");

  const ids = new Set();
  const seen = Object.fromEntries(SURFACES.map((s) => [s, new Map()]));

  for (const [i, op] of doc.ops.entries()) {
    const where = `capabilities.yaml ops[${i}]`;
    if (!op || typeof op.id !== "string" || !op.id) { fail(where, "missing string `id`"); continue; }
    if (ids.has(op.id)) fail(where, `duplicate id "${op.id}"`);
    ids.add(op.id);
    if (typeof op.summary !== "string" || !op.summary.trim()) fail(where, "missing `summary`");

    const surfaces = SURFACES.filter((s) => op[s] !== undefined);
    if (surfaces.length === 0) fail(where, `op "${op.id}" is on no surface`);

    for (const surface of surfaces) {
      const value = op[surface];
      const names = Array.isArray(value) ? value : [value];
      if (Array.isArray(value) && value.length === 0) fail(where, `empty ${surface} list`);
      for (const name of names) {
        if (typeof name !== "string" || !/^[a-z][a-z0-9_]*$/.test(name)) {
          fail(where, `${surface} name ${JSON.stringify(name)} is not snake_case`);
          continue;
        }
        const owner = seen[surface].get(name);
        if (owner && owner !== op.id) fail(where, `${surface} name "${name}" already used by "${owner}"`);
        seen[surface].set(name, op.id);
      }
    }

    if (op.params !== undefined) {
      if (!Array.isArray(op.params)) fail(where, "`params` must be a list");
      else {
        const names = new Set();
        for (const p of op.params) {
          if (!p || typeof p.name !== "string" || !p.name) { fail(where, "param missing `name`"); continue; }
          if (names.has(p.name)) fail(where, `duplicate param "${p.name}"`);
          names.add(p.name);
          if (typeof p.type !== "string") fail(where, `param "${p.name}" missing a string type`);
          if (typeof p.required !== "boolean") fail(where, `param "${p.name}" missing a boolean required`);
        }
      }
    }
  }
  return doc.ops;
}

function validateApps(doc) {
  if (!doc || !Array.isArray(doc.apps)) {
    fail("apps.yaml", "missing `apps:` list");
    return [];
  }
  if (doc.schema !== 1) fail("apps.yaml", "`schema` must be 1");

  const packages = new Set();
  for (const [i, app] of doc.apps.entries()) {
    const where = `apps.yaml apps[${i}]`;
    if (!app || typeof app.package !== "string" || !PACKAGE_RE.test(app.package)) {
      fail(where, `invalid package ${JSON.stringify(app && app.package)}`);
      continue;
    }
    if (packages.has(app.package)) fail(where, `duplicate package "${app.package}"`);
    packages.add(app.package);
    if (typeof app.label !== "string" || !app.label.trim()) fail(where, "missing `label`");
    if (app.hiddenDisplay !== undefined && !HIDDEN.has(app.hiddenDisplay)) {
      fail(where, `hiddenDisplay must be one of ${[...HIDDEN].join(", ")}`);
    }
    if (app.aliases !== undefined) {
      if (!Array.isArray(app.aliases) || app.aliases.some((a) => typeof a !== "string" || !a.trim())) {
        fail(where, "`aliases` must be a list of non-empty strings");
      }
    }
    if (app.safetyTags !== undefined && !Array.isArray(app.safetyTags)) {
      fail(where, "`safetyTags` must be a list");
    }
  }
  return doc.apps;
}

const tsString = (value) => JSON.stringify(value);
const ktList = (values) => values.map((v) => `        "${v}",`).join("\n");

function emitOps(ops) {
  const wire = ops.filter((op) => op.wire).map((op) => op.wire).sort();
  const kotlin = ops.filter((op) => op.kotlin).map((op) => op.kotlin).sort();
  const mcp = ops.filter((op) => op.mcp).map((op) => op.mcp).sort();

  const specs = ops
    .map((op) => {
      const fields = [`    id: ${tsString(op.id)}`, `    summary: ${tsString(op.summary)}`];
      for (const surface of SURFACES) {
        if (op[surface] === undefined) continue;
        fields.push(`    ${surface}: ${tsString(op[surface])}`);
      }
      return `  {\n${fields.join(",\n")},\n  },`;
    })
    .join("\n");

  return `// ${BANNER}
import type { CommandOp } from "../protocol.ts";

/** Wire op names declared in the descriptor. */
export type GeneratedWireOp =
${wire.map((name) => `  | ${tsString(name)}`).join("\n")};

export interface OpSpec {
  readonly id: string;
  readonly summary: string;
  readonly wire?: GeneratedWireOp;
  readonly mcp?: string;
  readonly kotlin?: string;
  readonly cli?: readonly string[];
}

export const OPS: readonly OpSpec[] = [
${specs}
];

export const WIRE_OPS: readonly GeneratedWireOp[] = [
${wire.map((name) => `  ${tsString(name)},`).join("\n")}
];

export const KOTLIN_TOOLS: readonly string[] = [
${kotlin.map((name) => `  ${tsString(name)},`).join("\n")}
];

export const MCP_TOOLS: readonly string[] = [
${mcp.map((name) => `  ${tsString(name)},`).join("\n")}
];

// Compile-time drift gate: the descriptor must list exactly the wire ops that
// shared/src/protocol.ts accepts. If this line errors, the two disagree.
type Equal<A, B> = (<T>() => T extends A ? 1 : 2) extends (<T>() => T extends B ? 1 : 2) ? true : false;
const _wireOpsMatchProtocol: Equal<GeneratedWireOp, CommandOp> = true;
void _wireOpsMatchProtocol;
`;
}

function emitKotlin(ops) {
  const wire = ops.filter((op) => op.wire).map((op) => op.wire).sort();
  const tools = ops.filter((op) => op.kotlin).map((op) => op.kotlin).sort();
  return `// ${BANNER}
package app.nase.companion.brain

/** Capability names generated from descriptors/capabilities.yaml. */
object GeneratedOps {
    val wire: List<String> =
        listOf(
${ktList(wire)}
        )

    val kotlinTools: List<String> =
        listOf(
${ktList(tools)}
        )
}
`;
}

function emitSeed(apps) {
  const seed = {
    schema: 1,
    apps: apps.map((app) => {
      const entry = { package: app.package, label: app.label, aliases: app.aliases ?? [] };
      if (app.deeplinks) entry.deeplinks = app.deeplinks;
      entry.hiddenDisplay = app.hiddenDisplay ?? "unknown";
      if (app.safetyTags) entry.safetyTags = app.safetyTags;
      return entry;
    }),
  };
  return `${JSON.stringify(seed, null, 2)}\n`;
}

function emitDocs(ops, apps) {
  const cell = (value) => (value === undefined ? "—" : Array.isArray(value) ? value.join(", ") : value);
  const rows = ops
    .map((op) => `| \`${op.id}\` | ${cell(op.wire)} | ${cell(op.mcp)} | ${cell(op.kotlin)} | ${cell(op.cli)} | ${op.summary} |`)
    .join("\n");
  const appRows = apps
    .map((app) => `| \`${app.package}\` | ${app.label} | ${(app.aliases ?? []).join(", ")} | ${app.hiddenDisplay ?? "unknown"} |`)
    .join("\n");

  return `<!-- ${BANNER} -->

# Capabilities

| id | wire | mcp | kotlin | cli | summary |
| --- | --- | --- | --- | --- | --- |
${rows}

# App seed

| package | label | aliases | hidden display |
| --- | --- | --- | --- |
${appRows}
`;
}

// --- build ---
const capabilities = readDescriptor("capabilities.yaml");
const appsDoc = readDescriptor("apps.yaml");
const ops = capabilities ? validateCapabilities(capabilities) : [];
const apps = appsDoc ? validateApps(appsDoc) : [];

if (problems.length > 0) {
  for (const problem of problems) console.error(`describe: ${problem}`);
  console.error(`\ndescribe: ${problems.length} problem(s) in descriptors; nothing written.`);
  process.exit(1);
}

const outputs = new Map([
  ["shared/src/generated/ops.ts", emitOps(ops)],
  ["android/app/src/main/java/app/nase/companion/brain/GeneratedOps.kt", emitKotlin(ops)],
  ["android/app/src/main/assets/apps_seed.json", emitSeed(apps)],
  ["docs/generated/capabilities.md", emitDocs(ops, apps)],
]);

let drifted = 0;
for (const [relPath, content] of outputs) {
  const path = join(ROOT, relPath);
  if (check) {
    const current = existsSync(path) ? readFileSync(path, "utf8") : "";
    if (current !== content) {
      drifted += 1;
      const expected = content.split("\n");
      const actual = current.split("\n");
      const line = expected.findIndex((value, index) => value !== actual[index]);
      console.error(`describe: drift in ${relPath} (first difference at line ${line + 1})`);
      if (line >= 0) {
        console.error(`  expected: ${expected[line]}`);
        console.error(`  actual:   ${actual[line]}`);
      }
    }
    continue;
  }
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
  console.log(`describe: wrote ${relative(ROOT, path)}`);
}

if (check) {
  if (drifted > 0) {
    console.error(`\ndescribe: ${drifted} file(s) out of date. Run: npm run describe`);
    process.exit(1);
  }
  console.log(`describe: ${outputs.size} generated file(s) up to date.`);
}
