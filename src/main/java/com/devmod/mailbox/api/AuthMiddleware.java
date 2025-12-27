package com.devmod.mailbox.api;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.moderation.AdminAuditLog;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT authentication middleware for the Mailbox API.
 */
public final class AuthMiddleware {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthMiddleware.class);

    // Token validity: 24 hours
    private static final long TOKEN_VALIDITY_MS = 24 * 60 * 60 * 1000L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Secret key for JWT signing
    @Nullable
    private static volatile SecretKey secretKey;
    private static final Object SECRET_LOCK = new Object();

    // Admin credentials (should be loaded from config in production)
    private static final Map<String, AdminUser> ADMIN_USERS = new ConcurrentHashMap<>();
    private static final Object USER_STORE_LOCK = new Object();
    @Nullable
    private static volatile Path userStorePath;

    private static final String HASH_PREFIX = "pbkdf2$";
    private static final int HASH_ITERATIONS = 120_000;
    private static final int HASH_KEY_LENGTH_BITS = 256;
    private static final int HASH_SALT_BYTES = 16;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_WINDOW_MS = 10 * 60 * 1000L;
    private static final long LOGIN_BLOCK_MS = 10 * 60 * 1000L;
    private static final Map<String, LoginAttempt> LOGIN_ATTEMPTS = new ConcurrentHashMap<>();

    // Context attribute key for authenticated user
    public static final String USER_ATTR = "authUser";

    private AuthMiddleware() {}

    /**
     * Initialize auth settings from MailboxConfig.
     */
    public static void initialize(MailboxConfig config) {
        if (config == null) {
            return;
        }

        String secret = config.getApiSecretKey();
        if (secret == null || secret.isBlank()) {
            secret = generateSecretString();
            config.setApiSecretKey(secret);
            config.save();
            LOGGER.warn("[MailboxAuth] Generated new API secret key; saved to config");
        }

        secretKey = deriveKey(secret);

        userStorePath = config.getConfigDirectory().resolve("mailbox_admin_users.json");
        loadUsers(userStorePath);
        ensureBootstrapAdmin();
    }

    /**
     * Register an admin user.
     */
    public static void registerUser(String username, String password, String role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Username and password are required");
        }
        AdminUser user = new AdminUser(username.trim(), hashPassword(password), role != null ? role : "ADMIN");
        ADMIN_USERS.put(user.username(), user);
        saveUsers();
    }

    /**
     * Handle login request - generates JWT token.
     */
    public static void handleLogin(Context ctx) {
        LoginRequest request = ctx.bodyAsClass(LoginRequest.class);

        if (request == null || request.username() == null || request.password() == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new LoginResponse(false, null, "Missing credentials"));
            return;
        }

        String username = request.username().trim();
        String rateKey = rateLimitKey(ctx, username);
        if (isRateLimited(rateKey)) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS);
            ctx.json(new LoginResponse(false, null, "Too many attempts. Try again later."));
            return;
        }

        AdminUser user = ADMIN_USERS.get(username);
        PasswordCheck check = user != null
            ? verifyPassword(request.password(), user.passwordHash())
            : PasswordCheck.invalid();
        if (user == null || !check.isValid()) {
            recordFailedAttempt(rateKey);
            LOGGER.warn("Failed login attempt for user: {}", username);
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(new LoginResponse(false, null, "Invalid credentials"));
            return;
        }

        recordSuccessfulAttempt(rateKey);
        if (check.upgradedHash() != null) {
            updateUserPassword(user.username(), check.upgradedHash());
        }

        // Generate JWT token
        String token = generateToken(user);

        LOGGER.info("Successful login for user: {}", request.username());
        AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                .action(AdminAuditLog.Action.LOGIN)
                .actorUuid(null)
                .actorName(user.username())
                .targetType("auth")
                .details("Admin login")
                .build())
            .exceptionally(error -> {
                LOGGER.warn("[Auth] Failed to persist admin login audit log", error);
                return null;
            });
        ctx.json(new LoginResponse(true, token, null));
    }

    /**
     * Validate JWT token - called as before handler.
     */
    public static void validateToken(Context ctx) {
        // Skip auth for login endpoint
        if (ctx.path().endsWith("/auth/login")) {
            return;
        }

        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        Claims claims = parseToken(token);

        if (claims == null) {
            throw new UnauthorizedResponse("Invalid or expired token");
        }

        // Store user info in context for use by controllers
        ctx.attribute(USER_ATTR, new AuthenticatedUser(
            claims.getSubject(),
            claims.get("role", String.class)
        ));
    }

    /**
     * Get the authenticated user from context.
     */
    @Nullable
    public static AuthenticatedUser getUser(Context ctx) {
        return ctx.attribute(USER_ATTR);
    }

    /**
     * Check if the current user has admin role.
     */
    public static boolean isAdmin(Context ctx) {
        AuthenticatedUser user = getUser(ctx);
        return user != null && "ADMIN".equals(user.role());
    }

    /**
     * Require admin role or throw forbidden.
     */
    public static void requireAdmin(Context ctx) {
        if (!isAdmin(ctx)) {
            ctx.status(HttpStatus.FORBIDDEN);
            throw new SecurityException("Admin role required");
        }
    }

    private static String generateToken(AdminUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(TOKEN_VALIDITY_MS);

        return Jwts.builder()
            .subject(user.username())
            .claim("role", user.role())
            .issuedAt(Timestamp.from(now))
            .expiration(Timestamp.from(expiry))
            .signWith(getSecretKey())
            .compact();
    }

    @Nullable
    private static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            LOGGER.debug("Expired JWT token");
            return null;
        } catch (Exception e) {
            LOGGER.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    private static String hashPassword(String password) {
        byte[] salt = new byte[HASH_SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = pbkdf2(password.toCharArray(), salt, HASH_ITERATIONS, HASH_KEY_LENGTH_BITS);
        return HASH_PREFIX
            + HASH_ITERATIONS
            + "$"
            + Base64.getEncoder().encodeToString(salt)
            + "$"
            + Base64.getEncoder().encodeToString(derived);
    }

    private static PasswordCheck verifyPassword(String password, String hash) {
        if (hash == null || hash.isBlank()) {
            return PasswordCheck.invalid();
        }
        if (!hash.startsWith(HASH_PREFIX)) {
            boolean legacyOk = verifyLegacy(password, hash);
            return legacyOk ? PasswordCheck.upgrade(hashPassword(password)) : PasswordCheck.invalid();
        }
        List<String> parts = Splitter.on('$').splitToList(hash);
        if (parts.size() != 4) {
            return PasswordCheck.invalid();
        }
        try {
            int iterations = Integer.parseInt(parts.get(1));
            byte[] salt = Base64.getDecoder().decode(parts.get(2));
            byte[] expected = Base64.getDecoder().decode(parts.get(3));
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, HASH_KEY_LENGTH_BITS);
            return MessageDigest.isEqual(expected, actual) ? PasswordCheck.valid() : PasswordCheck.invalid();
        } catch (Exception e) {
            return PasswordCheck.invalid();
        }
    }

    private static boolean verifyLegacy(String password, String hash) {
        String legacy = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
        return legacy.equals(hash);
    }

    // DTOs
    public record LoginRequest(String username, String password) {}
    public record LoginResponse(boolean success, @Nullable String token, @Nullable String error) {}
    public record AdminUser(String username, String passwordHash, String role) {}
    public record AuthenticatedUser(String username, String role) {}
    public record AdminUserStore(List<AdminUser> users) {}
    private record LoginAttempt(int failures, long windowStart, long blockedUntil) {}
    private record PasswordCheck(boolean isValid, @Nullable String upgradedHash) {
        static PasswordCheck valid() { return new PasswordCheck(true, null); }
        static PasswordCheck invalid() { return new PasswordCheck(false, null); }
        static PasswordCheck upgrade(String newHash) { return new PasswordCheck(true, newHash); }
    }

    private static SecretKey getSecretKey() {
        SecretKey key = secretKey;
        if (key == null) {
            synchronized (SECRET_LOCK) {
                if (secretKey == null) {
                    secretKey = generateRandomKey();
                }
                key = secretKey;
            }
        }
        return key;
    }

    private static SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(hash);
        } catch (Exception e) {
            LOGGER.warn("[MailboxAuth] Failed to derive secret key, generating random key");
            return generateRandomKey();
        }
    }

    private static SecretKey generateRandomKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static String generateSecretString() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    private static String rateLimitKey(Context ctx, String username) {
        String ip = ctx.ip();
        String user = username != null ? username.toLowerCase(Locale.ROOT) : "unknown";
        return ip + ":" + user;
    }

    private static boolean isRateLimited(String key) {
        LoginAttempt attempt = LOGIN_ATTEMPTS.get(key);
        if (attempt == null) {
            return false;
        }
        if (attempt.blockedUntil > System.currentTimeMillis()) {
            return true;
        }
        if (System.currentTimeMillis() - attempt.windowStart > LOGIN_WINDOW_MS) {
            LOGIN_ATTEMPTS.remove(key);
            return false;
        }
        return false;
    }

    private static void recordFailedAttempt(String key) {
        long now = System.currentTimeMillis();
        LOGIN_ATTEMPTS.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > LOGIN_WINDOW_MS) {
                return new LoginAttempt(1, now, 0);
            }
            int failures = existing.failures + 1;
            long blockedUntil = failures >= MAX_LOGIN_ATTEMPTS ? now + LOGIN_BLOCK_MS : existing.blockedUntil;
            return new LoginAttempt(failures, existing.windowStart, blockedUntil);
        });
    }

    private static void recordSuccessfulAttempt(String key) {
        LOGIN_ATTEMPTS.remove(key);
    }

    private static void updateUserPassword(String username, String newHash) {
        synchronized (USER_STORE_LOCK) {
            AdminUser user = ADMIN_USERS.get(username);
            if (user == null) {
                return;
            }
            ADMIN_USERS.put(username, new AdminUser(username, newHash, user.role()));
            saveUsers();
        }
    }

    private static void loadUsers(@Nullable Path filePath) {
        if (filePath == null) {
            return;
        }
        synchronized (USER_STORE_LOCK) {
            ADMIN_USERS.clear();
            if (!Files.exists(filePath)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                AdminUserStore store = GSON.fromJson(reader, AdminUserStore.class);
                if (store != null && store.users() != null) {
                    for (AdminUser user : store.users()) {
                        if (user != null && user.username() != null && !user.username().isBlank()) {
                            ADMIN_USERS.put(user.username(), user);
                        }
                    }
                }
                LOGGER.info("[MailboxAuth] Loaded {} admin users", ADMIN_USERS.size());
            } catch (Exception e) {
                LOGGER.error("[MailboxAuth] Failed to load admin users", e);
            }
        }
    }

    private static void saveUsers() {
        Path filePath = userStorePath;
        if (filePath == null) {
            return;
        }
        synchronized (USER_STORE_LOCK) {
            try {
                Files.createDirectories(filePath.getParent());
                Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
                Path backupFile = filePath.resolveSibling(filePath.getFileName() + ".bak");
                AdminUserStore store = new AdminUserStore(new ArrayList<>(ADMIN_USERS.values()));
                try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    GSON.toJson(store, writer);
                    writer.flush();
                }
                if (Files.exists(filePath)) {
                    Files.copy(filePath, backupFile, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tempFile, filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                try {
                    Files.move(filePath.resolveSibling(filePath.getFileName() + ".tmp"), filePath,
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    LOGGER.error("[MailboxAuth] Failed to save admin users (fallback)", ex);
                }
            } catch (Exception e) {
                LOGGER.error("[MailboxAuth] Failed to save admin users", e);
            }
        }
    }

    private static void ensureBootstrapAdmin() {
        if (!ADMIN_USERS.isEmpty()) {
            return;
        }
        String username = "admin";
        String password = generatePassword(20);
        AdminUser user = new AdminUser(username, hashPassword(password), "ADMIN");
        ADMIN_USERS.put(username, user);
        saveUsers();
        LOGGER.warn("[MailboxAuth] Bootstrap admin created. Username: {} Password: {}", username, password);
    }

    private static String generatePassword(int length) {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
