package com.example;

import org.bukkit.entity.Cow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MobAgeTest {
    @Test public void zombieIsMadeAdult() {
        List<String> calls = new ArrayList<>();

        RockFrostPlugin.makeAdult(mob(Zombie.class, calls));

        assertEquals(List.of("setAdult"), calls);
    }

    @Test public void ageableMobIsMadeAdult() {
        List<String> calls = new ArrayList<>();

        RockFrostPlugin.makeAdult(mob(Cow.class, calls));

        assertEquals(List.of("setAdult"), calls);
    }

    @Test public void nonAgeableMobIsUnchanged() {
        List<String> calls = new ArrayList<>();

        RockFrostPlugin.makeAdult(mob(Skeleton.class, calls));

        assertEquals(List.of(), calls);
    }

    private <T extends Mob> T mob(Class<T> type, List<String> calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return null;
                }));
    }
}
