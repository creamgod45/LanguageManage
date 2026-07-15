[English](user_manual_book.md) | [繁體中文](user_manual_book.zh.md)

# LanguageManager User Manual

This manual explains how to use LanguageManager in a JetBrains IDE to create isolated localization schemes, manage translations, inspect problems, and apply repairs safely.

> The plugin never selects, enrolls, or modifies language files automatically. Only files explicitly added to the active scheme can be read or written.

### Supported IDE versions

The minimum supported version is JetBrains Platform build `253.5` (IntelliJ IDEA 2025.3.5), with no configured upper bound. Marketplace Plugin Verifier has confirmed compatibility with IntelliJ IDEA 2025.3.5, 2025.3.6, 2026.1.1 through 2026.1.4, and 2026.2 RC. See [Compatibility Verification](compatibility.md) for the complete record.

## 1. Supported Content

### File formats

- JSON: `.json`
- YAML: `.yaml`, `.yml`
- Laravel PHP: `.php`; an optional `declare(strict_types=1);` may precede a static `return [...]` or `return array(...)`
- JetBrains/Java ResourceBundle Properties: `.properties`

### UI languages

The plugin follows the IDE display language by default and includes:

- English
- Traditional Chinese
- Simplified Chinese
- Japanese
- Korean

## 2. Installing the Plugin

### Install from ZIP

1. Open IDE Settings.
2. Go to **Plugins**.
3. Click the gear icon and choose **Install Plugin from Disk…**.
4. Select `LanguageManage-{version}.zip` without extracting it.
5. Restart the IDE if requested.

After installation, the LanguageManager icon appears in the IDE Tool Window sidebar.

## 3. Creating Your First Scheme

1. Open the **LanguageManager** Tool Window.
2. Open the **New Scheme** dropdown.
3. Choose a creation mode:
   - **Select Files**: select one or more JSON, YAML/YML, PHP, or Properties files, then enter a scheme name.
   - **Select Folders**: select one or more folders—such as `en`, `zh_CN`, and `zh_TW`—and wait for the backend to combine, scan, and parse the supported files.
4. The folder-recognition dialog displays each full path, format, locale, namespace, entry count, and recognition result. A failed file keeps its error message but cannot be selected. Use **Add Folder** to merge another folder into the scan.
5. Enter a recognizable scheme name, select the recognized files to manage, and click **Create Scheme**.
6. Wait for loading to finish. Parsed entries then appear in the translation table.

Folder mode checks at most 500 supported files, descends at most 16 levels per root, applies the configured new-scheme loading budget, and skips common non-source directories such as `.git`, `.idea`, `vendor`, `node_modules`, `build`, `storage`, and `cache`. Selecting both a parent and child directory does not duplicate files because normalized absolute paths are deduplicated. Discovery only creates a candidate list; no file is managed until the user confirms it.

Scheme behavior:

- Every scheme owns an independent file list and cache.
- Switching schemes never mixes entries from another scheme.
- Deleting a scheme never deletes its source language files.
- More folders may be added in the recognition popup before creation. To change the managed scope later, create a new scheme; the plugin never adds files automatically.

### Import and export scheme settings

Choose **Import Scheme Settings** or **Export Scheme Settings** from the Tool Window's **New Scheme** dropdown.

- Exports include scheme names, managed files, usage base paths, Regex patterns, and exclusions.
- Paths under the project root become portable `/`-separated relative paths. External paths remain absolute.
- Entries, issues, usage counts, and cache are not exported; imported schemes are reparsed.
- Relative paths are resolved from the currently open project root. The preview shows the scheme, configured path, resolved path, and recognition result.
- Import is disabled if any managed file is missing, has an unsupported extension, is unsafe, or uses `..` to escape the project root.
- Existing files with parser errors are shown as warnings and may still be imported for later repair.

## 4. Locale and Namespace Detection

### JSON/YAML

The filename is the locale:

```text
lang/en.json       -> locale: en, namespace: empty
lang/zh_TW.yaml    -> locale: zh_TW, namespace: empty
```

### Laravel PHP

The parent folder is the locale and the filename is the namespace:

```text
lang/en/messages.php       -> locale: en, namespace: messages
lang/zh_TW/validation.php  -> locale: zh_TW, namespace: validation
```

### JetBrains/Java ResourceBundle Properties

The base filename is the namespace. A bundle without a locale suffix is treated as English:

```text
LanguageManagerBundle.properties        -> locale: en, namespace: LanguageManagerBundle
LanguageManagerBundle_zh_TW.properties  -> locale: zh_TW, namespace: LanguageManagerBundle
```

## 5. Using the Translation Table

Translations with the same `namespace + key` are JOINed into one row. Every locale is displayed in a separate value column.

Example:

| Namespace | Key | en | zh_TW |
| --- | --- | --- | --- |
| messages | auth.failed | Invalid credentials | 登入資料錯誤 |

### Search

- **Fuzzy Search**: matches text contained in a key, value, namespace, locale, or path.
- **Exact Search**: a key, value, namespace, or full `namespace.key` must match without case sensitivity.
- **Locale Filter**: uses the selected locale to decide which keys match while retaining other locale columns for comparison.
- **Translation Status Filter**: defaults to all rows; it can show rows missing any locale value or rows with a usage count of zero.

### Pagination

- Every page contains at most 100 rows.
- Use **Previous** and **Next** below the table.
- Changing search conditions returns to the first page.

### Cell selection and clipboard

- Clicking any cell maps row actions to that cell's row. The whole row uses the current IDE theme's selection color.
- `Ctrl+C` copies selected cells; multiple cells produce TSV.
- `Ctrl+V` may paste into one locale value cell only.
- Namespace, Key, Usage, and issue columns cannot be pasted into.

## 6. Translation Actions

Open **Actions ▾** to access the following commands.

### Add translation

1. Choose the managed scheme file to write.
2. Confirm the locale and namespace derived from that file.
3. Enter a key and value.
4. Confirm to write safely and reload the scheme.

### Add locale version

1. Choose **Actions ▾ → Add Locale Version**.
2. Select a source locale such as `en`.
3. Enter a new locale code such as `es`.
4. The plugin maps every source file to a target: `en/auth.php` becomes `es/auth.php`, and `en.json` becomes `es.json`.
5. Normal translation values are cleared for translation. JSON arrays retain their structure to keep files parseable.
6. Review every new file in the Diff and apply before files are created and added to the scheme.

Creation stops without overwriting if the target locale or file already exists, a source cannot be parsed, or multiple sources map to the same target.

### Edit selected

1. Select any cell in the translation table.
2. Choose **Actions ▾ → Edit Selected**.
3. Selecting a locale value edits that locale directly. Selecting Namespace or Key uses the row's first existing locale.
4. The form uses label/input rows. Switching its target file refreshes locale, namespace, and value from the same key in that file; a missing key produces an empty value.
5. Keys may be natural-language sentences containing spaces, Unicode, and punctuation, such as `Not powered on or not detected`. Only blank, control-character, or over-256-character values are rejected.
6. Confirm the key/value changes. The currently selected file is the write target.

### Bulk delete

1. Select one or more rows.
2. Choose **Delete Selected**.
3. The confirmation count represents selected JOINed key rows, not the multiplied number of locale entries.
4. Confirm to remove all locale entries belonging to those rows.

### Rename key

1. Select one translation key.
2. Choose **Rename Key**.
3. Enter the new key.
4. Every file in the scheme containing the old key is renamed together.

If any affected file already contains the new key, the entire write is rejected to prevent overwriting.

### IDE Find in Files

Select one row and choose **Find in Files** to open the IDE-native search. Every format inserts only the actual key. PHP filename namespaces such as `auth` and ResourceBundle names such as `LanguageManagerFrontendBundle` are never prefixed.

Choose **Find in Files with Usage Regex** to select the first active-scheme usage Regex containing a `(?<key>…)` named group. The complete key group is replaced with the selected row's literal key, outer `^`/`$` anchors are removed, and IDE Regex mode is enabled. Regex metacharacters in the key are escaped character by character—for example, `custom\.attribute-name\.rule-name`—without using `\Q...\E`. Other groups and backreferences, such as `(?<quote>…)` and `\k<quote>`, are retained. If no pattern contains a named `key` group, the plugin reports an error without opening search.

## 7. Issues and Suggestions

The **Issues and Suggestions** tab can contain:

| Type | Meaning | Available action |
| --- | --- | --- |
| Parse/read error | A file cannot be parsed or read safely | Open the source file for manual repair |
| Missing value | Value is blank | Preview filling it with the key |
| Duplicate key | Same locale, namespace, and key appear more than once | Locate and decide manually |
| Duplicate value | Multiple keys share one value | Locate and decide manually |
| Missing locale | A key is absent from one or more locales | Locate it in the translation table |
| Possibly unused | No source-code usage was discovered | Preview deletion or keep it |

### Single-row handling

Click **Handle** in the final column. Depending on issue type, the action previews a repair/deletion, opens the file, or locates the translation row.

### Bulk handling

- **Handle Selected** processes the selected issue rows.
- **Handle All Repairable** includes missing values and possibly-unused keys.
- Issues requiring human judgment are never modified automatically.

> Possibly-unused results come from a bounded text scan and are suggestions only. Dynamically constructed keys may not be discovered.

## 8. Diff Preview and Safe Apply

**Repair/Normalize** and automatic issue handling never write immediately.

1. The backend generates current and proposed content in memory.
2. An IDE two-pane Diff displays current content on the left and proposed content on the right.
3. For bulk changes, select each affected file from the control above the Diff.
4. **Cancel** writes nothing.
5. **Apply Changes** rechecks the source SHA-256.
6. If the IDE, Git, or another process changed the file after preview, the plugin refuses to overwrite and requests another preview.

### What repair/normalize does

- Fills blank values with their key.
- Renders parseable content in normalized JSON, YAML, PHP, or Properties syntax.
- Never guesses or writes an unparseable file.

## 9. Format Notes

### JSON

- The root must be an object.
- Nested objects appear as dotted keys in the table.
- Arrays are displayed as JSON text and remain arrays when written back.
- Dots inside sentence-style keys remain literal and do not create nested objects.

### YAML

- Use spaces for indentation; tabs are rejected.
- Single quotes, double quotes, and normal end-of-line comments are supported.
- Normalization may change quoting and indentation; always review the Diff.

### Laravel PHP

- Only an optional `declare(strict_types=1);` and a static return array are accepted.
- Strings, numbers, booleans, and nested arrays are supported.
- Other `declare` directives, calls, variables, concatenation, and arbitrary expressions are rejected.
- PHP is never executed.

### JetBrains/Java ResourceBundle Properties

- Supports comments beginning with `#` or `!` and `=`, `:`, or whitespace key/value separators.
- Supports backslash continuations and Java escapes such as `\t`, `\n`, `\ `, `\:`, and `\u4F60`.
- Duplicate/blank keys, unfinished escapes, and malformed `\uXXXX` sequences produce a safe `PARSE_ERROR` without writing the source.
- Edits and normalization use UTF-8. Comments and original layout are not preserved, so review the Diff.
- Locale creation can produce `Bundle_es.properties` from `Bundle.properties`; `Bundle_zh_TW.properties` retains the `Bundle` namespace.

## 10. Usage Scan Settings

Select a scheme in the Tool Window and click **Scheme Settings**. The popup uses the already-loaded active scheme and shows its managed files and isolated scan settings. It does not dynamically load schemes from IDE Settings.

**Settings → Tools → LanguageManager** manages plugin display language, issue visibility, and defaults for newly created schemes. Existing schemes are not loaded there. The Tool Window's JetBrains **More Options** menu also contains a shortcut to this settings page.

Hiding duplicate-value or possibly-unused suggestions removes that type from the issue table, status count, and **Handle All Repairable** action without affecting other diagnostics.

New-scheme defaults include a base path at the current project or 1–10 parent levels, Regex patterns, and exclusions. These values are copied into future schemes and never overwrite existing scheme settings.

New schemes default to a 2,048 KB limit per language file, 20 MB total language content, 20,000 entries per file, and 100,000 entries per scheme. The same four limits can be changed independently in each scheme. File size is checked before parser allocation; entry limits are enforced while the parser builds its map, and JSON/PHP nesting is capped at 128 levels. Over-limit content is skipped and appears as an issue instead of being retained in the table or cache. Hard safety ceilings are 10 MB per file, 100 MB per scheme, 100,000 entries per file, and 250,000 entries per scheme. Oversized results remain usable in the current in-memory state but are not serialized into an oversized disk cache.

The settings shortcut targets the registered plugin settings component rather than a localized page title, so it works in every supported UI language.

### Scan base path

- Blank means the currently open project root.
- **Browse** may select another safe local or WSL directory, such as a monorepo frontend root.
- The base path affects usage counting only and never enrolls language files from that folder.

### Usage detection regular expressions

Both the new-scheme defaults and active-scheme settings display the complete placeholder explanation below the Regex list. The Add/Edit dialog also shows a directly usable double-quote example such as `(?:backendMessage|message)\(\s*"(?<key>[^"\r\n]{1,256})"\s*\)`. Replace the function names with those used by the project. Prefer a function-specific prefix over matching every quoted string, because an earlier string or character literal on the same line can consume a non-overlapping match before the localization call is reached.

Each Regex match extracts a candidate key in this order:

1. Named group `(?<key>…)`.
2. First capture group.
3. Complete match when no group exists.

The candidate must exactly equal a scheme `key` or `namespace.key` before it is counted. Example for Laravel helpers:

```regex
(?:__|trans)\(\s*["'](?<key>[^"']+)["']
```

Example for Vue/JavaScript `$t()`:

```regex
\$t\(\s*["'](?<key>[^"']+)["']
```

Multiple occurrences of the same key on one line count once. Dynamically concatenated keys usually cannot be detected reliably.

### Excluded directories

Defaults include:

- Project/dependency: `.git`, `.github`, `docs`, `vendor`, `node_modules`.
- Laravel/Gradle/build: `storage`, `database`, `gradle`, `.gradle`, `build`, `out`, `dist`, `target`.
- IDE: `.idea`, `.run`, `.vscode`, `.fleet`, `.vs`, `.settings`, `.metadata`, `nbproject`.
- AI/environment: `.env`, `.claude`, `.codex`, `.gemini`, `.agents`, `.ai`.

When upgrading, an untouched old default list is migrated with new defaults. A customized list is never replaced.

- A single name such as `vendor` excludes every directory with that name.
- A relative path such as `tests/fixtures` excludes only that branch under the base path.
- Use **Add/Edit/Delete** to prevent fixtures, tests, or generated code from inflating counts.

Applying settings persists modified schemes, invalidates their caches, and recounts the active scheme in the background. A scheme supports at most 20 Regex patterns of 512 characters each and 100 exclusions.

## 11. Cache and Reload

Schemes and caches are stored under:

```text
.idea/language-manager/
├── schemes.json
└── cache-{schemeId}.json
```

- Normal scheme switching uses cache when fingerprints are unchanged.
- **Reload** forces parsing.
- A source change, cache format upgrade, or fingerprint mismatch rebuilds the cache.
- Do not edit cache manually. Deleting cache never deletes source language files.

## 12. Troubleshooting

### Nothing happens after creating a scheme

- Check whether the status bar says loading.
- Allow the first WSL/remote filesystem access to finish.
- Click **Reload**.
- Confirm files still exist and are no larger than 10 MB each.
- Search IDE logs for `Language Manager`.

### Parser errors appear

- Click **Handle** to open the source file.
- JSON requires a complete object root.
- YAML cannot use tab indentation.
- PHP must be an optional `declare(strict_types=1);` followed by a purely static return array.

### Paste does nothing

- Select exactly one cell.
- Select a locale value column, not Namespace, Key, Usage, or diagnostics.
- Confirm that the clipboard contains text.

### Usage count is zero

- Dynamic keys, template helpers, or non-text references may not be detected.
- Select the scheme and open **Scheme Settings** to verify base path, Regex, and exclusions.
- Excluded directories are skipped. New schemes exclude `.git`, `.github`, `docs`, `vendor`, and common AI/IDE configuration folders by default.
- Zero means the bounded scan found nothing; it does not prove that a key is unused.

## 13. Reporting an Issue

The **Report an Issue** link at the top of the Tool Window opens:

[https://github.com/creamgod45/LanguageManage/issues/new](https://github.com/creamgod45/LanguageManage/issues/new)

Choose the appropriate Issue type:

- Bug report: reproducible UI, RPC, parser, write, or performance problems.
- Feature request: new workflows, analysis, formats, or IDE integration.
- Format compatibility: a minimal JSON, YAML, Laravel PHP, or ResourceBundle Properties parse/round-trip example.

Before submitting, remove passwords, tokens, client names, and other sensitive content from logs, paths, and language files.
