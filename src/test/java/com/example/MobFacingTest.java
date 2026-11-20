package com.example;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MobFacingTest {
    @Test public void lookingAtTargetDoesNotForceBodyRotation() {
        List<String> calls = new ArrayList<>();
        Player target = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> null);
        Mob mob = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(), new Class<?>[]{Mob.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("lookAt")) assertSame(target, args[0]);
                    return null;
                });

        RockFrostPlugin.faceTarget(mob, target);

        assertEquals(List.of("lookAt"), calls);
    }
}
