package com.devmod.mailbox.api;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;

/**
 * Embedded HTTP server for the mailbox admin panel API.
 * Provides REST endpoints for managing messages, news, users, and tasks.
 */
public final class MailboxApiServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxApiServer.class);

    private static final int DEFAULT_PORT = 8765;
    private static final String API_PREFIX = "/api";

    @Nullable
    private static Javalin server = null;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private MailboxApiServer() {}

    /**
     * Start the API server on the default port.
     */
    public static void start() {
        start(DEFAULT_PORT);
    }

    /**
     * Start the API server on the specified port.
     */
    public static synchronized void start(int port) {
        if (running.get()) {
            LOGGER.warn("Mailbox API server is already running");
            return;
        }

        try {
            MailboxConfig config = MailboxConfig.INSTANCE;
            AuthMiddleware.initialize(config);

            // Explicitly configure Jackson to work around NeoForge classloader issues
            JavalinJackson jacksonMapper = new JavalinJackson(new ObjectMapper());

            Javalin app = Javalin.create(javalinConfig -> {
                javalinConfig.jsonMapper(jacksonMapper);
                javalinConfig.http.defaultContentType = "application/json";
                java.util.List<String> origins = MailboxConfig.INSTANCE.getApiAllowedOrigins();
                if (!origins.isEmpty()) {
                    javalinConfig.plugins.enableCors(cors -> {
                        cors.add(rule -> {
                            rule.allowCredentials = false;
                            String first = origins.get(0);
                            String[] rest = origins.size() > 1
                                ? origins.subList(1, origins.size()).toArray(String[]::new)
                                : new String[0];
                            rule.allowHost(first, rest);
                        });
                    });
                } else {
                    LOGGER.warn("[MailboxAPI] CORS disabled (no allowed origins configured)");
                }
            });
            server = app;

            // Register exception handlers
            registerExceptionHandlers(app);

            // Register routes
            registerRoutes(app);

            // Start server
            app.start(port);
            running.set(true);

            LOGGER.info("Mailbox API server started on port {}", port);
        } catch (Exception e) {
            LOGGER.error("Failed to start Mailbox API server", e);
            server = null;
        }
    }

    /**
     * Stop the API server.
     */
    public static synchronized void stop() {
        Javalin app = server;
        if (!running.get() || app == null) {
            return;
        }

        try {
            app.stop();
            LOGGER.info("Mailbox API server stopped");
        } catch (Exception e) {
            LOGGER.error("Error stopping Mailbox API server", e);
        } finally {
            server = null;
            running.set(false);
        }
    }

    /**
     * Check if the server is running.
     */
    public static boolean isRunning() {
        return running.get();
    }

    /**
     * Get the server port (or -1 if not running).
     */
    public static int getPort() {
        Javalin s = server;
        if (s != null && running.get()) {
            return s.port();
        }
        return -1;
    }

    private static void registerExceptionHandlers(Javalin app) {
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(HttpStatus.BAD_REQUEST);
            String message = e.getMessage();
            ctx.json(new ErrorResponse("Bad Request", message != null ? message : "Bad Request"));
        });

        app.exception(SecurityException.class, (e, ctx) -> {
            ctx.status(HttpStatus.FORBIDDEN);
            String message = e.getMessage();
            ctx.json(new ErrorResponse("Forbidden", message != null ? message : "Forbidden"));
        });

        app.exception(Exception.class, (e, ctx) -> {
            LOGGER.error("Unhandled API exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new ErrorResponse("Internal Server Error", "An unexpected error occurred"));
        });
    }

    private static void registerRoutes(Javalin app) {
        // Health check (no auth required) - use raw JSON to avoid mapper issues
        app.get("/health", ctx -> {
            ctx.contentType("application/json");
            ctx.result("{\"healthy\":true,\"status\":\"OK\"}");
        });

        // Auth endpoint
        app.post(API_PREFIX + "/auth/login", AuthMiddleware::handleLogin);

        // Protected routes - all require JWT auth
        app.before(API_PREFIX + "/*", AuthMiddleware::validateToken);
        app.before(API_PREFIX + "/*", ctx -> {
            if (!ctx.path().endsWith("/auth/login")) {
                AuthMiddleware.requireAdmin(ctx);
            }
        });

        // Message routes
        app.get(API_PREFIX + "/messages",
            com.devmod.mailbox.api.controllers.MessageController::listMessages);
        app.get(API_PREFIX + "/messages/{id}",
            com.devmod.mailbox.api.controllers.MessageController::getMessage);
        app.post(API_PREFIX + "/messages",
            com.devmod.mailbox.api.controllers.MessageController::createMessage);
        app.post(API_PREFIX + "/messages/broadcast",
            com.devmod.mailbox.api.controllers.MessageController::broadcastMessage);
        app.delete(API_PREFIX + "/messages/{id}",
            com.devmod.mailbox.api.controllers.MessageController::deleteMessage);

        // Broadcast queue routes
        app.get(API_PREFIX + "/broadcast/jobs/active",
            com.devmod.mailbox.api.controllers.BroadcastController::listActiveJobs);
        app.get(API_PREFIX + "/broadcast/jobs/recent",
            com.devmod.mailbox.api.controllers.BroadcastController::listRecentJobs);
        app.get(API_PREFIX + "/broadcast/jobs/{id}",
            com.devmod.mailbox.api.controllers.BroadcastController::getJob);

        // News routes
        app.get(API_PREFIX + "/news",
            com.devmod.mailbox.api.controllers.NewsController::listNews);
        app.get(API_PREFIX + "/news/{id}",
            com.devmod.mailbox.api.controllers.NewsController::getNews);
        app.post(API_PREFIX + "/news",
            com.devmod.mailbox.api.controllers.NewsController::createNews);
        app.put(API_PREFIX + "/news/{id}",
            com.devmod.mailbox.api.controllers.NewsController::updateNews);
        app.delete(API_PREFIX + "/news/{id}",
            com.devmod.mailbox.api.controllers.NewsController::deleteNews);

        // User routes
        app.get(API_PREFIX + "/users",
            com.devmod.mailbox.api.controllers.UserController::listUsers);
        app.get(API_PREFIX + "/users/{uuid}",
            com.devmod.mailbox.api.controllers.UserController::getUser);
        app.get(API_PREFIX + "/users/{uuid}/inbox",
            com.devmod.mailbox.api.controllers.UserController::getUserInbox);
        app.put(API_PREFIX + "/users/{uuid}/access",
            com.devmod.mailbox.api.controllers.UserController::updateUserAccess);

        // Task routes
        app.get(API_PREFIX + "/tasks",
            com.devmod.mailbox.api.controllers.TaskController::listTasks);
        app.get(API_PREFIX + "/tasks/audit/recent",
            com.devmod.mailbox.api.controllers.TaskController::getRecentAudits);
        app.get(API_PREFIX + "/tasks/{id}",
            com.devmod.mailbox.api.controllers.TaskController::getTask);
        app.get(API_PREFIX + "/tasks/{id}/audit",
            com.devmod.mailbox.api.controllers.TaskController::getTaskAudit);
        app.post(API_PREFIX + "/tasks",
            com.devmod.mailbox.api.controllers.TaskController::createTask);
        app.put(API_PREFIX + "/tasks/{id}",
            com.devmod.mailbox.api.controllers.TaskController::updateTask);
        app.delete(API_PREFIX + "/tasks/{id}",
            com.devmod.mailbox.api.controllers.TaskController::deleteTask);

        // Config routes
        app.get(API_PREFIX + "/config",
            com.devmod.mailbox.api.controllers.ConfigController::getConfig);
        app.put(API_PREFIX + "/config",
            com.devmod.mailbox.api.controllers.ConfigController::updateConfig);
        app.get(API_PREFIX + "/stats",
            com.devmod.mailbox.api.controllers.ConfigController::getStats);

        // Analytics routes (advanced metrics)
        app.get(API_PREFIX + "/analytics/dashboard",
            com.devmod.mailbox.api.controllers.AnalyticsController::getDashboardMetrics);
        app.get(API_PREFIX + "/analytics/hourly",
            com.devmod.mailbox.api.controllers.AnalyticsController::getHourlyVolume);
        app.get(API_PREFIX + "/analytics/type-breakdown",
            com.devmod.mailbox.api.controllers.AnalyticsController::getTypeBreakdown);
        app.get(API_PREFIX + "/analytics/top-senders",
            com.devmod.mailbox.api.controllers.AnalyticsController::getTopSenders);
        app.get(API_PREFIX + "/analytics/top-receivers",
            com.devmod.mailbox.api.controllers.AnalyticsController::getTopReceivers);
        app.get(API_PREFIX + "/analytics/anomalies",
            com.devmod.mailbox.api.controllers.AnalyticsController::getAnomalies);
        app.get(API_PREFIX + "/analytics/system-status",
            com.devmod.mailbox.api.controllers.AnalyticsController::getSystemStatus);
        app.get(API_PREFIX + "/analytics/db-recommendations",
            com.devmod.mailbox.api.controllers.AnalyticsController::getDbRecommendations);

        // Admin audit log routes
        app.get(API_PREFIX + "/audit",
            com.devmod.mailbox.api.controllers.AdminAuditController::listAudits);

        // Search routes
        app.post(API_PREFIX + "/search",
            com.devmod.mailbox.api.controllers.SearchController::search);
        app.get(API_PREFIX + "/search/quick",
            com.devmod.mailbox.api.controllers.SearchController::quickSearch);
        app.get(API_PREFIX + "/search/suggestions",
            com.devmod.mailbox.api.controllers.SearchController::getSuggestions);
        app.get(API_PREFIX + "/search/stats",
            com.devmod.mailbox.api.controllers.SearchController::getStats);
        app.post(API_PREFIX + "/search/rebuild-index",
            com.devmod.mailbox.api.controllers.SearchController::rebuildIndex);

        // Ticket routes
        app.get(API_PREFIX + "/tickets",
            com.devmod.mailbox.api.controllers.TicketController::listTickets);
        app.get(API_PREFIX + "/tickets/stats",
            com.devmod.mailbox.api.controllers.TicketController::getStats);
        app.get(API_PREFIX + "/tickets/{id}",
            com.devmod.mailbox.api.controllers.TicketController::getTicket);
        app.post(API_PREFIX + "/tickets",
            com.devmod.mailbox.api.controllers.TicketController::createTicket);
        app.put(API_PREFIX + "/tickets/{id}",
            com.devmod.mailbox.api.controllers.TicketController::updateTicket);
        app.put(API_PREFIX + "/tickets/{id}/assign",
            com.devmod.mailbox.api.controllers.TicketController::assignTicket);
        app.post(API_PREFIX + "/tickets/{id}/comments",
            com.devmod.mailbox.api.controllers.TicketController::addComment);
    }

    // Response DTOs
    public record ErrorResponse(String error, String message) {}
    public record HealthResponse(boolean healthy, String status) {}
}
