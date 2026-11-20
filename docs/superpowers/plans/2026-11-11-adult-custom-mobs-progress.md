# SDD ledger — plan: docs/superpowers/plans/2026-11-11-adult-custom-mobs.md

Pre-flight: no shared interfaces (one task).

Ruling: Execute in the existing non-Git project without worktrees, commits, or Git-dependent workspace scripts — the plan explicitly prohibits initializing Git and the user requested implementation here — no branch isolation is available.

Ruling: Use Ageable.setAdult() for zombies and other ageable mobs through one branch — the locally resolved Paper API declares Zombie extends Ageable — this relies on the project's pinned Paper API contract.

Task 1: complete.

- RED: `mvn -o "-Dtest=MobAgeTest" test` failed with the expected three missing `makeAdult` method errors before production implementation.
- GREEN: the same focused command passed 3 tests with no failures or errors.
- Full verification: `mvn -o clean package` passed 17 tests, zero failures/errors/skips, and produced `target/custom_mob-1.0.0.jar`.
- Reviewed shared spawn placement and public API routing; invalid-entity rejection remains unchanged.
- Final independent read-only reviewer: no actionable findings. Live Paper server spawning was not tested.
- Existing unrelated compile warning: deprecated-for-removal `Sound.valueOf` remains unchanged.
- No Git repository exists, so finishing/integration menus, commits and workspace cleanup are inapplicable; changes and JAR are preserved in the requested project.
