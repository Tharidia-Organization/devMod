package com.devmod.client.vfx.effekseer.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

public final class NativeLibraryInstaller {
    private NativeLibraryInstaller() {
    }

    public static boolean installOrUpdate(String resourcePath, File target) throws IOException {
        if (!target.isFile()) {
            copy(resourcePath, target.toPath());
            return true;
        }
        byte[] resourceHash = sha1(Objects.requireNonNull(NativeLibraryInstaller.class.getClassLoader()
            .getResourceAsStream(resourcePath)));
        byte[] fileHash = sha1(Files.newInputStream(target.toPath()));
        if (Arrays.equals(resourceHash, fileHash)) {
            return false;
        }
        Files.delete(target.toPath());
        copy(resourcePath, target.toPath());
        return true;
    }

    private static void copy(String resourcePath, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream input = Objects.requireNonNull(NativeLibraryInstaller.class.getClassLoader()
            .getResourceAsStream(resourcePath))) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] sha1(InputStream input) throws IOException {
        try (InputStream stream = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
