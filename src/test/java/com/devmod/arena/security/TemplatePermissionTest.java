package com.devmod.arena.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

class TemplatePermissionTest {

    @Test
    void permissionNodeStrings() {
        assertEquals("devmod.template.view", TemplatePermission.VIEW.permissionNode());
        assertEquals("devmod.template.use", TemplatePermission.USE.permissionNode());
        assertEquals("devmod.template.edit", TemplatePermission.EDIT.permissionNode());
        assertEquals("devmod.template.delete", TemplatePermission.DELETE.permissionNode());
        assertEquals("devmod.template.admin", TemplatePermission.ADMIN.permissionNode());
    }

    @Test
    void hierarchicalLevels() {
        assertTrue(TemplatePermission.VIEW.level() < TemplatePermission.USE.level());
        assertTrue(TemplatePermission.USE.level() < TemplatePermission.EDIT.level());
        assertTrue(TemplatePermission.EDIT.level() < TemplatePermission.DELETE.level());
        assertTrue(TemplatePermission.DELETE.level() < TemplatePermission.ADMIN.level());
    }

    @Test
    void adminImpliesAll() {
        for (TemplatePermission p : TemplatePermission.values()) {
            assertTrue(TemplatePermission.ADMIN.implies(p));
        }
    }

    @Test
    void viewDoesNotImplyHigher() {
        assertFalse(TemplatePermission.VIEW.implies(TemplatePermission.USE));
        assertFalse(TemplatePermission.VIEW.implies(TemplatePermission.EDIT));
    }

    @Test
    void impliedPermissions() {
        Set<TemplatePermission> implied = TemplatePermission.EDIT.impliedPermissions();
        assertTrue(implied.contains(TemplatePermission.VIEW));
        assertTrue(implied.contains(TemplatePermission.USE));
        assertTrue(implied.contains(TemplatePermission.EDIT));
        assertFalse(implied.contains(TemplatePermission.DELETE));
        assertFalse(implied.contains(TemplatePermission.ADMIN));
    }

    @Test
    void requiredOpLevel() {
        assertEquals(0, TemplatePermission.VIEW.requiredOpLevel());
        assertEquals(0, TemplatePermission.USE.requiredOpLevel());
        assertEquals(2, TemplatePermission.EDIT.requiredOpLevel());
        assertEquals(3, TemplatePermission.DELETE.requiredOpLevel());
        assertEquals(4, TemplatePermission.ADMIN.requiredOpLevel());
    }

    @Test
    void fromNode() {
        assertEquals(TemplatePermission.EDIT, TemplatePermission.fromNode("devmod.template.edit"));
    }

    @Test
    void fromNodeInvalid() {
        assertThrows(IllegalArgumentException.class, () -> TemplatePermission.fromNode("invalid.node"));
    }

    @Test
    void forOpLevel() {
        assertEquals(TemplatePermission.USE, TemplatePermission.forOpLevel(0));
        assertEquals(TemplatePermission.EDIT, TemplatePermission.forOpLevel(2));
        assertEquals(TemplatePermission.DELETE, TemplatePermission.forOpLevel(3));
        assertEquals(TemplatePermission.ADMIN, TemplatePermission.forOpLevel(4));
    }

    @Test
    void allNodes() {
        String[] nodes = TemplatePermission.allNodes();
        assertEquals(5, nodes.length);
    }

    @Test
    void hasPermissionWithHierarchy() {
        Set<TemplatePermission> granted = Set.of(TemplatePermission.EDIT);
        assertTrue(TemplatePermission.hasPermission(granted, TemplatePermission.VIEW));
        assertTrue(TemplatePermission.hasPermission(granted, TemplatePermission.USE));
        assertTrue(TemplatePermission.hasPermission(granted, TemplatePermission.EDIT));
        assertFalse(TemplatePermission.hasPermission(granted, TemplatePermission.DELETE));
    }

    @Test
    void fromOpLevel() {
        Set<TemplatePermission> perms = TemplatePermission.fromOpLevel(3);
        assertTrue(perms.contains(TemplatePermission.VIEW));
        assertTrue(perms.contains(TemplatePermission.USE));
        assertTrue(perms.contains(TemplatePermission.EDIT));
        assertTrue(perms.contains(TemplatePermission.DELETE));
        assertFalse(perms.contains(TemplatePermission.ADMIN));
    }
}
