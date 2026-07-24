[English](performance_memory.md) | [繁體中文](performance_memory.zh.md)

# Memory and Large-Scheme Verification

This project includes a reproducible regression test instead of relying on a theoretical object-size estimate:

```powershell
$env:JAVA_HOME = (Resolve-Path '.intellijPlatform/ides/IU-2025.3.5/jbr').Path
.\gradlew.bat :backend:test --tests cg.creamgod45.LocalizationMemoryFootprintTest --rerun-tasks
```

The generated machine-readable result is written to:

```text
backend/build/reports/language-manager-memory/12-language.properties
```

## Reference scenario

The test generates 12 real JSON language files and one real source file:

- 8,000 keys per locale, 96,000 parsed translation entries in total.
- Source calls are not mocked; all 8,000 calls are processed by the production Regex usage scanner.
- Because each logical key belongs to 12 locale entries, the scanner produces and validates 96,000 usage-location records.
- The 12 language files total 7,149,396 bytes (6.82 MiB). Every file is below the default 2,048 KiB file limit; the set is below the default 20 MiB scheme limit and 100,000-entry scheme limit.
- The production `LanguageFileCodec`, `UsageScanSupport`, DTOs, and RPC serialization shape are used.

Reference run on JetBrains Runtime 21 with compressed object references:

| Measurement | Before backend-only paging | After backend-only paging | Reduction |
| --- | ---: | ---: | ---: |
| Frontend retained object graph | 29,377,744 bytes (28.02 MiB) | 24,385,536 bytes (23.26 MiB) | 4,992,208 bytes (4.76 MiB) |
| Full state RPC JSON payload | 50,621,862 bytes (48.28 MiB) | 28,952,053 bytes (27.61 MiB) | 21,669,809 bytes (20.67 MiB) |

The backend retained graph for entries plus usage locations was 29,377,688 bytes (28.02 MiB). Usage locations must remain there because they power navigation, but they are no longer duplicated in every frontend state or normal state-RPC update. A selected key requests at most 100 deduplicated locations per page.

## What the numbers prove

The test asserts the exact parsed entry and scan-location counts, then fails if the optimized frontend graph or RPC payload is not smaller than the former state shape. JOL measures the retained Java object graph; it is not a measurement of short-lived parser peak memory, whole-IDE memory, Swing rendering, or JVM metadata. Absolute bytes can vary by JBR and compressed-reference settings, while the regression comparison remains reproducible on the same runtime.

Parser peak memory is controlled independently by checking file bytes before reading, enforcing per-file and per-scheme entry limits while parsing, applying a total scheme byte budget, limiting nesting, catching each file's parser error, and cooperatively cancelling obsolete loads.
