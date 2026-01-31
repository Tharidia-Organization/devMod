package com.devmod.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.TestBootstrap;
import com.devmod.telemetry.damage.DamageAttributionResolver;

@DisplayName("DamageAttributionResolver")
class DamageAttributionResolverTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("findSourceItemMethod locates ItemStack getter")
    void findSourceItemMethodFindsGetter() throws Exception {
        Method finder = DamageAttributionResolver.class.getDeclaredMethod("findSourceItemMethod", Class.class);
        finder.setAccessible(true);

        Method found = (Method) finder.invoke(null, SourceItemDummy.class);
        assertNotNull(found);

        Object result = found.invoke(new SourceItemDummy());
        assertEquals(Items.DIAMOND_SWORD, ((ItemStack) result).getItem());
    }

    @Test
    @DisplayName("findProjectileItemMethod locates ItemStack getter")
    void findProjectileItemMethodFindsGetter() throws Exception {
        Method finder = DamageAttributionResolver.class.getDeclaredMethod("findProjectileItemMethod", Class.class);
        finder.setAccessible(true);

        Method found = (Method) finder.invoke(null, ProjectileItemDummy.class);
        assertNotNull(found);

        Object result = found.invoke(new ProjectileItemDummy());
        assertEquals(Items.ARROW, ((ItemStack) result).getItem());
    }

    @Test
    @DisplayName("findSourceItemMethod returns null when no getter exists")
    void findSourceItemMethodReturnsNullWhenMissing() throws Exception {
        Method finder = DamageAttributionResolver.class.getDeclaredMethod("findSourceItemMethod", Class.class);
        finder.setAccessible(true);

        Method found = (Method) finder.invoke(null, NoItemDummy.class);
        assertNull(found);
    }

    static class SourceItemDummy {
        public ItemStack getWeaponItem() {
            return new ItemStack(Items.DIAMOND_SWORD);
        }
    }

    static class ProjectileItemDummy {
        public ItemStack getItemStack() {
            return new ItemStack(Items.ARROW);
        }
    }

    static class NoItemDummy {
        public String getName() {
            return "none";
        }
    }
}
