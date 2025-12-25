package com.devmod.client.ui.editor;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TemplateSystem {
    public enum TemplateType { WEAPON, ARMOR, ENCHANT, ATTRIBUTE, HYBRID }
    
    public static class Template {
        public String id, name, category;
        public TemplateType type;
        public Map<String, Object> data = new HashMap<>();
        public List<String> tags = new ArrayList<>();
        public int priority = 0;
        
        public Template(String id, String name, TemplateType type) {
            this.id = id; this.name = name; this.type = type;
        }
        
        public Template tag(String... tags) { 
            this.tags.addAll(Arrays.asList(tags)); return this; 
        }
        
        public Template priority(int p) { 
            this.priority = p; return this; 
        }
        
        public Template set(String key, Object value) { 
            data.put(key, value); return this; 
        }
    }
    
    private final Map<String, Template> templates = new HashMap<>();
    private final Map<String, List<Template>> categoryIndex = new HashMap<>();
    
    public void register(Template template) {
        templates.put(template.id, template);
        categoryIndex.computeIfAbsent(template.category, k -> new ArrayList<>()).add(template);
    }
    
    public List<Template> getForItem(ItemStack item) {
        var type = WeaponTypeDetector.detect(item);
        String category = type.name().toLowerCase();
        
        return categoryIndex.getOrDefault(category, Collections.emptyList())
            .stream()
            .sorted((a, b) -> Integer.compare(b.priority, a.priority))
            .toList();
    }
    
    public List<Template> search(String query, String category) {
        return templates.values().stream()
            .filter(t -> category == null || category.equals(t.category))
            .filter(t -> t.name.toLowerCase().contains(query.toLowerCase()) || 
                        t.tags.stream().anyMatch(tag -> tag.toLowerCase().contains(query.toLowerCase())))
            .sorted((a, b) -> Integer.compare(b.priority, a.priority))
            .toList();
    }
    
    public Optional<Template> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }
    
    // Built-in templates
    public static TemplateSystem createDefault() {
        var system = new TemplateSystem();
        
        system.register(new Template("sword_basic", "Basic Sword", TemplateType.WEAPON)
            .tag("melee", "common").priority(1)
            .set("attack_damage", 7.0f).set("attack_speed", 1.6f));
            
        system.register(new Template("sword_legendary", "Legendary Sword", TemplateType.WEAPON)
            .tag("melee", "legendary").priority(10)
            .set("attack_damage", 12.0f).set("attack_speed", 2.0f).set("crit_chance", 0.15f));
            
        system.register(new Template("bow_basic", "Basic Bow", TemplateType.WEAPON)
            .tag("ranged", "common").priority(1)
            .set("draw_speed", 1.0f).set("accuracy", 0.9f));
            
        return system;
    }
}