# Plan Doc UTF-8 Guard Skill Design

## Goal

Create a Codex skill that only activates while filling in, revising, extending, or repairing Markdown documents under `C:\Users\93445\Desktop\Astrbot\上下文\Plan`, so document edits do not introduce mojibake caused by lossy read/write steps.

## Approved Direction

- Build the skill as `plan-doc-utf8-guard`
- Install it under `C:\Users\93445\.codex\skills\plan-doc-utf8-guard`
- Bundle a deterministic guard script instead of relying on prose-only instructions
- Keep the skill narrowly scoped to document work inside the `Plan` tree

## Problem Statement

- The recent corruption was caused by lossy read/write handling, not by the original files being inherently non-UTF-8
- Once `U+FFFD` replacement characters are written back to disk, the corruption becomes permanent
- Same-name files under different paths make it easy to edit the wrong source document and later overwrite the intended one
- Terminal output is not a trustworthy source artifact and must never be used as write-back input for the original document

## Scope

- Trigger only when Codex is asked to fill in, modify, continue, repair, or rewrite Markdown documents under `C:\Users\93445\Desktop\Astrbot\上下文\Plan`
- Guard edits to `.md` files by checking path, UTF-8 decodability, and `U+FFFD`
- Warn when duplicate file names exist under the `Plan` tree so the true source can be confirmed before editing
- Encode the workflow order directly in the skill instructions

## Out of Scope

- Code writing or code review tasks
- Documents outside `C:\Users\93445\Desktop\Astrbot\上下文\Plan`
- Automatic repair of already-corrupted text
- General-purpose encoding detection for arbitrary repositories

## UX Intent

- The skill should feel like a narrow safety rail, not a global writing mode
- When the target file is outside the `Plan` tree, the skill should not intervene
- When a guarded file fails validation, Codex should stop write-back and report the precise reason
- The instructions should be short enough to trigger reliably without wasting context

## Skill Trigger Design

### Name

`plan-doc-utf8-guard`

### Frontmatter Description Intent

The description should say that the skill is used when Codex edits Markdown planning documents under the `Plan` tree and must preserve source-file encoding integrity. It must also say that the skill is not for code changes and does not apply outside that directory.

### Representative User Triggers

- "Help me fill in this implementation plan under `C:\Users\93445\Desktop\Astrbot\上下文\Plan`."
- "Continue writing this markdown plan document in the Plan directory."
- "Repair this Plan document, but do not break its encoding."

## Core Workflow

The skill body should instruct Codex to follow this exact order:

1. Confirm that the target file is inside `C:\Users\93445\Desktop\Astrbot\上下文\Plan`
2. Run the guard script before reading for edit decisions
3. Edit the source file directly instead of round-tripping through terminal output or clipboard text
4. Write the file back with explicit UTF-8
5. Run the same guard script again after the write completes
6. Refuse to treat terminal-rendered text as the source of truth

If step 1 fails, the skill does nothing and yields control to normal behavior. If any guarded validation step fails, the skill must stop before write-back and report the failure.

## Bundled Resources

### Required

- `SKILL.md`
- `scripts/guard_plan_doc.py`
- `agents/openai.yaml`

### Not Required

- `references/`
- `assets/`

This skill is intentionally small. All essential rules live in `SKILL.md`, and the deterministic checks live in the script.

## Guard Script Contract

### Path

`C:\Users\93445\.codex\skills\plan-doc-utf8-guard\scripts\guard_plan_doc.py`

### CLI

```bash
python scripts/guard_plan_doc.py <target-file> [--root <plan-root>] [--check-duplicates]
```

### Defaults

- Default `--root` value: `C:\Users\93445\Desktop\Astrbot\上下文\Plan`
- Default behavior checks only the provided file
- `--check-duplicates` enables same-name file discovery under the root

### Success Conditions

- The target resolves under the `Plan` root
- The file bytes decode as strict UTF-8
- The decoded content does not contain `U+FFFD`

### Failure Conditions

Return non-zero and print a concise machine- and human-readable reason when:

- The target is outside the guarded root
- The file cannot be decoded as UTF-8
- The file contains `U+FFFD`
- The path does not exist or is not a regular file

### Duplicate Detection

When `--check-duplicates` is present, search the `Plan` tree for files with the same basename as the target file.

- If exactly one match exists, report success normally
- If multiple matches exist, report success for the target file but also print the candidate paths so Codex can confirm the true source before editing
- Duplicate detection is advisory, not a hard failure by itself

## Editing Rules to Encode in SKILL.md

- Validate path first, then validate encoding
- Only write guarded documents with explicit UTF-8
- Scan for `U+FFFD` before and after every write
- Never use terminal output as a write-back source
- Prefer direct edits against the source document
- If validation fails, report and stop instead of "trying once" to save

## Implementation Notes

- The script should use Python standard library only
- Path checks should use resolved absolute paths to avoid simple path traversal confusion
- UTF-8 checks should operate on raw bytes with strict decoding
- `U+FFFD` detection should happen on decoded text after strict UTF-8 succeeds
- Output should be brief so the skill can easily consume it during routine use

## Validation Strategy

Implementation should validate the skill at two levels.

### 1. Skill Structure Validation

Run the official validator after the files are created:

```bash
python C:\Users\93445\.codex\skills\.system\skill-creator\scripts\quick_validate.py C:\Users\93445\.codex\skills\plan-doc-utf8-guard
```

### 2. Behavior Validation

Validate realistic scenarios for the script and the skill workflow:

- A valid UTF-8 Markdown file under `Plan` passes
- A file outside `Plan` is reported as outside scope
- A file with invalid UTF-8 bytes fails
- A file containing `U+FFFD` fails
- A duplicated basename under `Plan` emits duplicate warnings

### 3. Writing-Skills Baseline

Before finalizing the skill instructions, run at least one baseline scenario without the skill to capture the natural failure mode, then verify that the finished skill changes the behavior. The implementation plan must include this RED/GREEN loop because `writing-skills` requires it.

## Open Questions Resolved

- Paths outside `C:\Users\93445\Desktop\Astrbot\上下文\Plan` are ignored rather than blocked
- Duplicate file names trigger warnings, not hard refusal
- The guard script should be written directly as part of the skill bundle

## Notes

- The skill's core rule should stay short and memorable: "Validate path first, then encoding; write only with UTF-8; scan `U+FFFD` before and after writes; never treat terminal output as the source of truth."
- The follow-up implementation plan should include both the skill files and the baseline/verification tasks required by `writing-skills`
