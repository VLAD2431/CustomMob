# Skelebass Projectile Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Stop Skelebass projectiles at solid blocks and prevent Skelebass from shooting while retreating.

**Architecture:** Keep behavior in the existing `RockFrostPlugin`; extract only a small pure predicate if needed to unit-test the retreat threshold. Use Bukkit block ray tracing in `BassProjectileTask` and stop at the nearest block or player collision.

**Tech Stack:** Java, Bukkit/Paper API, JUnit, Maven.

**Spec:** Approved in conversation: use the existing desired-distance minus 0.5 threshold for retreating; projectiles stop on first block collision.

## Global Constraints
- Preserve existing projectile damage, entity collision, and configuration behavior.
- Match `maintainDistance`: retreat begins below `desiredDistance - 0.5`.

## Review Focus
- A block collision earlier than a player collision must stop the projectile without damaging the player.
- A player collision before a block must retain existing damage and stop behavior.
- Skelebass must not shoot while closer than the retreat threshold; firing outside retreat remains cooldown-driven.

---

### Task 1: Skelebass retreat firing and projectile block collision

**Files:**
- Modify: `src/main/java/com/example/RockFrostPlugin.java`
- Test: `src/test/java/com/example/SkelebassBehaviorTest.java`

- [ ] Add a unit test for a pure retreat-threshold predicate: true below `desiredDistance - 0.5`, false at and above the threshold.
- [ ] Run `mvn test` and confirm the new test fails before implementation.
- [ ] Update `tickBass` to skip cooldown decrement/reset and firing while retreating; retain normal cooldown behavior otherwise.
- [ ] Update `BassProjectileTask.run` to ray-trace solid blocks across each movement step and process player collisions only if they occur before the first block.
- [ ] Run `mvn test` and confirm the full suite passes.
