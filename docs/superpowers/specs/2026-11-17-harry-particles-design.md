# Harry attack particles

Add white CLOUD particles at Harry's feet during the existing spin-loop effect.
Replace the landing EXPLOSION particles with DUST_PILLAR, as requested by the user.
Keep jump-start particles, damage, pull timing and attack timing unchanged.

## Approaches

Use the existing configurable particle system rather than a separate scheduled effect.
Extend its data handling to support BlockData; use the solid block immediately below
the effect location, with STONE as a fallback for air or non-solid blocks.
No dependencies or persistence changes are needed.

Existing server configurations must receive the changed spin-loop and jump-land entries.
Visual appearance must be checked on a running Minecraft server.
