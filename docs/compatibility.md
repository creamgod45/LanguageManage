[English](compatibility.md) | [繁體中文](compatibility.zh.md)

# Plugin Compatibility Verification

## Supported products

`plugin.xml` declares only the shared `com.intellij.modules.platform` dependency. JetBrains Marketplace therefore derives compatibility for IntelliJ Platform-based products that provide this common module; product names are not maintained as a hard-coded list in the descriptor. Split-mode frontend and backend module dependencies remain in their respective content descriptors.

AppCode is not declared as a target. Its absence from Marketplace's supported-product list cannot be overridden by adding a product name to `plugin.xml`.

The approved LanguageManager `1.5.3` release and the current `1.5.4` source use the following plugin descriptor settings:

- Minimum version: JetBrains Platform build `253.5` (IntelliJ IDEA 2025.3.5)
- Maximum version: unrestricted
- Compilation and minimum-version test platform: IntelliJ IDEA 2025.3.5
- JVM bytecode target: Java 21

## Marketplace release status for 1.5.3

| Field | Value |
|---|---|
| Status | Approved |
| Approval date | July 21, 2026 |
| Compatibility range | `253.5+` |
| Marketplace package size | 791.4 KB |
| Uploaded by | Laifu來福（來福Laifu） |
| Version downloads at report time | 0 |

## Marketplace verification results for 1.5.3

JetBrains Marketplace Compatibility Verification reported the following LanguageManager `1.5.3` results on July 21, 2026:

| Status | Product | Version | Date and time | Verifier | Verdict |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 | 2026-07-21 12:40 | IDE | No issues occurred during an IDE run with the plugin installed |
| Success | IntelliJ IDEA | 2026.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 RC (`262.8665.184`) | 2026-07-21 12:37 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 2026-07-21 12:35 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.4 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.3 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.3 | 2026-07-21 12:37 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.1 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2025.3.6 | 2026-07-21 12:35 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2025.3.6 | 2026-07-21 12:37 | Plugin Verifier `1.408` | Compatible |

`Compatible` means Plugin Verifier found no binary compatibility failure for that IDE version. The IDE row is a separate installation-and-run check, while the other rows are static Plugin Verifier checks.

Leaving `until-build` unset allows newer IDE versions to attempt installation. Every release should still be verified against the latest stable IDE and relevant EAP/RC versions with both Plugin Verifier and an installation run.
