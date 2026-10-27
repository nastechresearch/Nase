# Support

Nase is distributed as a sideloaded APK, not through Google Play ([PLAY_POLICY.md](../PLAY_POLICY.md)).
Support happens on the GitHub issue tracker for this repository.

The same metadata in machine-readable form is in [descriptors/support.yaml](../descriptors/support.yaml).

## Where to file what

| Report | Where |
| --- | --- |
| Something did the wrong thing, stopped, or crashed | [Bug report](https://github.com/nastechresearch/Nase/issues/new/choose) |
| A task is hard or impossible and you want it supported | [Feature request](https://github.com/nastechresearch/Nase/issues/new/choose) |
| A file in the repo is wrong, stale, or unclear | Bug report, naming the file and the section |
| Install, pairing, MCP, safety, or limits question | Check [README.md](../README.md) first; if the answer is not there, file a bug report and say what you tried |
| Security-sensitive issue | Do not open a public issue. See [Security reports](#security-reports) below |

The bug form asks for the Android version, the Nase version, whether a computer
or MCP server is involved, which brain was in charge, what the agent was asked
to do, what happened, and whether pairing, crypto, or the relay is involved.

## Labels

Each report gets one primary label from the areas of the repo. A report that
crosses areas can carry a second one.

| Label | Meaning |
| --- | --- |
| `bug` | Something Nase did that it should not have, or failed to do |
| `docs` | README.md, CHANGELOG.md, PLAY_POLICY.md, or docs/ is wrong, stale, or unclear |
| `android` | The Nase Companion app: accessibility service, hidden display, typing, app UI |
| `mcp` | The MCP server, its tools, and listen mode (`packages/mcp`) |
| `relay` | The WebSocket relay, Nase Cloud, or a private relay |
| `pairing/crypto` | Pairing links and QR codes, the six-digit safety code, end-to-end encryption |
| `safety` | Confirmations before sending or paying, password-field masking, calls, Stop |

## What makes a useful report

- **Nase version** from **Settings > Check for updates**, or from
  [latest.json](https://download.nase.karlmagendavid.com/latest.json). This tree
  is 0.6.4 (versionCode 11). If you built from source, say so and name the
  branch or commit.
- **Android version.** Nase requires Android 11 or newer.
- **Which setup you are running.** A key on the phone, Grok Bot, or an MCP host
  on a computer. The README's [three ways to connect a
  brain](../README.md#three-ways-to-connect-a-brain) describes all three. Name
  the provider or host, not the key.
- **What you asked for and what the phone did**, including the exact on-screen
  message if there is one.
- **Whether the problem involves pairing, crypto, or the relay**, or happens
  entirely on the device. That decides whether it lands on `pairing/crypto`,
  `relay`, or `android`.
- **For hidden-screen problems, whether Shizuku is on.** The per-app matrix is
  in [docs/hidden-display-apps.md](hidden-display-apps.md); check it there
  before filing, since some apps refuse the hidden display by design.

## Before you paste

Password fields are masked as `[password]` and typing into them is refused. The
action log stores character counts, never the text. Screenshots, UI trees, and
task history can still contain personal data, so remove addresses, order
numbers, names, API keys, pairing links, and safety codes from anything you
attach. Do not paste an API key to prove it works.

## Security reports

Do not put exploit details in a public issue. This repository does not yet name
a security contact: `TODO_OWNER_CONFIRM`. The owner must confirm where security
reports should go before this page is published widely.

## Documentation

- [README.md](../README.md): install, pairing, MCP setup, safety, limits
- [CHANGELOG.md](../CHANGELOG.md): what changed in each release
- [PLAY_POLICY.md](../PLAY_POLICY.md): why Nase ships as a direct APK
- [docs/hidden-display-apps.md](hidden-display-apps.md): which apps run out of sight
- [.env.example](../.env.example): environment overrides for the MCP server and CLI
- [LICENSE](../LICENSE): MIT

## Repository and downloads

The repository and issue tracker follow the git remote:
`github.com/nastechresearch/Nase`. README.md links the APK and GitHub Releases to
`github.com/oroplex/nase`; those download links are unchanged.
