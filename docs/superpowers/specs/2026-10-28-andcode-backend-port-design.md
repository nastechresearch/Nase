# AndCode backend port — design

Date: 2026-10-28. Owner decisions recorded below. Nase stays on its current theme;
nothing in `ui/design` changes.

## Decisions

1. **Port AndCode's backend wholesale** (runtime, agent adapters, session/project
   storage, streaming events), not its Compose UI.
2. **Bundle PRoot** so coding agents run on the phone without a PC. Accepted
   consequence: the distributed app carries GPL-2.0 material.
3. **Chat first.** The default surface is a streaming conversation. Device control
   is dormant until the user asks for something that needs it; it is never
   implicit.
4. **Keep the existing theme** and adapt AndCode's onboarding into it.
5. **More providers**, including OpenCode as one provider among others.

## Licensing obligations (must ship with the feature, not later)

AndCode is MIT. Its on-device runtime bundles Termux-derived binaries:

| Component | License | Obligation |
|---|---|---|
| `proot` | GPL-2.0 | Ship the GPL-2.0 text, the exact corresponding source (pinned tarball + SHA-256), and a written offer; state that it is unmodified |
| `talloc` | LGPL-3.0-or-later | Ship license text and source reference; keep it dynamically linked and replaceable |
| `libandroid-shmem` | BSD-3-Clause | Reproduce copyright and conditions |

Concrete work:

- `third_party/` directory with license texts and a `SOURCES.md` recording version,
  upstream URL, and SHA-256 for each bundled binary.
- `THIRD_PARTY_NOTICES.md` in the APK and linked from an in-app Licenses screen.
- README must stop implying the whole distribution is MIT-only: state "Nase is
  MIT; the on-device coding runtime bundles GPL-2.0 proot".
- Do not modify proot. If we ever patch it, publish the patch.

## Architecture

New package `app.nase.companion.coding`, mirroring AndCode's boundaries but wired
into Nase's existing abstractions:

| AndCode module | Nase landing | Notes |
|---|---|---|
| `runtime/LocalAgent` | `coding/LocalAgent.kt` | Drives the agent CLI inside PRoot |
| `runtime/OpenCodeBackend` | `coding/OpenCodeBackend.kt` | Same adapter, Nase process/IO conventions |
| `runtime/RuntimeRegistry` | `coding/RuntimeRegistry.kt` | One place that knows which runtimes exist |
| `runtime/FileEdit` | `coding/FileEdit.kt` | Patch application, no UI |
| `data/settings` | `coding/CodingSettings.kt` | Keys sealed with the existing `KeystoreSecretBox` |
| `data/local` + `data/repository` | `coding/CodingStore.kt` | Projects, sessions, transcripts |
| `OnboardingGate` | `voice`/`ui` onboarding step | Restyled, same steps as AndCode |

Integration points that already exist and must be reused, not duplicated:

- **Provider abstraction** (`brain/ChatClient`, `BrainRouter`, `ProviderPreset`):
  OpenCode becomes another provider. Streaming uses the same delta path the local
  brain already uses, so chat renders tokens as they arrive.
- **Descriptor layer**: coding tools (`read_file`, `write_file`, `edit_file`,
  `run_shell`, `list_dir`) are declared once in `descriptors/capabilities.yaml`,
  so they appear on the MCP and CLI surfaces automatically.
- **Safety**: every coding tool goes through `SafetyPolicy`. Writes, deletes and
  shell commands confirm by default. `AppAutonomy` stays deny-by-default, so
  "can do everything" is an explicit opt-in, not a side effect.

## Data flow

```
chat message -> BrainRouter -> OpenCodeProvider (streaming)
                                  |
                    local runtime (PRoot)  or  remote server
                                  |
                    tool calls -> descriptor ops -> SafetyPolicy -> CodingStore
                                  |
                        streamed deltas -> chat UI (existing theme)
```

Device control stays on its own path: `PhoneOps`/`ScreenRouter` are only invoked
when the user's request needs the phone, and the arm/disarm state is visible in
the chat header.

## Phases

**P1 — runtime brings-up (no UI).** PRoot bootstrap, pinned assets, manifest and
asset packaging, `LocalRuntimeStatus`, license files. Acceptance: a JVM test
verifies asset hashes; a device run starts the runtime and reports version.

**P2 — OpenCode provider with streaming.** Provider preset, auth via Keystore,
streaming deltas, error taxonomy mapped to `BrainError`. Acceptance: a mock
OpenCode server streams a reply end to end in a vitest test.

**P3 — coding tools.** Descriptor entries, executor, SafetyPolicy wiring, path
confinement to the project root. Acceptance: descriptor drift gate passes; JVM
tests cover traversal attempts and confirm-on-write.

**P4 — chat-first shell + onboarding.** Chat is default; control requires an
explicit ask; AndCode-style onboarding in the Nase theme. Acceptance: flows
documented with the `flow-mobile` IA (screens, states, exits) before code.

**P5 — capability mode, vision fallback, AI call.** "Can do everything" surfaced
from `AppAutonomy`; vision-without-vision builds a structured screen description
from the accessibility tree plus screenshot metadata; AI call streams audio and
text through `NaseVoiceInteractionService`.

## Risks

- **GPL scope.** The combined APK contains GPL-2.0. If we later want a purely MIT
  build, the runtime must move to a separate flavour (decision C).
- **Runtime size and startup.** PRoot + Termux packages are hundreds of MB and add
  cold-start time; needs a progress UI and a way to skip the download.
- **Supply chain.** Pinned assets must be hash-verified at build time, not fetched
  loosely.
- **Shell tool is the sharpest edge in the product.** Path confinement plus
  confirmation is the floor; a sandbox boundary is a later, separate design.

## Testing

- JVM: asset hashes, path confinement, confirm-on-write, provider adapter parsing.
- vitest: streaming provider against a mock server; descriptor conformance.
- CI: existing `ci`, `descriptors`, `release` lanes; `release` must fail if the
  license files are missing from the APK.
- Device: PRoot bootstrap and one agent turn per release candidate.
