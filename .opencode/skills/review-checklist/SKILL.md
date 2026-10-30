---
name: Review Checklist
description: Review code changes for correctness, edge cases, code quality, readability and missing tests. Use when asked to review a diff, PR, commit, patch or in-progress work, when asked for a code review or second opinion on Java/Spring Boot changes, or before calling a change done. Read-only: produces findings, never edits files.
---

# Review Checklist

Review the requested change only. Everything below is a lens for judging that change, not a mandate to audit the repo.

## 0. Establish scope first

1. Get the change set: `git status`, then `git diff` (unstaged), `git diff --staged`, or `git diff <base>...HEAD` if the
   user named a branch or commit range. Use `git show <sha>` for a single commit.
2. Read the full file around each hunk, not just the hunk. Most review misses come from judging a line without its
   enclosing method.
3. State the scope you reviewed in one line at the top of the report. If the asked-for scope is ambiguous (which
   branch, which files), say what you assumed rather than silently reviewing everything.

## 1. Correctness

- Does the code do what the change claims? Trace one happy-path call end to end — controller → service → repository →
  entity — and confirm the wiring actually resolves (right bean, `@Primary` vs `@Qualifier`, right interface).
- Nullability: every new dereference, `get()` on `Optional`, unboxing, `Map`/`List` lookup, and stream reduction. Which
  inputs make it throw?
- Boundaries: what happens for the empty list, the missing entity (404 vs 500), the duplicate name, the unknown enum,
  the oversized/negative field, the non-numeric path variable, a body with unknown or wrong-typed fields.
- Contract shape: HTTP status codes, response bodies, and error messages match the rest of the API; a changed field or
  status is a breaking change for existing clients — call it out if it is unannounced.
- Persistence: transaction boundaries (`@Transactional` where multi-step writes must be atomic), flush timing,
  `LazyInitializationException` risk on detached entities, entity equality, and whether a new relationship needs
  cascade/orphan removal.
- Concurrency and idempotency: read-modify-write races, find-or-create under a unique constraint (does the code handle
  the constraint violation it will eventually hit?), duplicate submissions.
- Soft deletes: new reads must filter `State.ACTIVE`; new deletes must not hard-delete; a changed state is handled
  consistently by every query it touches.
- Time and encoding: timestamps bumped on write, `LocalDateTime` vs `Instant` consistency with `BaseModel`.

## 2. Edge cases specific to this codebase

- PATCH is three-state: key missing → `null` → leave untouched; `"field": value` → `Optional.of` → apply;
  `"field": null` → `Optional.empty()` → clear. Check new patch fields preserve all three, that clearing a required
  field is rejected or intentionally allowed, and that `lastUpdatedAt` is still bumped on every patch.
- Bulk and import paths (`/products/import`) are not transactional per item — is a partial failure observable, and is
  that acceptable?
- External calls (`PitchService`, dummyjson) degrade instead of propagating failure; a new external call needs the same
  treatment plus a timeout.
- Auth: `TokenCheckFilter` covers new endpoints unless explicitly excluded. A new public endpoint should be a
  deliberate, visible decision.
- `Optional` DTO fields, Jackson 3 imports (`tools.jackson.databind.*`), and WAR/`provided` Tomcat assumptions hold.
- Tests here need a live MySQL for `@SpringBootTest`; a change that only passes with a DB must say so.

## 3. Code quality and consistency

- Package-by-layer placement, naming, and shape match the neighbours (interface + impl for services, mapper for
  DTO ↔ entity, constructor injection, Lombok where the file already uses it).
- No entity leaks into responses; no DTO logic pushed into the controller; no repository call made from a controller.
- Duplication only matters when the change introduces a second copy of logic that already exists — then point at the
  existing one.
- Error handling: no swallowed exceptions, no `catch (Exception e) { }`, no logged-and-rethrown, no `Optional.get()`
  without a guard, no `null` return used to signal "not found" when the codebase uses `Optional`.
- Dead ends: unused parameters, unused imports, unreachable branches, fields nothing reads, `@Deprecated` without a
  replacement path.
- New public names use the existing vocabulary (`ProductDto`, `patchProduct`, …), not a synonym for an existing concept.

## 4. Readability and comments

- Could a competent reviewer understand the method after one read? Flag needlessly nested branching, boolean parameters,
  and clever one-liners that need a decoder.
- **Comments are the default suspect.** Flag any new comment that:
  - restates what the next line plainly says (`// set the name`);
  - narrates the diff ("added to fix the bug", "new method", "changed from X to Y");
  - documents history, ticket numbers, or a conversation that will be meaningless in six months;
  - is now stale or contradicts the code it sits next to;
  - explains *what* where the code should be made obvious instead;
  - is commented-out code, or a leftover `TODO`/`FIXME` with no owner.
  Comments that earn their place explain a non-obvious *why* (a workaround, a protocol quirk, an invariant) — keep those.
- Javadoc: no ceremonial Javadoc on trivial getters or DTOs; no `@param` lists that just repeat parameter names.
- Prefer the fix that deletes the comment: better names and smaller methods, not more prose.

## 5. Tests

- Is there a test that fails before the change and passes after? If not, that is a `major`, not a nit.
- Coverage of the actual risk: the edge cases from §1–§2 (missing/empty/null, not-found, duplicate, boundary values,
  state transitions), not just the happy path.
- Existing tests updated rather than deleted or weakened (no assertions removed, no `@Disabled` added, no broad
  `assertNotNull` standing in for a real check).
- Unit test (mapper/service, no Spring context) vs `@SpringBootTest` (needs MySQL) chosen deliberately; a slow context
  test for pure logic is a nit, a DB-dependent test for pure mapping is a smell.
- Test names say what broke when they fail. Arrange/act/assert stays readable.
- If tests were not run, say what you could not verify. Do not claim the suite is green.

## 6. Report format

```
Scope: <files / commits reviewed, and what you assumed>

Verdict: APPROVE | APPROVE WITH NITS | CHANGES REQUIRED

Findings (highest severity first)
1. [blocker] ProductController.java:42 — <defect>. <why it breaks: input, state, or contract>. <fix direction, one line>.
2. [major]   ...
3. [minor]   ...
4. [nit]     ...

Not verified: <what you could not check, e.g. tests requiring MySQL>
```

Rules for the report:

- Severity means: `blocker` = wrong behaviour, data loss, security, or broken build; `major` = likely bug, or missing
  test for real risk; `minor` = quality or robustness issue that will bite later; `nit` = preference, take it or leave it.
- Cite `file:line` for every finding. No line reference, no finding.
- One finding per line. Group only when several lines share one root cause.
- No summary of the change you were asked to review, no restating what the code does, no praise section.
- If there are no findings, write `Verdict: APPROVE` and nothing beyond `Not verified`.
- Never modify files, never apply fixes. If asked to fix, decline and hand the findings back.
