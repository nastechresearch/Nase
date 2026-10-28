# Descriptor layer — decisions and wave 1 contract

Date: 2026-10-28. Branch: `feat/descriptor-layer` (stacked on PR #1).

## Decisions (settled with the owner)

1. **Single source of truth, compiled out** — descriptor YAMLs are authored once; a
   generator emits the Kotlin tool list, MCP registrations, wire types and CLI verbs.
   Generated files are committed and CI-gated against drift.
2. **App existence is decided on the device, never in YAML** — YAML only seeds
   aliases and annotations. Resolution order: exact package → curated alias →
   live `PackageManager` label → `unknown_app`.
3. **No bulk app export.** Resolution happens on device; the agent asks by name and
   receives one match plus tags. Ambiguity returns candidates (max 5), never a guess.
4. **The registry returns data; it never authorizes.** `ScreenGuard`,
   `awaitCallScreen`, display fallback and `SafetyPolicy` stay downstream and
   unchanged. App choice cannot bypass a confirmation.
5. **Approach C split** — capability surface is codegen; app catalogue is runtime data.

## Descriptor → seed shape

`descriptors/apps.yaml` compiles to `android/app/src/main/assets/apps_seed.json`:

```json
{
  "schema": 1,
  "apps": [{
    "package": "com.ubercab.eats",
    "label": "Uber Eats",
    "aliases": ["ubereats", "uber eats", "food delivery"],
    "deeplinks": ["ubereats://"],
    "hiddenDisplay": "bounces",
    "safetyTags": ["payment"]
  }]
}
```

`hiddenDisplay` is one of `trusted` | `bounces` | `unknown`, sourced from
`docs/hidden-display-apps.md`. If the seed is missing, corrupt, or has a newer
`schema`, consumers ignore it and fall back to live `PackageManager` lookup.

## Wave 1 file ownership (strict)

| Owner | May create | May edit |
|---|---|---|
| main agent | `descriptors/**`, `tools/describe/**`, generated files, `package.json`, `.github/workflows/ci.yml` | same |
| A1 registry | `display/AppRegistry.kt`, `display/AppMatch.kt`, `test/.../display/AppRegistryTest.kt` | `display/ScreenRouter.kt` |
| A2 safety | `voice/SafetyTable.kt`, `test/.../voice/SafetyTableTest.kt` | `voice/VoiceRisk.kt`, `widget/VoiceTile.kt`, `res/values/strings.xml` |
| A3 observability | `recap/OpMetrics.kt`, `recap/ActionExplanation.kt`, their tests | `recap/` only |
| A4 support | `descriptors/support.yaml`, `.github/ISSUE_TEMPLATE/*`, `docs/support.md` | README support section only |
| A5 brain data | `descriptors/brain.yaml` | nothing else |

Rules for every agent: no git commit, no push, no new dependencies, do not run
Gradle (no Android SDK in this environment), keep logic pure and JVM-testable,
and never edit a file you do not own. Report what you changed and what you could
not verify.

## Deferred pending owner decisions

- Per-app autonomy levels and prompt-injection tripwire (product policy).
- Cost telemetry and session replay (privacy).
