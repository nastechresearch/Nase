#!/usr/bin/env node
// Fails when a credential-shaped string is committed.
// Usage: node tools/describe/src/secrets.mjs
//
// Deliberately narrow: only well-known credential prefixes and private-key
// blocks. A broad "any long string after the word token" rule flags fixtures
// and docs, which trains people to ignore the gate.

import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const SKIP_DIRS = new Set([".git", "node_modules", "build", ".gradle", ".idea", "dist", "coverage"]);
const SKIP_FILES = new Set(["package-lock.json", "secrets.mjs"]);
const TEXT_EXT = /\.(md|ya?ml|json|ts|tsx|js|mjs|cjs|kt|kts|xml|toml|sh|txt|example|properties|gradle)$/i;

const PATTERNS = [
  [/-----BEGIN [A-Z ]*PRIVATE KEY-----/, "private key block"],
  [/\bAKIA[0-9A-Z]{16}\b/, "AWS access key id"],
  [/\bgh[pousr]_[A-Za-z0-9]{36,}\b/, "GitHub token"],
  [/\bgithub_pat_[A-Za-z0-9_]{40,}\b/, "GitHub fine-grained token"],
  [/\bsk-[A-Za-z0-9]{32,}\b/, "OpenAI-style secret key"],
  [/\bxai-[A-Za-z0-9]{20,}\b/, "xAI key"],
  [/\bAIza[0-9A-Za-z_-]{35}\b/, "Google API key"],
  [/\bsk-ant-[A-Za-z0-9_-]{20,}\b/, "Anthropic key"],
  [/\b(?:CSC_LINK|CSC_KEY_PASSWORD|APPLE_API_KEY|AZURE_SIGN_[A-Z_]+)\s*[:=]\s*["']?[A-Za-z0-9+/=_-]{20,}/, "signing credential value"],
];

// A real credential never advertises itself. Test fixtures do, deliberately:
// "sk-ant-PLAINTEXT-secret-1111" exists so the keystore path can be exercised
// without a secret. Ignoring these markers keeps the gate meaningful.
const FAKE_MARKER = /PLAINTEXT|EXAMPLE|DUMMY|FAKE|REDACTED|CHANGEME|NOTAREALKEY|xxxx/i;

const findings = [];
for (const path of walk(ROOT)) {
  if (!TEXT_EXT.test(path)) continue;
  const rel = relative(ROOT, path);
  if (SKIP_FILES.has(rel.split("/").pop())) continue;
  let text;
  try {
    text = readFileSync(path, "utf8");
  } catch {
    continue;
  }
  const lines = text.split("\n");
  for (const [pattern, label] of PATTERNS) {
    for (const [index, line] of lines.entries()) {
      const match = line.match(pattern);
      if (match && !FAKE_MARKER.test(match[0])) findings.push(`${rel}:${index + 1}: ${label}`);
    }
  }
}

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRS.has(entry)) continue;
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) yield* walk(path);
    else if (stat.isFile() && stat.size < 2_000_000) yield path;
  }
}

if (findings.length > 0) {
  for (const finding of findings) console.error(`secrets: ${finding}`);
  console.error(`\nsecrets: ${findings.length} credential-shaped string(s). Remove them; never commit a key.`);
  process.exit(1);
}
console.log("secrets: none found.");
