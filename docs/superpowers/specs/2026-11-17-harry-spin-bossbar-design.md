# Harry Spin and Boss Bar Design

Keep Harry Mono's boss bar visible while the boss is alive, including its current health. During spin, begin pulling players after 20 ticks and stop pulling toward a point 1.5 blocks from the boss center. Preserve current spin damage, duration, and config-driven pull strength. Remove the bar when the mob dies, unloads, is removed, or the plugin disables. Use existing Paper/Bukkit APIs and add focused unit tests for the pure spin timing and pull-distance calculation. No new dependencies or unrelated behavior changes.

Approaches considered: show the bar only during spin (rejected per user clarification); show it while the boss is alive (selected); use a global/broadcast bar (rejected because it exposes unrelated bosses to all players). The selected implementation updates each boss bar with viewers in that boss's world and tracks it by mob UUID.
