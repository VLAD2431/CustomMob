package com.example;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.*;

public class PluginDeclarationTest {
    private PluginDescriptionFile description() throws InvalidDescriptionException {
        InputStream resource = getClass().getResourceAsStream("/plugin.yml");
        assertNotNull("plugin.yml must be available on the classpath", resource);
        return new PluginDescriptionFile(resource);
    }

    @Test public void custommobCommandIsDeclaredWithAdminPermission() throws InvalidDescriptionException {
        Map<String, Map<String, Object>> commands = description().getCommands();
        assertNotNull(commands);
        assertTrue("custommob command must be declared", commands.containsKey("custommob"));
        assertEquals("rockfrost.admin", commands.get("custommob").get("permission"));
    }

    @Test public void adminPermissionIsDeclaredForOperators() throws InvalidDescriptionException {
        assertTrue(description().getPermissions().stream().anyMatch(permission ->
                permission.getName().equals("rockfrost.admin")
                        && permission.getDefault() == org.bukkit.permissions.PermissionDefault.OP));
    }

    @Test public void oldRockfrostCommandIsNotDeclared() throws InvalidDescriptionException {
        Map<String, Map<String, Object>> commands = description().getCommands();
        assertNotNull(commands);
        assertFalse("old rockfrost command must not be declared", commands.containsKey("rockfrost"));
    }
}
