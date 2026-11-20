package com.example;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import static org.junit.Assert.*;

public class MobEquipmentTest {
    @Test public void zombiesSpawnWithoutArmorOrItemsInEitherHand() {
        assertEquipmentCleared(Zombie.class);
    }

    @Test public void skeletonsSpawnWithoutArmorOrItemsInEitherHand() {
        assertEquipmentCleared(Skeleton.class);
    }

    @Test public void mobsWithoutEquipmentAreSupported() {
        RockFrostPlugin.clearEquipment(mob(Mob.class, null));
    }

    private void assertEquipmentCleared(Class<? extends Mob> type) {
        boolean[] occupied = {true, true, true, true, true, true};
        EntityEquipment equipment = (EntityEquipment) Proxy.newProxyInstance(
                EntityEquipment.class.getClassLoader(), new Class<?>[]{EntityEquipment.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("clear")) {
                        Arrays.fill(occupied, false);
                        return null;
                    }
                    throw new AssertionError("Unexpected equipment call: " + method.getName());
                });

        RockFrostPlugin.clearEquipment(mob(type, equipment));

        assertArrayEquals("All four armor slots and both hands must be empty",
                new boolean[]{false, false, false, false, false, false}, occupied);
    }

    private <T extends Mob> T mob(Class<T> type, EntityEquipment equipment) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEquipment")) return equipment;
                    throw new AssertionError("Unexpected mob call: " + method.getName());
                }));
    }

}
