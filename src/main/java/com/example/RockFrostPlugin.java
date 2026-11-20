package com.example;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.*;

public final class RockFrostPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final List<String> MOB_TYPES = List.of("skelebass", "dancebomber", "pumpkin", "pumpkin_soul", "pumpkin_brute", "pumpkin_poison", "harry_mono");
    private HarryMonoController harry;
    private NamespacedKey mobKey;
    private final Map<UUID, MobData> mobs = new HashMap<>();
    private final Set<String> pendingDeathEntries = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        mobKey = new NamespacedKey(this, "rockfrost-mob");
        harry = new HarryMonoController(this);
        getServer().getPluginManager().registerEvents(harry, this);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("custommob")).setExecutor(this);
        Objects.requireNonNull(getCommand("custommob")).setTabCompleter(this);
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) registerMob(entity);
        new BukkitRunnable() { @Override public void run() { tickMobs(); } }.runTaskTimer(this, 1, 1);
    }
    @Override public void onDisable() {
        if (harry != null) harry.close();
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("rockfrost_hidden");
        if (team != null) pendingDeathEntries.forEach(team::removeEntry);
        pendingDeathEntries.clear();
        mobs.clear();
    }

    private void registerMob(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.isDead() || mobs.containsKey(mob.getUniqueId())) return;
        String type = mob.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (type == null || !MOB_TYPES.contains(type)) return;
        if (mob instanceof Zombie zombie && type.startsWith("pumpkin")) zombie.setShouldBurnInDay(false);
        Bukkit.getMobGoals().removeAllGoals(mob);
        mob.setTarget(null);
        mob.getPathfinder().stopPathfinding();
        if (type.equals("dancebomber")) setBomberPhase(mob, false);
        mobs.put(mob.getUniqueId(), new MobData(type));
        if (type.equals("harry_mono")) {
            if (mob instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
            harry.register(mob.getUniqueId());
        }
    }

    @EventHandler public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) registerMob(entity);
    }

    @EventHandler public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) { mobs.remove(entity.getUniqueId()); harry.remove(entity.getUniqueId()); }
    }

    private void tickMobs() {
        harry.pulse();
        for (Iterator<Map.Entry<UUID, MobData>> it = mobs.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, MobData> entry = it.next();
            MobData data = entry.getValue(); Entity mob = Bukkit.getEntity(entry.getKey());
            if (!(mob instanceof LivingEntity living) || living.isDead()) { harry.remove(entry.getKey()); it.remove(); continue; }
            String speedPath = data.type.equals("dancebomber")
                    ? (data.headlessTicks > 0 ? "mobs.dancebomber.headless-movement-speed" : "mobs.dancebomber.normal-movement-speed")
                    : "mobs." + data.type + ".movement-speed";
            double speed = cfg(speedPath, .25);
            if (!Double.isFinite(speed) || speed < 0 || data.type.equals("harry_mono") && speed > 1.2) speed = data.type.equals("harry_mono") ? .32 : .25;
            living.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
            if (living.getTicksLived() % 100 == 0 && Math.random() < 0.5) playSound(living.getLocation(), "mobs." + data.type + ".sounds.ambient");
            if (data.type.equals("skelebass")) tickBass(living, data);
            else if (data.type.equals("dancebomber")) tickBomber(living, data);
            else if (data.type.equals("harry_mono") && living instanceof Mob creature) {
                harry.updateBossBar(living);
                harry.tick(creature);
            }
            else tickPumpkin(living, data);
        }
    }

    private void tickBass(LivingEntity mob, MobData data) {
        Player target = nearestPlayer(mob, 32); if (target == null) { stopMoving(mob); return; }
        faceTarget(mob, target);
        double desiredDistance = cfg("mobs.skelebass.keep-distance", 12);
        maintainDistance(mob, target, desiredDistance);
        if (isRetreating(mob.getLocation().distance(target.getLocation()), desiredDistance)) return;
        if (--data.cooldown <= 0) { data.cooldown = intCfg("mobs.skelebass.attack-cooldown-ticks", 60); shootBass(mob, target); }
    }

    static boolean isRetreating(double distance, double desiredDistance) {
        return distance < desiredDistance - 0.5;
    }

    private void tickBomber(LivingEntity mob, MobData data) {
        Player target = nearestPlayer(mob, 32);
        if (target == null) {
            stopMoving(mob);
            if (data.headlessTicks > 0 && --data.headlessTicks == 0) { data.meleeCooldown = 0; setBomberPhase(mob, false); }
            return;
        }
        faceTarget(mob, target);
        if (data.headlessTicks > 0) {
            data.headlessTicks--;
            if (data.headlessTicks == 0) {
                data.meleeCooldown = 0;
                setBomberPhase(mob, false);
                return;
            }
            double distance = mob.getLocation().distance(target.getLocation());
            if (distance > cfg("mobs.dancebomber.melee-range", 2.2)) moveToward(mob, target, 1.0);
            else stopMoving(mob);
            if (data.meleeCooldown > 0) data.meleeCooldown--;
            else if (distance <= cfg("mobs.dancebomber.melee-range", 2.2)) {
                target.damage(cfg("mobs.dancebomber.melee-damage", 6), mob);
                data.meleeCooldown = intCfg("mobs.dancebomber.melee-cooldown-ticks", 20);
            }
            return;
        }
        maintainDistance(mob, target, cfg("mobs.dancebomber.keep-distance", 10));
        if (--data.cooldown <= 0) { data.cooldown = intCfg("mobs.dancebomber.throw-cooldown-ticks", 40); throwBomb(mob, target, data); }
    }

    private void tickPumpkin(LivingEntity mob, MobData data) {
        String path = "mobs." + data.type;
        Player target = nearestPlayer(mob, Math.max(0, cfg(path + ".detection-range", 32)));
        if (target == null) { stopMoving(mob); return; }
        faceTarget(mob, target);
        if (data.type.equals("pumpkin_soul")) {
            maintainDistance(mob, target, cfg(path + ".keep-distance", 10));
            if (--data.cooldown <= 0) {
                data.cooldown = Math.max(1, intCfg(path + ".attack-cooldown-ticks", 60));
                new SoulProjectileTask(mob, target).runTaskTimer(this, 1, 1);
            }
            return;
        }
        double distance = mob.getLocation().distance(target.getLocation());
        if (distance > cfg(path + ".melee-range", 2.2)) moveToward(mob, target, 1.0);
        else stopMoving(mob);
        if (data.meleeCooldown > 0) data.meleeCooldown--;
        else if (distance <= cfg(path + ".melee-range", 2.2)) {
            target.damage(cfg(path + ".melee-damage", 4), mob);
            data.meleeCooldown = Math.max(1, intCfg(path + ".melee-cooldown-ticks", 20));
        }
    }

    private void shootBass(LivingEntity mob, Player target) {
        playSound(mob.getLocation(), "mobs.skelebass.sounds.shoot");
        new BassProjectileTask(mob.getLocation().add(0, 1.3, 0), mob, target).runTaskTimer(this, 1, 1);
    }
    private void throwBomb(LivingEntity mob, Player target, MobData data) {
        playSound(mob.getLocation(), "mobs.dancebomber.sounds.throw-head"); particles(mob.getLocation().add(0, 1.5, 0), "mobs.dancebomber.particles.throw-head");
        setBomberPhase(mob, true); data.headlessTicks = intCfg("mobs.dancebomber.headless-duration-ticks", 100);
        Location start = mob.getLocation().add(0, 1.5, 0);
        Material material;
        try {
            material = Material.valueOf(getConfig().getString("mobs.dancebomber.bomb-projectile.material", "WOODEN_HOE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid bomb projectile material; using WOODEN_HOE");
            material = Material.WOODEN_HOE;
        }
        ItemStack bombItem = new ItemStack(material);
        ItemMeta meta = bombItem.getItemMeta();
        String model = getConfig().getString("mobs.dancebomber.bomb-projectile.custom-model-data-string", "").trim();
        if (!model.isEmpty()) {
            var component = meta.getCustomModelDataComponent();
            component.setStrings(List.of(model));
            meta.setCustomModelDataComponent(component);
        }
        String itemModel = getConfig().getString("mobs.dancebomber.bomb-projectile.item-model", "").trim();
        if (!itemModel.isEmpty()) {
            NamespacedKey key = NamespacedKey.fromString(itemModel.contains(":") ? itemModel : "minecraft:" + itemModel);
            if (key != null) meta.setItemModel(key);
        }
        bombItem.setItemMeta(meta);
        ItemDisplay display = (ItemDisplay) start.getWorld().spawnEntity(start, EntityType.ITEM_DISPLAY);
        display.setItemStack(bombItem);
        new ProjectileTask(start, mob, target, "bomb", display).runTaskTimer(this, 1, 1);
    }

    private void setBomberPhase(LivingEntity mob, boolean headless) {
        stopMoving(mob);
        String path = headless ? "mobs.dancebomber.custom-name-headless" : "mobs.dancebomber.custom-name-normal";
        mob.setCustomName(getConfig().getString(path));
        mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(cfg(headless ? "mobs.dancebomber.headless-movement-speed" : "mobs.dancebomber.normal-movement-speed", .25));
    }

    private final class SoulProjectileTask extends BukkitRunnable {
        private final LivingEntity shooter;
        private final Location location;
        private final Vector direction;
        private double travelled;

        SoulProjectileTask(LivingEntity shooter, Player target) {
            this.shooter = shooter;
            this.location = shooter.getEyeLocation().clone();
            this.direction = target.getEyeLocation().toVector().subtract(location.toVector());
            if (direction.lengthSquared() > 0) direction.normalize();
        }

        @Override public void run() {
            String path = "mobs.pumpkin_soul.projectile";
            double maxDistance = Math.max(0, cfg(path + ".max-distance", 32));
            double step = Math.min(cfg(path + ".speed-per-tick", 0.8), maxDistance - travelled);
            if (!shooter.isValid() || location.getWorld() == null || step <= 0 || direction.lengthSquared() == 0) { cancel(); return; }
            Vector start = location.toVector();
            double firstHit = step + 1;
            Player victim = null;
            if (location.getBlock().getType().isSolid()) firstHit = 0;
            else {
                var block = location.getWorld().rayTraceBlocks(location, direction, step, FluidCollisionMode.NEVER, true);
                if (block != null) firstHit = block.getHitPosition().distance(start);
            }
            double radius = Math.max(0, cfg(path + ".hitbox-radius", .3));
            for (Player player : location.getWorld().getPlayers()) {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;
                var box = player.getBoundingBox().clone().expand(radius);
                var hit = box.contains(start) ? null : box.rayTrace(start, direction, step);
                double distance = box.contains(start) ? 0 : hit == null ? Double.POSITIVE_INFINITY : hit.getHitPosition().distance(start);
                if (distance < firstHit) { firstHit = distance; victim = player; }
            }
            double moved = Math.min(step, firstHit);
            location.add(direction.clone().multiply(moved));
            travelled += moved;
            particles(location, "mobs.pumpkin_soul.particles.projectile-trail");
            if (victim != null) victim.damage(cfg(path + ".damage", 5), shooter);
            if (firstHit <= step || travelled >= maxDistance) cancel();
        }
    }

    private final class BassProjectileTask extends BukkitRunnable {
        private final Location location;
        private final LivingEntity shooter;
        private final Vector direction;
        private final Set<UUID> hit = new HashSet<>();
        private double travelled;

        BassProjectileTask(Location start, LivingEntity shooter, Player target) {
            this.location = start;
            this.shooter = shooter;
            this.direction = target.getEyeLocation().toVector().subtract(start.toVector());
            if (direction.lengthSquared() > 0) direction.normalize();
        }

        @Override public void run() {
            double maxDistance = cfg("mobs.skelebass.projectile.max-distance", 50);
            if (location.getWorld() == null || travelled >= maxDistance || direction.lengthSquared() == 0) { cancel(); return; }
            double step = Math.min(cfg("mobs.skelebass.projectile.speed-per-tick", 1.5), maxDistance - travelled);
            if (step <= 0) { cancel(); return; }
            playSound(location, "mobs.skelebass.sounds.projectile-loop");
            Vector start = location.toVector();
            double radius = Math.max(0, cfg("mobs.skelebass.projectile.hitbox-radius", .8));
            double firstBlockHit = Double.POSITIVE_INFINITY;
            if (location.getBlock().getType().isSolid()) firstBlockHit = 0;
            else {
                org.bukkit.util.RayTraceResult block = location.getWorld().rayTraceBlocks(
                        location, direction, step, FluidCollisionMode.NEVER, true);
                if (block != null) firstBlockHit = block.getHitPosition().distanceSquared(start);
            }
            List<Map.Entry<Player, Double>> collisions = new ArrayList<>();
            for (Player player : location.getWorld().getPlayers()) {
                if (player.isDead() || hit.contains(player.getUniqueId())) continue;
                org.bukkit.util.BoundingBox box = player.getBoundingBox().clone().expand(radius);
                if (box.contains(start)) collisions.add(Map.entry(player, 0.0));
                else {
                    org.bukkit.util.RayTraceResult result = box.rayTrace(start, direction, step);
                    if (result != null) {
                        double distance = result.getHitPosition().distanceSquared(start);
                        if (distance < firstBlockHit) collisions.add(Map.entry(player, distance));
                    }
                }
            }
            collisions.sort(Comparator.comparingDouble(Map.Entry::getValue));
            if (firstBlockHit <= step * step) {
                location.add(direction.clone().multiply(Math.sqrt(firstBlockHit)));
                travelled += Math.sqrt(firstBlockHit);
                particles(location, "mobs.skelebass.particles.projectile-trail");
                for (Map.Entry<Player, Double> collision : collisions) {
                    if (collision.getValue() >= firstBlockHit) break;
                    Player player = collision.getKey();
                    hit.add(player.getUniqueId());
                    player.damage(cfg("mobs.skelebass.projectile.damage", 8), shooter);
                    player.setVelocity(direction.clone().multiply(cfg("mobs.skelebass.projectile.knockback-strength", 1.8)));
                    if (!getConfig().getBoolean("mobs.skelebass.projectile.pierce-entities", false)) break;
                }
                cancel();
                return;
            }
            boolean pierce = getConfig().getBoolean("mobs.skelebass.projectile.pierce-entities", false);
            for (Map.Entry<Player, Double> collision : collisions) {
                Player player = collision.getKey();
                hit.add(player.getUniqueId());
                player.damage(cfg("mobs.skelebass.projectile.damage", 8), shooter);
                player.setVelocity(direction.clone().multiply(cfg("mobs.skelebass.projectile.knockback-strength", 1.8)));
                if (!pierce) { cancel(); return; }
            }
            location.add(direction.clone().multiply(step));
            travelled += step;
            particles(location, "mobs.skelebass.particles.projectile-trail");
            if (travelled >= maxDistance) cancel();
        }
    }

    static void applyBombExplosion(LivingEntity living, UUID ownerId, Location at, double knockbackStrength, double damage) {
        if (living.getUniqueId().equals(ownerId)) return;
        Vector knockback = living.getLocation().toVector().subtract(at.toVector());
        if (knockback.lengthSquared() > 0) living.setVelocity(knockback.normalize().multiply(knockbackStrength));
        living.damage(damage);
    }

    private final class ProjectileTask extends BukkitRunnable {
        private Location location;
        private final UUID ownerId;
        private final Player target;
        private final String kind;
        private final ItemDisplay display;
        private final Vector velocity;
        private double travelled;

        ProjectileTask(Location start, LivingEntity owner, Player target, String kind, ItemDisplay display) {
            this.location = start;
            this.ownerId = owner.getUniqueId();
            this.target = target;
            this.kind = kind;
            this.display = display;
            double speed = cfg(kind.equals("bass") ? "mobs.skelebass.projectile.speed-per-tick" : "mobs.dancebomber.bomb-projectile.speed", 1.1);
            Vector direction = target.getEyeLocation().toVector().subtract(start.toVector());
            double gravity = cfg("mobs.dancebomber.bomb-projectile.gravity", .03);
            double[] arc = ProjectileMath.highArc(direction.getX(), direction.getY(), direction.getZ(), speed, gravity);
            this.velocity = new Vector(arc[0], arc[1], arc[2]);
        }

        @Override public void run() {
            double maxDistance = cfg(kind.equals("bass") ? "mobs.skelebass.projectile.max-distance" : "mobs.dancebomber.bomb-projectile.max-distance", 50);
            if (!target.isOnline() || target.isDead() || location.getWorld() == null || !location.getWorld().equals(target.getWorld()) || travelled > maxDistance) {
                removeAndCancel();
                return;
            }
            if (kind.equals("bomb")) velocity.setY(velocity.getY() - cfg("mobs.dancebomber.bomb-projectile.gravity", .03));
            double step = Math.min(velocity.length(), Math.max(0, maxDistance - travelled));
            Vector start = location.toVector();
            Vector direction = velocity.lengthSquared() > 0 ? velocity.clone().normalize() : new Vector();
            double firstHit = Double.POSITIVE_INFINITY;
            if (location.getBlock().getType().isSolid()) firstHit = 0;
            else if (step > 0) {
                org.bukkit.util.RayTraceResult block = location.getWorld().rayTraceBlocks(location, direction, step, FluidCollisionMode.NEVER, true);
                if (block != null) firstHit = block.getHitPosition().distance(start);
            }
            double radius = Math.max(0, cfg("mobs.dancebomber.bomb-projectile.hitbox-radius", .8));
            for (Player player : location.getWorld().getPlayers()) {
                if (player.isDead()) continue;
                org.bukkit.util.BoundingBox box = player.getBoundingBox().clone().expand(radius);
                if (box.contains(start)) firstHit = 0;
                else if (step > 0) {
                    org.bukkit.util.RayTraceResult hit = box.rayTrace(start, direction, step);
                    if (hit != null) firstHit = Math.min(firstHit, hit.getHitPosition().distance(start));
                }
            }
            boolean collided = firstHit != Double.POSITIVE_INFINITY;
            double distance = collided ? firstHit : step;
            location.add(direction.clone().multiply(distance));
            travelled += distance;
            if (display != null) {
                if (velocity.lengthSquared() > 0) location.setDirection(velocity);
                display.teleport(location);
            }
            String base = kind.equals("bass") ? "mobs.skelebass" : "mobs.dancebomber";
            playSound(location, base + ".sounds." + (kind.equals("bass") ? "projectile-loop" : "flying-head-loop"));
            particles(location, base + ".particles." + (kind.equals("bass") ? "projectile-trail" : "flying-head-trail"));
            if (collided) {
                if (kind.equals("bomb")) explode(location);
                removeAndCancel();
            } else if (travelled >= maxDistance || step == 0) removeAndCancel();
        }

        private void removeAndCancel() {
            if (display != null) display.remove();
            cancel();
        }

        private void explode(Location at) {
            double radius = cfg("mobs.dancebomber.bomb-projectile.explosion-radius", 4.5);
            for (Entity entity : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
                if (entity instanceof LivingEntity living) {
                    applyBombExplosion(living, ownerId, at,
                            cfg("mobs.dancebomber.bomb-projectile.explosion-knockback", 1.2),
                            cfg("mobs.dancebomber.bomb-projectile.explosion-damage", 12));
                }
            }
            playSound(at, "mobs.dancebomber.sounds.explosion"); particles(at, "mobs.dancebomber.particles.explosion");
        }
    }

    /**
     * Spawns a configured custom mob near the supplied location. Must be called on the server thread.
     *
     * @param mobId configured mob ID (case-insensitive)
     * @param location center of the existing safe-location search
     * @return the spawned entity, or null if the ID or location is invalid or no suitable mob can be spawned
     */
    public LivingEntity spawnMob(String mobId, Location location) {
        if (mobId == null || location == null || location.getWorld() == null) return null;
        String type = mobId.toLowerCase(Locale.ROOT);
        if (!MOB_TYPES.contains(type)) return null;
        try {
            return spawn(null, type, location);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Could not spawn " + type + ": " + e.getMessage());
            return null;
        }
    }

    private LivingEntity spawn(CommandSender sender, String type, Location location) {
        String path = "mobs." + type;
        EntityType entityType;
        try { entityType = EntityType.valueOf(getConfig().getString(path + ".entity-type", type.startsWith("pumpkin") || type.equals("harry_mono") ? "ZOMBIE" : "SKELETON")); }
        catch (IllegalArgumentException ex) { entityType = type.equals("harry_mono") ? EntityType.ZOMBIE : EntityType.SKELETON; }
        Location safe = findSpawnLocation(location);
        if (safe == null) { if (sender != null) sender.sendMessage(ChatColor.RED + "Не найдено безопасное место для " + type + "."); return null; }
        Entity entity = safe.getWorld().spawnEntity(safe, entityType);
        if (!(entity instanceof Mob mob)) {
            entity.remove();
            if (sender != null) sender.sendMessage(ChatColor.RED + "Тип " + entityType + " не поддерживает движение моба. Проверьте " + path + ".entity-type.");
            return null;
        }
        makeAdult(mob);
        mob.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, type);
        mob.setRemoveWhenFarAway(false); mob.setCanPickupItems(false); Bukkit.getMobGoals().removeAllGoals(mob);
        if (mob instanceof AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(false);
        if (mob instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
        mob.setSilent(true);
        if (type.equals("harry_mono")) mob.customName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(getConfig().getString(path + ".custom-name", "Байкер Гарри Моно")));
        else mob.setCustomName(getConfig().getString(path + ".custom-name", getConfig().getString(path + ".custom-name-normal")));
        mob.setCustomNameVisible(false);
        var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("rockfrost_hidden");
        if (team == null) team = scoreboard.registerNewTeam("rockfrost_hidden");
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(mob.getUniqueId().toString());
        double health = cfg(path + ".max-health", 40);
        double speed = cfg(path + (type.equals("dancebomber") ? ".normal-movement-speed" : ".movement-speed"), .25);
        if (type.equals("harry_mono")) {
            if (!Double.isFinite(health) || health < 1 || health > 2048) health = 200;
            if (!Double.isFinite(speed) || speed < 0 || speed > 1.2) speed = .32;
        } else {
            if (!Double.isFinite(health) || health < 1) health = 40;
            if (!Double.isFinite(speed) || speed < 0) speed = .25;
        }
        mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health); mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getValue());
        mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
        clearEquipment(mob);
        registerMob(mob); if (sender != null) sender.sendMessage(ChatColor.GREEN + "Создан " + type + ".");
        return mob;
    }

    static void clearEquipment(Mob mob) {
        var equipment = mob.getEquipment();
        if (equipment != null) equipment.clear();
    }

    static void makeAdult(Mob mob) {
        if (mob instanceof Ageable ageable) ageable.setAdult();
    }

    private Location findSpawnLocation(Location origin) {
        World world = origin.getWorld();
        for (int radius = 2; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                for (int y = 1; y >= -2; y--) {
                    Location candidate = origin.clone().add(x, y, z).getBlock().getLocation().add(.5, 0, .5);
                    if (!candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()
                            || !candidate.getBlock().isPassable() || !candidate.clone().add(0, 1, 0).getBlock().isPassable()
                            || !world.getNearbyEntities(candidate.clone().add(0, 1, 0), .7, 1, .7).isEmpty()) continue;
                    Vector direction = origin.toVector().subtract(candidate.toVector());
                    if (direction.lengthSquared() > 0) candidate.setDirection(direction);
                    return candidate;
                }
            }
        }
        return null;
    }

    private Location resolve(CommandSender sender, String value) { if (value == null) return sender instanceof Player p ? p.getLocation() : null; Player p = Bukkit.getPlayerExact(value); return p != null ? p.getLocation() : sender instanceof Player player ? player.getLocation() : null; }
    private Player nearestPlayer(Entity from, double range) { return from.getWorld().getPlayers().stream().filter(p -> !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(from.getLocation()) <= range * range).min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(from.getLocation()))).orElse(null); }
    static void faceTarget(LivingEntity mob, Player target) {
        if (mob instanceof Mob creature) creature.lookAt(target);
    }
    void maintainDistance(LivingEntity mob, Player target, double desiredDistance) {
        double distance = mob.getLocation().distance(target.getLocation());
        if (distance < desiredDistance - 0.5) moveAway(mob, target, 1.0);
        else if (distance > desiredDistance + 0.5) moveToward(mob, target, 1.0);
        else stopMoving(mob);
    }
    void stopMoving(LivingEntity mob) {
        MobData data = mobs.get(mob.getUniqueId());
        if (data != null) data.lastRetreatPosition = null;
        if (mob instanceof Mob creature) creature.getPathfinder().stopPathfinding();
    }
    void moveToward(LivingEntity mob, Player target, double factor) {
        if (!(mob instanceof Mob creature) || mob.getTicksLived() % 10 != 0) return;
        MobData data = mobs.get(mob.getUniqueId());
        if (data != null) data.lastRetreatPosition = null;
        Location targetLocation = target.getLocation();
        for (int radius = 0; radius <= 2; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                if (startGroundPathNear(creature, targetLocation.clone().add(x, 0, z), targetLocation.getBlockY(), factor, null)) return;
            }
        }
        stopMoving(mob);
    }
    private void moveAway(LivingEntity mob, Player target, double factor) {
        if (!(mob instanceof Mob creature) || mob.getTicksLived() % 10 != 0) return;
        Location origin = mob.getLocation();
        Location targetLocation = target.getLocation();
        MobData data = mobs.get(mob.getUniqueId());
        var currentPath = creature.getPathfinder().getCurrentPath();
        boolean moved = data != null && data.lastRetreatPosition != null
                && data.lastRetreatPosition.getWorld().equals(origin.getWorld())
                && data.lastRetreatPosition.distanceSquared(origin) > 0.01;
        if (data != null) data.lastRetreatPosition = origin.clone();
        if (moved && currentPath != null && currentPath.getFinalPoint() != null
                && pathMakesProgress(origin.distanceSquared(targetLocation),
                currentPath.getFinalPoint().distanceSquared(targetLocation), true)) return;
        Vector direction = origin.toVector().subtract(targetLocation.toVector()).setY(0);
        if (direction.lengthSquared() < 0.0001) direction = origin.getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) direction = new Vector(1, 0, 0);
        direction.normalize();
        double[] angles = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2};
        for (double distance : new double[]{6, 4, 2}) for (double angle : angles) {
            Vector offset = direction.clone().rotateAroundY(angle).multiply(distance);
            if (startGroundPathNear(creature, origin.clone().add(offset), origin.getBlockY(), factor, target.getLocation())) return;
        }
        stopMoving(mob);
    }
    static boolean pathMakesProgress(double currentDistanceSquared, double destinationDistanceSquared, boolean retreat) {
        return retreat ? destinationDistanceSquared > currentDistanceSquared : destinationDistanceSquared < currentDistanceSquared;
    }
    private boolean startGroundPathNear(Mob mob, Location location, int baseY, double factor, Location retreatFrom) {
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return false;
        for (int delta : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            int y = baseY + delta;
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
            Location candidate = new Location(world, location.getBlockX() + .5, y, location.getBlockZ() + .5);
            if (retreatFrom != null && candidate.distanceSquared(retreatFrom) <= mob.getLocation().distanceSquared(retreatFrom)) continue;
            if (candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()
                    && candidate.getBlock().isPassable() && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                    && startGroundPath(mob, candidate, factor, retreatFrom)) return true;
        }
        return false;
    }
    private boolean startGroundPath(Mob mob, Location destination, double factor, Location retreatFrom) {
        var path = mob.getPathfinder().findPath(destination);
        if (path == null || path.getFinalPoint() == null) return false;
        if (!path.canReachFinalPoint()
                && path.getFinalPoint().distanceSquared(destination) >= mob.getLocation().distanceSquared(destination) - 1) return false;
        if (retreatFrom != null
                && path.getFinalPoint().distanceSquared(retreatFrom) <= mob.getLocation().distanceSquared(retreatFrom)) return false;
        return mob.getPathfinder().moveTo(path, factor);
    }
    private double cfg(String path, double fallback) { return getConfig().getDouble(path, fallback); }
    private int intCfg(String path, int fallback) { return getConfig().getInt(path, fallback); }
    private void playSound(Location location, String path) {
        playSound(location, getConfig(), path);
    }
    void playSound(Location location, ConfigurationSection config, String path) {
        ConfigurationSection s = config.getConfigurationSection(path);
        if (s == null || location.getWorld() == null) return;
        String name = s.getString("sound", "BLOCK_NOTE_BLOCK_BASS");
        try {
            SoundCategory category = SoundCategory.valueOf(s.getString("category", "HOSTILE").toUpperCase(Locale.ROOT));
            Sound sound = null;
            if (!name.contains(":")) {
                try { sound = Sound.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { }
            }
            double maxDistance = s.getDouble("max-distance", 64);
            for (Player player : location.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(location) > maxDistance * maxDistance) continue;
                for (int i = 0; i < s.getInt("repeat", 1); i++) {
                    if (sound != null) player.playSound(location, sound, category, (float) s.getDouble("volume", 1), (float) s.getDouble("pitch", 1));
                    else player.playSound(location, name, category, (float) s.getDouble("volume", 1), (float) s.getDouble("pitch", 1));
                }
            }
        } catch (IllegalArgumentException e) { getLogger().warning("Invalid sound settings at " + path + ": " + e.getMessage()); }
    }

    private void particles(Location location, String path) {
        particles(location, getConfig(), path);
    }
    void particles(Location location, ConfigurationSection config, String path) {
        if (location.getWorld() == null) return;
        for (Map<?, ?> raw : config.getMapList(path)) {
            try {
                Particle particle = Particle.valueOf(String.valueOf(raw.get("type")).toUpperCase(Locale.ROOT));
                double[] offset = numbers(raw.get("offset"), new double[]{0, 0, 0});
                int count = raw.get("count") instanceof Number n ? n.intValue() : 1;
                double extra = raw.get("extra") instanceof Number n ? n.doubleValue() : 0;
                double yOffset = raw.get("y-offset") instanceof Number n ? n.doubleValue() : 0;
                Object data = null;
                if (particle.getDataType() == Particle.DustTransition.class) {
                    float scale = raw.get("scale") instanceof Number n ? n.floatValue() : 1;
                    data = new Particle.DustTransition(color(raw.get("from-color")), color(raw.get("to-color")), scale);
                } else if (particle.getDataType() == Color.class) {
                    data = color(raw.get("color"));
                } else if (particle.getDataType() == org.bukkit.block.data.BlockData.class) {
                    org.bukkit.block.Block ground = location.clone().subtract(0, 0.1, 0).getBlock();
                    data = ground.getType().isSolid() ? ground.getBlockData() : Material.STONE.createBlockData();
                } else if (particle.getDataType() != Void.class) {
                    throw new IllegalArgumentException("unsupported data type for " + particle);
                }
                location.getWorld().spawnParticle(particle, location.clone().add(0, yOffset, 0), count, offset[0], offset[1], offset[2], extra, data);
            } catch (IllegalArgumentException e) { getLogger().warning("Invalid particle settings at " + path + ": " + e.getMessage()); }
        }
    }

    private double[] numbers(Object value, double[] defaults) {
        if (value == null) return defaults;
        if (!(value instanceof List<?> list) || list.size() != 3 || !(list.get(0) instanceof Number) || !(list.get(1) instanceof Number) || !(list.get(2) instanceof Number)) throw new IllegalArgumentException("expected three numeric values");
        return new double[]{((Number) list.get(0)).doubleValue(), ((Number) list.get(1)).doubleValue(), ((Number) list.get(2)).doubleValue()};
    }
    private Color color(Object value) {
        double[] rgb = numbers(value, new double[]{255, 255, 255});
        return Color.fromRGB((int) rgb[0], (int) rgb[1], (int) rgb[2]);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rockfrost.admin")) { sender.sendMessage(ChatColor.RED + "Нет права rockfrost.admin."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { reloadConfig(); harry.reload(); sender.sendMessage(net.kyori.adventure.text.Component.text("Конфигурация перезагружена.", net.kyori.adventure.text.format.NamedTextColor.GREEN)); return true; }
        if (args.length >= 2 && args[0].equalsIgnoreCase("spawn")) {
            String type = args[1].toLowerCase(Locale.ROOT);
            if (!MOB_TYPES.contains(type)) { sender.sendMessage(ChatColor.RED + "Неизвестный тип моба: " + type); return true; }
            Location loc = resolve(sender, args.length > 2 ? args[2] : null); if (loc != null) spawn(sender, type, loc); return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "/custommob spawn <" + String.join("|", MOB_TYPES) + "> [игрок]"); sender.sendMessage(ChatColor.YELLOW + "/custommob reload"); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if (args.length == 1) return List.of("spawn", "reload").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList(); if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) return MOB_TYPES.stream().filter(x -> x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList(); return Collections.emptyList(); }
    private void playMobSound(LivingEntity mob, String sound) {
        String type = mob.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (type != null) playSound(mob.getLocation(), "mobs." + type + ".sounds." + sound);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof LivingEntity mob && mobs.containsKey(mob.getUniqueId()) && e.getFinalDamage() > 0
                && e.getFinalDamage() < mob.getHealth()) playMobSound(mob, "hurt");
    }
    @EventHandler public void onDeath(EntityDeathEvent e) {
        harry.remove(e.getEntity().getUniqueId());
        if (mobs.containsKey(e.getEntity().getUniqueId())) {
            playMobSound(e.getEntity(), "death");
            if ("pumpkin_brute".equals(mobs.get(e.getEntity().getUniqueId()).type)) {
                LivingEntity brute = e.getEntity();
                Location at = brute.getLocation().clone();
                at.getWorld().spawnParticle(Particle.DRAGON_BREATH, at.clone().add(0, 1, 0), 50, .5, .5, .5, 0);
                Bukkit.getScheduler().runTaskLater(this, () -> explodeBrute(brute, at), 20L);
            }
            String entry = e.getEntity().getUniqueId().toString();
            pendingDeathEntries.add(entry);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("rockfrost_hidden");
                if (team != null) team.removeEntry(entry);
                pendingDeathEntries.remove(entry);
            }, 40L);
            mobs.remove(e.getEntity().getUniqueId());
        }
    }
    private void explodeBrute(LivingEntity mob, Location at) {
        String path = "mobs.pumpkin_brute.explosion";
        double radius = Math.max(0, cfg(path + ".radius", 4));
        for (int i = 0; i < 150; i++) {
            Vector offset = Vector.getRandom().multiply(2).subtract(new Vector(1, 1, 1));
            if (offset.lengthSquared() > 1) { i--; continue; }
            at.getWorld().spawnParticle(Particle.DRAGON_BREATH, at.clone().add(offset.multiply(radius)), 1, 0, 0, 0, 0);
        }
        for (Entity entity : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(mob) || living.getLocation().distanceSquared(at) > radius * radius) continue;
            living.damage(Math.max(0, cfg(path + ".damage", 8)), mob);
            if (living instanceof Player player && !player.isDead()) {
                int duration = Math.max(0, intCfg(path + ".nausea-duration-ticks", 60));
                if (duration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, Math.max(0, intCfg(path + ".nausea-amplifier", 0))));
            }
        }
        particles(at, "mobs.pumpkin_brute.particles.explosion");
        playSound(at, "mobs.pumpkin_brute.sounds.explosion");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPoisonAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity mob) || !(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0) return;
        MobData data = mobs.get(mob.getUniqueId());
        if (data == null || !data.type.equals("pumpkin_poison")) return;
        int duration = Math.max(0, intCfg("mobs.pumpkin_poison.poison-duration-ticks", 60));
        if (duration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, Math.max(0, intCfg("mobs.pumpkin_poison.poison-amplifier", 0))));
    }

    @EventHandler public void onTarget(EntityTargetEvent e) { if (e.getEntity().getPersistentDataContainer().has(mobKey, PersistentDataType.STRING)) e.setCancelled(true); }
    @EventHandler public void onMelee(EntityDamageByEntityEvent e) { if (e.getDamager() instanceof LivingEntity mob && "dancebomber".equals(mob.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING))) e.setDamage(cfg("mobs.dancebomber.melee-damage", 6)); }
    private static final class MobData {
        final String type;
        int cooldown = 1;
        int headlessTicks;
        int meleeCooldown;
        Location lastRetreatPosition;
        MobData(String type) { this.type = type; }
    }
}
