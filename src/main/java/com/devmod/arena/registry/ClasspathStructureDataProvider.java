package com.devmod.arena.registry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
public class ClasspathStructureDataProvider implements TemplateValidator.StructureDataProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathStructureDataProvider.class);

    private final ClassLoader resourceLoader;

    public ClasspathStructureDataProvider(ClassLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    @Override
    public byte[] load(String path) throws Exception {
        ResourceLocation rl = ResourceLocation.tryParse(Objects.requireNonNull(path));
        if (rl == null) {
            LOGGER.warn("Invalid ResourceLocation for structure path: {}", path);
            return null;
        }
        String resourcePath = "data/" + rl.getNamespace() + "/structures/" + rl.getPath() + ".nbt";
        try (InputStream in = resourceLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("Structure NBT not found at resource path: {}", resourcePath);
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            LOGGER.error("Failed to read structure NBT from {}", resourcePath, e);
            throw e;
        }
    }
}
