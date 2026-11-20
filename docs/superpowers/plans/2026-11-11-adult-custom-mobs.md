# Adult-Only Custom Mobs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure newly spawned custom mobs are adults without changing ordinary world mobs.

**Architecture:** Normalize age through a small static helper called only by the shared custom spawn method. Reuse the existing entity APIs and dynamic-proxy test conventions.

**Tech Stack:** Java 21, Paper API 1.21.7-R0.1-SNAPSHOT, Maven, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-11-11-adult-custom-mobs-design.md`

## Global Constraints
- Affect only newly created custom mobs, through the existing shared `spawn()` method.
- Do not change ordinary world mobs, previously spawned entities, configuration, or dependencies.
- Preserve existing spawn validation and registration behavior.
- This directory is not a Git repository; do not initialize Git or attempt commits.

## Review Focus
- Zombie variants must receive the adult-setting operation.
- Other ageable entities must become adults if configured as custom mobs.
- Skeletons and other non-ageable mobs must remain otherwise unchanged.
- Both command and public API must retain their common spawn path.
- Invalid or unsupported entities must retain existing rejection behavior.

---

### Task 1: Normalize custom mob age at spawn

**Files:**
- Modify: `src/main/java/com/example/RockFrostPlugin.java`
- Create: `src/test/java/com/example/MobAgeTest.java`

**Interfaces:**
- Consumes: existing private `spawn(CommandSender sender, String type, Location location)`.
- Produces: package-private `static void makeAdult(Mob mob)`.

- [ ] **Step 1: Write failing tests.** Use JUnit 4 and dynamic proxies, matching `MobFacingTest`. Add `zombieIsMadeAdult`, `ageableMobIsMadeAdult`, and `nonAgeableMobIsUnchanged`; assert the adult-setting call on a Zombie and a Cow, and an empty invocation list on a Skeleton. Confirm available age APIs against the locally resolved Paper dependency before finalizing exact assertions.
- [ ] **Step 2: Run the focused test and confirm failure.** Run `mvn -Dtest=MobAgeTest test`; expect compilation failure because `makeAdult` does not yet exist.
- [ ] **Step 3: Implement the helper and call it in `spawn()`.** Use the available Paper adult-setting API for Zombie and Ageable instances, with no mutation for other mobs. Place the call after the `Mob` validation and before the existing custom mob configuration. Do not add global listeners or change registration of existing entities.
- [ ] **Step 4: Verify the focused tests and package.** Run `mvn -Dtest=MobAgeTest test`, then `mvn package`; expect zero test failures and a successful package build. Inspect the spawn call placement and unchanged command/API routing and rejection branches.
- [ ] **Step 5: Review and report.** Load verification/review skills as required, review only relevant changes, and report test evidence and the built JAR path. Explicitly distinguish unit/build verification from live-server verification.
