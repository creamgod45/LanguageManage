[English](compatibility.md) | [繁體中文](compatibility.zh.md)

# Plugin Compatibility Verification

## Supported products

`plugin.xml` declares only the shared `com.intellij.modules.platform` dependency. JetBrains Marketplace therefore derives compatibility for IntelliJ Platform-based products that provide this common module; product names are not maintained as a hard-coded list in the descriptor. Split-mode frontend and backend module dependencies remain in their respective content descriptors.

AppCode is not declared as a target. Its absence from Marketplace's supported-product list cannot be overridden by adding a product name to `plugin.xml`.

LanguageManager `1.5.0` uses the following plugin descriptor settings:

- Minimum version: JetBrains Platform build `253.5` (IntelliJ IDEA 2025.3.5)
- Maximum version: unrestricted
- Compilation and minimum-version test platform: IntelliJ IDEA 2025.3.5
- JVM bytecode target: Java 21

## Marketplace Verification Results for 1.4.1

JetBrains Marketplace Compatibility Verification reported the following LanguageManager `1.4.1` results on July 15–16, 2026:

| Status | Product | Version | Date and time | Verifier | Verdict |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-15 09:01 | IDE | No issues occurred during an IDE run with the plugin installed |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-15 08:58 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 RC (`262.8665.184`) | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 2026-07-15 08:58 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.4 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.3 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.3 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.2 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.1 | 2026-07-16 10:26 | Plugin Verifier `1.408` | Compatible |
| Warning | IntelliJ IDEA | 2026.1 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible; 77 usages of experimental API |
| Warning | PhpStorm | 2026.1 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible; 77 usages of experimental API |
| Success | IntelliJ IDEA | 2025.3.6 | 2026-07-15 08:58 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2025.3.6 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |

`Compatible` means Plugin Verifier found no binary compatibility failure for that IDE version. The 2026.1 warning rows remain compatible, but report 77 experimental API usages whose contracts may change in later IDE releases. An actual IDE run and Plugin Verifier are separate checks.

Leaving `until-build` unset allows newer IDE versions to attempt installation. Every release should still be verified against the latest stable IDE and relevant EAP/RC versions with both Plugin Verifier and an installation run.
