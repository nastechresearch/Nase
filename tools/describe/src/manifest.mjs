#!/usr/bin/env node
// Fails when AndroidManifest.xml names a component class that does not exist.
//
// The rebrand dropped package dots in seven component names
// (.a11yNaseAccessibilityService instead of .a11y.NaseAccessibilityService).
// Android then cannot instantiate them, so enabling the accessibility service
// silently never binds and control does not start. Nothing in the build catches
// that: the manifest compiles, the class is simply never found at runtime.

import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const MANIFEST = join(ROOT, "android/app/src/main/AndroidManifest.xml");
const SOURCE_ROOT = join(ROOT, "android/app/src/main/java");
const APP_PACKAGE = "app.nase.companion";

const manifest = readFileSync(MANIFEST, "utf8");
const declared = [...manifest.matchAll(/android:name="(\.(?:[A-Za-z0-9_]+\.)*[A-Za-z0-9_]+)"/g)]
  .map((match) => match[1])
  .filter((name) => name !== ".", )
  .sort();

const sources = [];
for (const path of walk(SOURCE_ROOT)) {
  if (!path.endsWith(".kt")) continue;
  const text = readFileSync(path, "utf8");
  const pkg = /^package\s+([\w.]+)/m.exec(text)?.[1];
  if (!pkg) continue;
  for (const cls of text.matchAll(/^\s*(?:open\s+|abstract\s+|sealed\s+|data\s+|internal\s+)*class\s+(\w+)/gm)) {
    sources.push({ pkg, cls: cls[1], path });
  }
}

const missing = [];
for (const name of new Set(declared)) {
  const parts = name.slice(1).split(".");
  const cls = parts.pop();
  const pkg = parts.length > 0 ? `${APP_PACKAGE}.${parts.join(".")}` : APP_PACKAGE;
  const found = sources.some((source) => source.pkg === pkg && source.cls === cls);
  if (!found) missing.push(`${name}  (expected ${pkg}.${cls})`);
}

if (missing.length > 0) {
  for (const entry of missing) console.error(`manifest: unresolved component ${entry}`);
  console.error(`\nmanifest: ${missing.length} component(s) cannot be created. Check for a dropped "." in the class path.`);
  process.exit(1);
}
console.log(`manifest: ${new Set(declared).size} components resolve.`);

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) yield* walk(path);
    else yield path;
  }
}
