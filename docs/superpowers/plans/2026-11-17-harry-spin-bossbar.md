# Harry Spin and Boss Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep a health boss bar visible for living Harry Mono mobs and delay spin pull by one second while stopping players 1.5 blocks from the boss.

**Architecture:** Keep boss bar lifecycle in `HarryMonoController` alongside per-mob state. Extract small pure helpers for spin pull timing and target radius so behavior can be unit-tested without a server; track boss bars by UUID and reconcile viewers during mob ticks.

**Tech Stack:** Java 21, Paper API 1.21+, Maven, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-17-harry-spin-bossbar-design.md`

## Global Constraints
- Use Adventure `Component` for boss-bar title; no legacy `ChatColor` for new text.
- Verify Java changes with `mvn test-compile` or `mvn compile`.
- Keep changes scoped to Harry Mono behavior.

## Review Focus
- Spin should not pull before 20 active-spin ticks; test tick boundary.
- Pull should stop at 1.5 blocks and not reverse when already within that radius; test target-radius math.
- Boss bar viewers should be limited to the boss's world and cleared on lifecycle end; validate implementation paths in controller and plugin shutdown.

---

### Task 1: Spin timing and pull radius

**Files:**
- Modify: `src/main/java/com/example/HarryMonoController.java`
- Test: `src/test/java/com/example/HarryIntegrationTest.java`

- [ ] Add tests for the 20-tick pull activation boundary and a 1.5-block stopping radius.
- [ ] Run `mvn -Dtest=HarryIntegrationTest test` and confirm the new tests fail for the missing helper behavior.
- [ ] Implement pure package-visible helper(s) and apply the delayed pull plus radius-limited velocity in `spin`.
- [ ] Run the focused test and confirm it passes.

### Task 2: Persistent Harry boss bar

**Files:**
- Modify: `src/main/java/com/example/HarryMonoController.java`
- Test: `src/test/java/com/example/HarryIntegrationTest.java` (only if a pure lifecycle helper is introduced)

- [ ] Add per-mob boss-bar tracking, update title/progress/viewers while ticking living registered Harry Mono mobs, and remove viewers/bar from `remove` and `close`.
- [ ] Ensure unload, death, invalid entity, and plugin disable paths invoke cleanup (existing lifecycle calls should be reused).
- [ ] Run `mvn test` and `mvn test-compile`.
