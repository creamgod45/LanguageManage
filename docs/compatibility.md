[English](compatibility.md) | [繁體中文](compatibility.zh.md)

# Plugin Compatibility Verification

LanguageManager `1.4.1` uses the following plugin descriptor settings:

- Minimum version: JetBrains Platform build `253.5` (IntelliJ IDEA 2025.3.5)
- Maximum version: unrestricted
- Compilation and minimum-version test platform: IntelliJ IDEA 2025.3.5
- JVM bytecode target: Java 21

## Marketplace Verification Results

JetBrains Marketplace Compatibility Verification reported the following results on July 14, 2026:

| Status | Product | Version | Time | Verifier | Verdict |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 17:15 | IDE | No issues occurred during an IDE run with the plugin installed |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 17:05 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 17:05 | Plugin Verifier `1.408` | Compatible |
| Incomplete | IntelliJ IDEA | 2026.1.4 | 17:47 | IDE | Marketplace displayed `—`; this is not recorded as a failure |
| Success | IntelliJ IDEA | 2026.1.3 | 17:07 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 17:08 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 17:07 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2025.3.6 | 17:48 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2025.3.5 | 17:49 | Plugin Verifier `1.408` | Compatible |

`Compatible` means Plugin Verifier found no binary or API compatibility problems for that IDE version. An actual IDE run and Plugin Verifier are separate checks. The `—` entry means that no verdict was available; it does not indicate a failed verification.

Leaving `until-build` unset allows newer IDE versions to attempt installation. Every release should still be verified against the latest stable IDE and relevant EAP/RC versions with both Plugin Verifier and an installation run.
