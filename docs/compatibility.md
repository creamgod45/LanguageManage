[English](compatibility.md) | [繁體中文](compatibility.zh.md)

# Plugin Compatibility Verification

## Supported products

`plugin.xml` declares only the shared `com.intellij.modules.platform` dependency. JetBrains Marketplace therefore derives compatibility for IntelliJ Platform-based products that provide this common module; product names are not maintained as a hard-coded list in the descriptor. Split-mode frontend and backend module dependencies remain in their respective content descriptors.

AppCode is not declared as a target. Its absence from Marketplace's supported-product list cannot be overridden by adding a product name to `plugin.xml`.

The approved LanguageManager `1.5.3` and `1.5.4` releases use the following plugin descriptor settings:

- Minimum version: JetBrains Platform build `253.5` (IntelliJ IDEA 2025.3.5)
- Maximum version: unrestricted
- Compilation and minimum-version test platform: IntelliJ IDEA 2025.3.5
- JVM bytecode target: Java 21

## 1.5.4 progress API verification

The July 23, 2026 Marketplace report for the earlier `1.5.4` package found two internal API usages (`RawProgressReporterHandle` and `getReporter()`) plus 11 experimental usages from `RawProgressReporter`. The plugin remained binary compatible and passed the IDE installation run, but those progress APIs were not suitable for Marketplace distribution.

The implementation now uses the stable `Task.Backgroundable` and `ProgressIndicator` APIs while preserving cancellation, stage/detail text, exact progress fractions, and newer-load cancellation. The packaged ZIP contains no `RawProgressReporterHandle`, `RawProgressReporter`, or `reportRawProgress` references.

Local Plugin Verifier `1.408` results for the corrected package:

| Status | Product | Build | Verdict |
|---|---|---|---|
| Success | IntelliJ IDEA 2025.3.5 | `253.33514.17` | Compatible; no API usage warnings |
| Success | IntelliJ IDEA 2026.2 | `262.8665.258` | Compatible; no API usage warnings |

These checks cover the declared minimum build and the current stable build.

## Marketplace release status for 1.5.4

| Field | Value |
|---|---|
| Status | Approved |
| Approval date | July 23, 2026 |
| Compatibility range | `253.5+` |
| Marketplace package size | 938.48 KB |
| Uploaded by | Laifu來福（來福Laifu） |
| Version downloads at report time | 0 |

## Marketplace verification results for 1.5.4

| Status | Product | Version | Date and time | Verifier | Verdict |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 | 2026-07-23 11:29 | IDE | No issues occurred during an IDE run with the plugin installed |
| Success | IntelliJ IDEA | 2026.2 | 2026-07-23 11:26 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-23 11:33 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 RC (`262.8665.184`) | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 2026-07-23 11:25 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.4 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.3 | 2026-07-23 11:31 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.3 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 2026-07-23 11:31 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.2 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 2026-07-23 11:31 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.1 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Warning | IntelliJ IDEA | 2026.1 | 2026-07-23 11:32 | Plugin Verifier `1.408` | Compatible; 101 experimental API usages |
| Warning | PhpStorm | 2026.1 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible; 101 experimental API usages |
| Success | IntelliJ IDEA | 2025.3.6 | 2026-07-23 11:25 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2025.3.6 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |

The published package has no internal API warning. Only the unpatched 2026.1 product builds report 101 experimental API usages; every listed patch, RC, and stable product build is reported as Compatible without API warnings.

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
