# Adult-only custom mob spawning

## Intent and scope
The user approved forcing all custom mobs created by the plugin's shared `spawn()` method to be adults. Both the command and public `spawnMob()` API use that method. Ordinary world mobs and previously spawned entities remain unchanged.

## Approaches
- Selected: normalize the custom mob's age immediately after validating it in `spawn()`. This covers both entry points without affecting unrelated entities.
- Rejected: a global spawn listener, which would broaden the change to ordinary mobs.
- Rejected: separate command/API fixes, which would duplicate behavior.

## Design
Add a package-private static `makeAdult(Mob mob)` helper in `RockFrostPlugin`. Use the existing Paper entity age API for zombies and other ageable mobs; leave mobs without a baby form untouched. Call it from the shared spawn path before registering/configuring the mob. Keep existing configuration and dependencies unchanged.

## Verification
Follow the existing JUnit 4 dynamic-proxy test pattern: verify zombie adulthood, other ageable mob adulthood, and no age mutation for skeletons. Run the focused test followed by the complete Maven package build. Live server spawning cannot be proven by unit tests alone.
