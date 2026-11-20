package com.example;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.Assert.*;

public class BombExplosionTest {
    private static class Victim {
        final UUID id = UUID.randomUUID();
        int damageCalls;
        double damage;
        Vector velocity;
        final LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(), new Class<?>[] {LivingEntity.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUniqueId": return id;
                        case "getLocation": return new Location(null, 2, 0, 0);
                        case "setVelocity": velocity = (Vector) args[0]; return null;
                        case "damage": damageCalls++; damage = (double) args[0]; return null;
                        default: throw new AssertionError("Unexpected call: " + method.getName());
                    }
                });
    }

    @Test public void ownerReceivesNeitherDamageNorKnockback() {
        Victim owner = new Victim();
        RockFrostPlugin.applyBombExplosion(owner.entity, owner.id, new Location(null, 0, 0, 0), 1.2, 12);
        assertEquals(0, owner.damageCalls);
        assertNull(owner.velocity);
    }

    @Test public void otherLivingEntitiesStillReceiveDamageAndKnockback() {
        Victim victim = new Victim();
        RockFrostPlugin.applyBombExplosion(victim.entity, UUID.randomUUID(), new Location(null, 0, 0, 0), 1.2, 12);
        assertEquals(1, victim.damageCalls);
        assertEquals(12, victim.damage, 0);
        assertEquals(new Vector(1.2, 0, 0), victim.velocity);
    }

    @Test public void entityAtExplosionCenterStillTakesDamageWithoutKnockback() {
        Victim victim = new Victim();
        RockFrostPlugin.applyBombExplosion(victim.entity, UUID.randomUUID(), new Location(null, 2, 0, 0), 1.2, 12);
        assertEquals(1, victim.damageCalls);
        assertNull(victim.velocity);
    }
}
