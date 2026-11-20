package com.example;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Main-thread combat and temporary effects. Collections retain IDs, never entities. */
final class HarryMonoController implements Listener {
    private final RockFrostPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Set<UUID>> bossBarViewers = new HashMap<>();
    private final Stuns stuns = new Stuns();
    private HarrySettings currentSettings;
    private long now;
    private final Map<UUID, List<Player>> nearbyCache = new HashMap<>();

    HarryMonoController(RockFrostPlugin plugin) { this.plugin = plugin; reload(); }
    void reload() { currentSettings = new HarrySettings(plugin.getConfig().getConfigurationSection("mobs.harry_mono")); }
    private HarrySettings settings() { return currentSettings; }
    void pulse() { now++; nearbyCache.clear(); stuns.expire(now); }
    void register(UUID id) { states.putIfAbsent(id, new State(settings())); }
    void remove(UUID id) {
        BossBar bar = bossBars.remove(id);
        Set<UUID> viewers = bossBarViewers.remove(id);
        if (bar != null && viewers != null) viewers.forEach(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.hideBossBar(bar);
        });
        State state = states.remove(id);
        if (state != null) {
            state.stuns.forEach(playerId -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) player.setFallDistance(0);
                stuns.remove(playerId);
            });
            state.stuns.clear();
        }
    }
    void close() { new ArrayList<>(states.keySet()).forEach(this::remove); stuns.ends.clear(); }

    void updateBossBar(LivingEntity mob) {
        UUID id = mob.getUniqueId();
        BossBar bar = bossBars.computeIfAbsent(id, ignored -> BossBar.bossBar(
                Component.text("Байкер Гарри Моно", NamedTextColor.RED), 1, BossBar.Color.RED, BossBar.Overlay.PROGRESS));
        double maxHealth = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        bar.progress((float) Math.max(0, Math.min(1, mob.getHealth() / maxHealth)));
        bar.name(Component.text("Байкер Гарри Моно", NamedTextColor.RED));
        Set<UUID> viewers = bossBarViewers.computeIfAbsent(id, ignored -> new HashSet<>());
        Location bossLocation = mob.getLocation();
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR
                    || !inBossBarRange(bossLocation, player.getLocation())) {
                if (viewers.remove(player.getUniqueId())) player.hideBossBar(bar);
                continue;
            }
            if (viewers.add(player.getUniqueId())) player.showBossBar(bar);
        }
        for (UUID playerId : new HashSet<>(viewers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(mob.getWorld())) {
                if (viewers.remove(playerId) && player != null) player.hideBossBar(bar);
            }
        }
    }

    static boolean inBossBarRange(Location boss, Location player) {
        return Objects.equals(boss.getWorld(), player.getWorld())
                && boss.toVector().distanceSquared(player.toVector()) <= 100 * 100;
    }

    static boolean spinPullEnabled(int activeTicks) { return activeTicks >= 20; }
    static double spinPullDistance(double distance, double stopRadius) { return Math.max(0, distance - stopRadius); }

    void tick(Mob mob) {
        State s = states.get(mob.getUniqueId());
        if (s == null) return;
        if (!mob.isValid() || mob.isDead()) { remove(mob.getUniqueId()); return; }
        tickBombs(mob, s);
        tickZones(mob, s);
        HarrySettings current = settings();
        Player target = players(mob.getLocation(), current.number("detection-range", 32, 0, 128)).stream()
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
        if (s.combat.attack == null) {
            if (target == null) { plugin.stopMoving(mob); return; }
            RockFrostPlugin.faceTarget(mob, target);
            if (s.combat.tick(true)) {
                HarryCombat.Attack attack = HarryCombat.choose(current.weights(), ThreadLocalRandom.current().nextDouble());
                if (attack != null) { start(mob, s, target, attack, current); return; }
                s.combat.finish(current.ticks("special-attack-interval-ticks", 100));
            }
            double range = current.number("melee.range", 2.2, 0, 8);
            if (mob.getLocation().distanceSquared(target.getLocation()) > range * range) plugin.moveToward(mob, target, 1);
            else {
                plugin.stopMoving(mob);
                if (now >= s.nextMelee) {
                    target.damage(current.number("melee.damage", 6, 0, 10000), mob);
                    s.nextMelee = now + current.ticks("melee.cooldown-ticks", 20);
                    effect(mob.getLocation(), current, "melee");
                }
            }
            return;
        }
            if (!s.combat.active && target == null) {
                s.combat.finish(settings().ticks("special-attack-interval-ticks", 100));
                plugin.stopMoving(mob);
                return;
            }
        switch (s.combat.attack) {
            case RAM -> ram(mob, s, target);
            case OIL -> { throwOil(mob, s, target); finish(mob, s); }
            case SPIN -> spin(mob, s);
            case JUMP -> jump(mob, s, target);
        }
    }

    private void start(Mob mob, State s, Player target, HarryCombat.Attack attack, HarrySettings cfg) {
        s.cfg = cfg;
        s.origin = mob.getLocation();
        s.hits.clear();
        s.travelled = 0;
        s.airborne = false;
        int preparation = switch (attack) {
            case RAM -> cfg.ticks("ram.windup-ticks", 20);
            case SPIN -> cfg.ticks("spin.windup-ticks", 20);
            case JUMP -> cfg.ticks("jump.prepare-timeout-ticks", 60);
            case OIL -> 1;
        };
        s.combat.begin(attack, preparation);
        plugin.stopMoving(mob);
        effect(mob.getLocation(), cfg, attack.name().toLowerCase(Locale.ROOT) + "-prepare");
    }

    private void finish(Mob mob, State s) {
        s.combat.finish(settings().ticks("special-attack-interval-ticks", 100));
        mob.setVelocity(new Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
        plugin.stopMoving(mob);
    }

    private void hold(Mob mob, State s) {
        plugin.stopMoving(mob);
        mob.setVelocity(new Vector());
        if (loaded(s.origin) && mob.getWorld().equals(s.origin.getWorld())
                && mob.getLocation().distanceSquared(s.origin) > .0001) {
            Location at = s.origin.clone();
            at.setYaw(mob.getLocation().getYaw());
            at.setPitch(mob.getLocation().getPitch());
            mob.teleport(at);
        }
    }

    private void ram(Mob mob, State s, Player target) {
        if (!s.combat.active) {
            hold(mob, s);
            RockFrostPlugin.faceTarget(mob, target);
            if (!s.combat.advance()) return;
                s.direction = horizontal(target.getLocation().toVector().subtract(mob.getLocation().toVector()));
            s.combat.active(12000);
            effect(mob.getLocation(), s.cfg, "ram-start");
        }
        double limit = s.cfg.number("ram.max-distance", 24, 0, 128);
        double length = Math.min(s.cfg.number("ram.speed-per-tick", 2.5, .05, 8), limit - s.travelled);
        if (length <= 0 || s.combat.advance()) { finish(mob, s); return; }
        Location from = mob.getLocation();
        Vector step = s.direction.clone().multiply(length);
        // Substeps respect the mob's full bounding box and stop before touching a wall.
        double allowed = 0;
        int parts = (int) Math.ceil(length / .15);
        for (int i = 1; i <= parts; i++) {
            Location next = from.clone().add(step.clone().multiply((double) i / parts));
            if (!clearBody(mob, next)) break;
            allowed = length * i / parts;
        }
        Vector movement = s.direction.clone().multiply(allowed);
        double radius = s.cfg.number("ram.hitbox-radius", .7, 0, 4);
        for (Player player : players(from, length + radius + 2)) {
            if (!crosses(from, movement, player.getBoundingBox(), radius) || !s.hits.add(player.getUniqueId())) continue;
            player.damage(s.cfg.number("ram.damage", 16, 0, 10000), mob);
            player.setVelocity(s.direction.clone().multiply(s.cfg.number("ram.knockback", 1.8, 0, 5))
                    .setY(s.cfg.number("ram.knockback-y", .5, 0, 3)));
            effect(player.getLocation(), s.cfg, "ram-hit");
        }
        double spacing = s.cfg.number("ram.trail-spacing", .5, .25, 8);
        for (double distance = 0; distance <= allowed; distance += spacing) {
            Location ground = ground(from.clone().add(s.direction.clone().multiply(distance)), 3);
            if (ground != null && s.zones.size() < 1024) s.zones.add(new Zone(ground, false, s.cfg,
                    now + s.cfg.ticks("ram.trail-duration-ticks", 100)));
        }
        Location destination = from.clone().add(movement);
        for (Iterator<Zone> it = s.zones.iterator(); it.hasNext();) {
            Zone zone = it.next();
            if (!zone.oil || !zone.at.getWorld().equals(from.getWorld())) continue;
            double oilRadius = zone.cfg.number("oil-bomb.radius", 3, 0, 16);
            if (!crosses(from, movement, BoundingBox.of(zone.at, oilRadius, .7, oilRadius), radius)) continue;
            for (Player player : players(zone.at, oilRadius)) player.setFireTicks(Math.max(player.getFireTicks(), zone.cfg.ticks("oil-bomb.burn-duration-ticks", 100)));
            effect(zone.at, zone.cfg, "oil-ignite");
            it.remove();
        }
        mob.teleport(destination);
        mob.setVelocity(new Vector());
        s.travelled += allowed;
        if (allowed < length || s.travelled >= limit) finish(mob, s);
    }

    private void spin(Mob mob, State s) {
        hold(mob, s);
        if (!s.combat.active) {
            if (!s.combat.advance()) return;
            s.combat.active(s.cfg.ticks("spin.duration-ticks", 200));
            s.spinActiveAt = now;
            effect(mob.getLocation(), s.cfg, "spin-start");
        }
        mob.setRotation(mob.getLocation().getYaw() + (float) s.cfg.number("spin.degrees-per-tick", 40, 0, 360), 0);
        double radius = s.cfg.number("spin.radius", 8, 0, 32);
        int activeTicks = (int) (now - s.spinActiveAt);
        for (Player player : players(mob.getLocation(), radius)) {
            if (spinPullEnabled(activeTicks)) {
                Vector delta = mob.getLocation().toVector().subtract(player.getLocation().toVector());
                double distance = delta.length();
                double pullDistance = spinPullDistance(distance, 1.5);
                if (pullDistance > 0) {
                    Vector velocity = delta.clone().normalize().multiply(Math.min(
                            s.cfg.number("spin.pull-strength", .35, 0, 2), pullDistance / 2));
                    player.setVelocity(new Vector(velocity.getX(), player.getVelocity().getY(), velocity.getZ()));
                }
            }
            if (s.spinHits.allow(player.getUniqueId(), now, s.cfg.ticks("spin.damage-period-ticks", 20)))
                player.damage(s.cfg.number("spin.damage", 2, 0, 10000), mob);
        }
        if (now % 4 == 0) effect(mob.getLocation(), s.cfg, "spin-loop");
        if (s.combat.advance()) finish(mob, s);
    }

    private void jump(Mob mob, State s, Player target) {
        if (!s.combat.active) {
            double distance = s.cfg.number("jump.keep-distance", 8, 0, 32);
            plugin.maintainDistance(mob, target, distance);
            RockFrostPlugin.faceTarget(mob, target);
            s.combat.advance();
            if (Math.abs(mob.getLocation().distance(target.getLocation()) - distance) > 1
                    && s.combat.remaining() > 0) return;
            plugin.stopMoving(mob);
            Vector delta = target.getLocation().toVector().subtract(mob.getLocation().toVector());
            s.direction = horizontal(delta).multiply(s.cfg.number("jump.horizontal-speed", .6, 0, 3));
            mob.setVelocity(s.direction.clone().setY(s.cfg.number("jump.vertical-speed", 1.2, .1, 3)));
            s.combat.active(s.cfg.ticks("jump.max-flight-ticks", 120));
            effect(mob.getLocation(), s.cfg, "jump-start");
            return;
        }
        if (!mob.isOnGround()) s.airborne = true;
        if (s.airborne && mob.isOnGround()) {
            Location at = mob.getLocation();
            for (Player player : players(at, s.cfg.number("jump.radius", 5, 0, 32))) {
                player.damage(s.cfg.number("jump.damage", 10, 0, 10000), mob);
                stuns.add(player.getUniqueId(), now, s.cfg.ticks("jump.stun-duration-ticks", 40));
                s.stuns.add(player.getUniqueId());
                player.setFallDistance(0);
                Vector landedAt = player.getLocation().toVector();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && player.getLocation().toVector().distanceSquared(landedAt) < 1) player.setFallDistance(0);
                }, s.cfg.ticks("jump.stun-duration-ticks", 40) + 1L);
            }
            effect(at, s.cfg, "jump-land");
            finish(mob, s);
        } else if (s.combat.advance()) finish(mob, s);
    }

    private void throwOil(Mob mob, State s, Player target) {
        if (target == null || s.bombs.size() >= 32) return;
        Location from = mob.getEyeLocation();
        Vector delta = target.getLocation().toVector().subtract(from.toVector());
        double gravity = s.cfg.number("oil-bomb.gravity", .03, 0, 1);
        double[] arc = ProjectileMath.highArc(delta.getX(), delta.getY(), delta.getZ(),
                s.cfg.number("oil-bomb.speed", 1.1, .1, 4), gravity);
        s.bombs.add(new Bomb(from, new Vector(arc[0], arc[1], arc[2]), s.cfg, now));
        effect(from, s.cfg, "oil-throw");
    }

    private void tickBombs(Mob owner, State state) {
        for (Iterator<Bomb> it = state.bombs.iterator(); it.hasNext();) {
            Bomb b = it.next();
            boolean expired = now - b.created >= b.cfg.ticks("oil-bomb.max-flight-ticks", 160)
                    || b.travelled >= b.cfg.number("oil-bomb.max-distance", 50, 0, 128);
            Location impact = null;
            Vector step = b.velocity;
            int parts = Math.max(1, (int) Math.ceil(step.length() / .15));
            for (int i = 1; !expired && i <= parts; i++) {
                Location next = b.at.clone().add(step.clone().multiply((double) i / parts));
                if (!loaded(next)) { expired = true; break; }
                double hitRadius = b.cfg.number("oil-bomb.hitbox-radius", .3, 0, 2);
                if (!clearProjectile(b.at, next, hitRadius)) { impact = next; break; }
                Vector segment = next.toVector().subtract(b.at.toVector());
                for (Player player : players(next, segment.length() + hitRadius + 2)) {
                    if (crosses(b.at, segment, player.getBoundingBox(), hitRadius)) { impact = next; break; }
                }
                if (impact != null) break;
            }
            if (expired || impact != null) {
                it.remove();
                if (impact != null) {
                    Location ground = ground(impact, 16);
                    if (ground != null && state.zones.size() < 1024) {
                        Zone zone = new Zone(ground, true, b.cfg, now + b.cfg.ticks("oil-bomb.duration-ticks", 300));
                        state.zones.add(zone);
                        affectOil(owner, zone);
                        effect(ground, b.cfg, "oil-impact");
                    }
                }
                continue;
            }
            b.at.add(step);
            b.travelled += step.length();
            b.velocity = step.clone().setY(step.getY() - b.cfg.number("oil-bomb.gravity", .03, 0, 1));
            effect(b.at, b.cfg, "oil-flight");
        }
    }

    private void tickZones(Mob owner, State state) {
        for (Iterator<Zone> it = state.zones.iterator(); it.hasNext();) {
            Zone zone = it.next();
            if (now >= zone.expires || !loaded(zone.at) || !owner.getWorld().equals(zone.at.getWorld())) { it.remove(); continue; }
            if (zone.oil) affectOil(owner, zone);
            if (now % zone.cfg.ticks("zone-particle-period-ticks", 5) == 0) {
                effect(zone.at, zone.cfg, zone.oil ? "oil-pool" : "ram-trail");
                if (zone.oil) {
                    double radius = zone.cfg.number("oil-bomb.radius", 3, 0, 16);
                    for (int i = 0; i < 12; i++) {
                        double angle = 2 * Math.PI * i / 12;
                        plugin.particles(zone.at.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), zone.cfg.config, "particles.oil-pool");
                    }
                }
            }
            if (!zone.oil && now % zone.cfg.ticks("ram.trail-damage-period-ticks", 20) == 0) {
                double radius = zone.cfg.number("ram.trail-radius", .7, 0, 8);
                for (Player player : players(zone.at, radius)) {
                    if (Math.abs(player.getLocation().getY() - zone.at.getY()) <= 1.2
                            && state.trailHits.allow(player.getUniqueId(), now,
                            zone.cfg.ticks("ram.trail-damage-period-ticks", 20)))
                        player.damage(zone.cfg.number("ram.trail-damage", 2, 0, 10000), owner);
                }
            }
        }
    }

    private void affectOil(Mob owner, Zone zone) {
        for (Player player : zonePlayers(zone.at, zone.cfg.number("oil-bomb.radius", 3, 0, 16))) {
            if (zone.touched.add(player.getUniqueId())) player.damage(zone.cfg.number("oil-bomb.initial-damage", 6, 0, 10000), owner);
            if (zone.slowHits.allow(player.getUniqueId(), now, zone.cfg.ticks("oil-bomb.slow-refresh-ticks", 5)))
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, zone.cfg.ticks("oil-bomb.slow-duration-ticks", 15),
                        (int) zone.cfg.number("oil-bomb.slow-amplifier", 2, 0, 10)));
        }
    }

    private void effect(Location at, HarrySettings cfg, String name) {
        plugin.playSound(at, cfg.config, "sounds." + name);
        plugin.particles(at, cfg.config, "particles." + name);
    }
    private static boolean loaded(Location at) {
        return at.getWorld() != null && at.getY() >= at.getWorld().getMinHeight() && at.getY() < at.getWorld().getMaxHeight()
                && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
    private static Location ground(Location at, int depth) {
        for (int y = 1; y >= -depth; y--) {
            Location sample = at.clone().add(0, y, 0);
            Location candidate = new Location(at.getWorld(), sample.getBlockX() + .5, sample.getBlockY(), sample.getBlockZ() + .5);
            if (!loaded(candidate) || candidate.getBlockY() <= candidate.getWorld().getMinHeight()) continue;
            if (candidate.getBlock().isPassable() && candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid())
                return candidate.add(0, .05, 0);
        }
        return null;
    }
    private static boolean clearBody(Mob mob, Location at) {
        BoundingBox box = mob.getBoundingBox().shift(at.toVector().subtract(mob.getLocation().toVector()));
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++)
            for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                if (!at.getWorld().isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = (int) Math.floor(box.getMinY() + .01); y <= (int) Math.floor(box.getMaxY() - .01); y++) {
                    if (y < at.getWorld().getMinHeight() || y >= at.getWorld().getMaxHeight()
                            || !at.getWorld().getBlockAt(x, y, z).isPassable()) return false;
                }
            }
        return true;
    }
    static boolean crosses(Location from, Vector movement, BoundingBox box, double radius) {
        BoundingBox expanded = box.clone().expand(radius);
        Vector start = from.toVector();
        return expanded.contains(start) || movement.lengthSquared() > 1e-9
                && expanded.rayTrace(start, movement.clone().normalize(), movement.length()) != null;
    }
    private static Vector horizontal(Vector vector) {
        vector.setY(0);
        return vector.lengthSquared() < 1e-9 ? new Vector(1, 0, 0) : vector.normalize();
    }
    private static boolean eligible(Player p) { return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR; }
    private List<Player> players(Location at, double radius) {
        double bounded = Math.max(0, Math.min(128, radius));
        UUID key = at.getWorld().getUID();
        List<Player> cached = nearbyCache.computeIfAbsent(key, ignored -> at.getWorld().getPlayers().stream()
                .filter(HarryMonoController::eligible).toList());
        List<Player> result = new ArrayList<>();
        for (Player player : cached) {
            Location p = player.getLocation();
            if (Math.abs(p.getX() - at.getX()) <= bounded && Math.abs(p.getY() - at.getY()) <= bounded
                    && Math.abs(p.getZ() - at.getZ()) <= bounded && p.distanceSquared(at) <= bounded * bounded) result.add(player);
        }
        return result;
    }
    private List<Player> zonePlayers(Location at, double radius) {
        List<Player> result = new ArrayList<>();
        for (Player p : players(at, radius)) {
            Location position = p.getLocation();
            if (Math.abs(position.getY() - at.getY()) <= 1.2) result.add(p);
        }
        return result;
    }
    private static boolean clearProjectile(Location from, Location to, double radius) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        int steps = Math.max(1, (int) Math.ceil(length / .15));
        for (int i = 1; i <= steps; i++) {
            Location center = from.clone().add(delta.clone().multiply((double) i / steps));
            int minX = (int) Math.floor(center.getX() - radius), maxX = (int) Math.floor(center.getX() + radius);
            int minY = (int) Math.floor(center.getY() - radius), maxY = (int) Math.floor(center.getY() + radius);
            int minZ = (int) Math.floor(center.getZ() - radius), maxZ = (int) Math.floor(center.getZ() + radius);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                if (!center.getWorld().isChunkLoaded(x >> 4, z >> 4)) return false;
                for (BoundingBox shape : center.getWorld().getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes()) {
                    if (shape.shift(new Vector(x, y, z)).expand(radius).contains(center.toVector())) return false;
                }
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        State s = states.get(e.getEntity().getUniqueId());
        if (s != null && s.combat.active && (s.combat.attack == HarryCombat.Attack.SPIN
                || s.combat.attack == HarryCombat.Attack.JUMP && e.getCause() == EntityDamageEvent.DamageCause.FALL)) e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent attack) {
            UUID attacker = attack.getDamager() instanceof Projectile p && p.getShooter() instanceof Player shooter
                    ? shooter.getUniqueId() : attack.getDamager().getUniqueId();
            if (stuns.contains(attacker, now)) e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent e) {
        State s = states.get(e.getEntity().getUniqueId());
        if (s != null && s.combat.active && s.combat.attack == HarryCombat.Attack.SPIN) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent || !stuns.contains(e.getPlayer().getUniqueId(), now) || e.getTo() == null) return;
        Location destination = e.getTo();
        if (e.getFrom().getX() == destination.getX() && e.getFrom().getY() == destination.getY() && e.getFrom().getZ() == destination.getZ()) return;
        Location fixed = e.getFrom().clone();
        fixed.setYaw(destination.getYaw()); fixed.setPitch(destination.getPitch());
        e.setTo(fixed);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) { clearStun(e.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { forget(e.getPlayer().getUniqueId()); }
    @EventHandler public void onPlayerDeath(PlayerDeathEvent e) { forget(e.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) { if (stuns.contains(e.getPlayer().getUniqueId(), now)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) { if (stuns.contains(e.getPlayer().getUniqueId(), now)) e.setCancelled(true); }
    private void forget(UUID id) {
        clearStun(id);
        for (State state : states.values()) {
            state.hits.remove(id); state.trailHits.remove(id); state.spinHits.remove(id); state.stuns.remove(id);
            for (Zone zone : state.zones) { zone.touched.remove(id); zone.slowHits.remove(id); }
        }
    }
    private void clearStun(UUID id) {
        stuns.remove(id);
        for (State state : states.values()) if (state.stuns.remove(id)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.setFallDistance(0);
        }
    }

    static final class Stuns {
        private final Map<UUID, Long> ends = new HashMap<>();
        void add(UUID id, long now, int duration) { ends.merge(id, now + duration, Math::max); }
        boolean contains(UUID id, long now) { return ends.getOrDefault(id, Long.MIN_VALUE) > now; }
        void remove(UUID id) { ends.remove(id); }
        void expire(long now) { ends.values().removeIf(end -> end <= now); }
    }
    private static final class State {
        final HarryCombat combat;
        HarrySettings cfg;
        final Set<UUID> hits = new HashSet<>();
        final HarryCombat.HitWindow trailHits = new HarryCombat.HitWindow(), spinHits = new HarryCombat.HitWindow();
        final List<Zone> zones = new ArrayList<>();
        final List<Bomb> bombs = new ArrayList<>();
        final Set<UUID> stuns = new HashSet<>();
        Location origin;
        Vector direction;
        double travelled;
        boolean airborne;
        long nextMelee;
        long spinActiveAt;
        State(HarrySettings cfg) { this.cfg = cfg; combat = new HarryCombat(cfg.ticks("special-attack-interval-ticks", 100)); }
    }
    private static final class Zone {
        final Location at;
        final boolean oil;
        final HarrySettings cfg;
        final long expires;
        final Set<UUID> touched = new HashSet<>();
        final HarryCombat.HitWindow slowHits = new HarryCombat.HitWindow();
        Zone(Location at, boolean oil, HarrySettings cfg, long expires) { this.at = at; this.oil = oil; this.cfg = cfg; this.expires = expires; }
    }
    private static final class Bomb {
        final Location at;
        Vector velocity;
        final HarrySettings cfg;
        final long created;
        double travelled;
        Bomb(Location at, Vector velocity, HarrySettings cfg, long created) {
            this.at = at; this.velocity = velocity; this.cfg = cfg; this.created = created;
        }
    }
}
