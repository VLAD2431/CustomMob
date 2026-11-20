# Harry Particles Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline execution.

**Goal:** Show CLOUD during spinning and DUST_PILLAR on landing.

**Architecture:** Reuse existing effect calls and particle configuration. Extend
RockFrostPlugin.particles(Location, ConfigurationSection, String) for BlockData.

**Tech Stack:** Java 21, Paper 1.21.7, Maven.

**Spec:** docs/superpowers/specs/2026-11-17-harry-particles-design.md

## Global Constraints

Preserve combat behavior and jump-start effects. Add no dependencies.

## Review Focus

Check solid ground data, non-solid fallback, unchanged particle types, effect
frequency, and existing server config behavior. Visual verification needs a server.

### Task 1: Configure and support particles

- [x] Add BlockData handling in the existing particle data dispatch, using ground material or STONE.
- [x] Add CLOUD to mobs.harry_mono.particles.spin-loop and replace jump-land with DUST_PILLAR.
- [x] Run `mvn test compile`; 33 tests passed, compilation succeeded. `mvn package -DskipTests` also succeeded.

No new automated tests for this small visual change; use the existing suite and compilation.
No commit: the workspace is not a Git repository.

Review: existing effect hooks already use the feet location and landing detection;
BlockData handling leaves other particle data branches unchanged. Ground is sampled
0.1 blocks below the feet, preserving the landing surface on slabs as well.
Visual validation on a live server is still required.
