package com.missingperson;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class App {
    private static final String FIXED_ADMIN_USERNAME = "admin";
    private static final String FIXED_ADMIN_PASSWORD = "admin123";

    public static void main(String[] args) throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        Path frontendDir = root.resolve("frontend");
        Path storageDir = storageDirectory(root);
        int port = parseIntOrDefault(System.getenv("PORT"), 8080);
        String host = firstNonBlank(System.getenv("HOST"), "0.0.0.0");

        System.out.println("Booting server with HOST=" + host + ", PORT=" + port);
        Files.createDirectories(storageDir.resolve("persons"));
        Files.createDirectories(storageDir.resolve("matches"));
        Files.createDirectories(frontendDir.resolve("models"));
        Files.createDirectories(frontendDir.resolve("vendor"));

        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
        try {
            databaseConfig.ensureSchema();
        } catch (IOException exception) {
            System.err.println("Startup failed before binding port: " + exception.getMessage());
            throw exception;
        }
        ImageStorage imageStorage = ImageStorage.fromEnvironment(storageDir);
        PersonRepository personRepository = new PersonRepository(storageDir.resolve("persons.tsv"), databaseConfig);
        MatchHistoryRepository historyRepository = new MatchHistoryRepository(storageDir.resolve("matches.tsv"), databaseConfig);
        AuthService authService = new AuthService(storageDir.resolve("users.tsv"), databaseConfig);

        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/login", new LoginHandler(authService));
        server.createContext("/api/signup", new SignupHandler(authService));
        server.createContext("/api/forgot-password", new ForgotPasswordHandler(authService));
        server.createContext("/api/reset-password", new ResetPasswordHandler(authService));
        server.createContext("/api/logout", new LogoutHandler(authService));
        server.createContext("/api/change-password", new ChangePasswordHandler(authService));
        server.createContext("/api/users", new UsersHandler(authService));
        server.createContext("/api/dashboard", new DashboardHandler(personRepository, historyRepository, authService));
        server.createContext("/api/history", new HistoryHandler(historyRepository, storageDir, authService));
        server.createContext("/api/review-insights", new ReviewInsightsHandler(historyRepository, authService));
        server.createContext("/api/cases", new CaseManagementHandler(personRepository, historyRepository, authService));
        server.createContext("/api/case-status", new CaseStatusHandler(personRepository, authService));
        server.createContext("/api/reviews", new ReviewStatusHandler(historyRepository, authService));
        server.createContext("/api/persons", new PersonsHandler(personRepository, imageStorage, authService, new RecognitionService()));
        server.createContext("/api/match", new MatchHandler(personRepository, historyRepository, imageStorage, authService));
        server.createContext("/files", new FileHandler(storageDir));
        server.createContext("/", new StaticHandler(frontendDir));
        server.setExecutor(null);
        server.start();

        System.out.println("Server started on http://" + host + ":" + port);
        System.out.println("Default admin login: admin / admin123");
        System.out.println("Storage mode: " + (databaseConfig.enabled() ? "database (" + databaseConfig.label() + ")" : "local TSV files"));
        System.out.println("Storage directory: " + storageDir);
        System.out.println("Image storage: " + imageStorage.label());
    }

    static Path storageDirectory(Path root) {
        String configured = trim(System.getenv("APP_DATA_DIR"));
        if (!configured.isEmpty()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return root.resolve("storage");
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCors(exchange)) {
                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        private final AuthService authService;

        LoginHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
            String username = trim(firstNonBlank(body.get("email"), body.get("username")));
            String password = trim(body.get("password"));
            Session session = authService.login(username, password);
            if (session == null) {
                sendJson(exchange, 401, "{\"error\":\"invalid credentials\"}");
                return;
            }

            sendJson(exchange, 200, "{"
                    + "\"token\":\"" + escape(session.token()) + "\","
                    + "\"user\":{\"email\":\"" + escape(session.username()) + "\",\"role\":\"" + escape(session.role()) + "\",\"canCaseEdit\":" + session.canCaseEdit() + "}"
                    + "}");
        }
    }

    static class SignupHandler implements HttpHandler {
        private final AuthService authService;

        SignupHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
            String email = trim(body.get("email"));
            String password = trim(body.get("password"));

            if (!looksLikeEmail(email)) {
                sendJson(exchange, 400, "{\"error\":\"valid email is required\"}");
                return;
            }
            if (password.length() < 6) {
                sendJson(exchange, 400, "{\"error\":\"password must be at least 6 characters\"}");
                return;
            }

            UserRecord created = authService.createUser(email, password, "operator", false);
            if (created == null) {
                sendJson(exchange, 409, "{\"error\":\"account already exists. Please sign in.\"}");
                return;
            }
            sendJson(exchange, 201, "{\"user\":" + created.toJson() + "}");
        }
    }

    static class ForgotPasswordHandler implements HttpHandler {
        private final AuthService authService;

        ForgotPasswordHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
            String email = trim(body.get("email"));
            if (!looksLikeEmail(email)) {
                sendJson(exchange, 400, "{\"error\":\"valid email is required\"}");
                return;
            }

            String code = authService.startPasswordReset(email);
            if (code == null) {
                sendJson(exchange, 404, "{\"error\":\"account not found\"}");
                return;
            }

            sendJson(exchange, 200, "{"
                    + "\"status\":\"reset_code_generated\","
                    + "\"resetCode\":\"" + escape(code) + "\","
                    + "\"message\":\"Demo mode: use this reset code in the reset form.\""
                    + "}");
        }
    }

    static class ResetPasswordHandler implements HttpHandler {
        private final AuthService authService;

        ResetPasswordHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
            String email = trim(body.get("email"));
            String code = trim(body.get("code"));
            String newPassword = trim(body.get("newPassword"));

            if (!looksLikeEmail(email) || isBlank(code) || newPassword.length() < 6) {
                sendJson(exchange, 400, "{\"error\":\"email, reset code, and a 6+ character password are required\"}");
                return;
            }

            if (!authService.resetPassword(email, code, newPassword)) {
                sendJson(exchange, 400, "{\"error\":\"invalid reset code or email\"}");
                return;
            }

            sendJson(exchange, 200, "{\"status\":\"password_reset\"}");
        }
    }

    static class LogoutHandler implements HttpHandler {
        private final AuthService authService;

        LogoutHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            String token = bearerToken(exchange);
            if (!isBlank(token)) {
                authService.logout(token);
            }
            sendJson(exchange, 200, "{\"status\":\"logged_out\"}");
        }
    }

    static class ChangePasswordHandler implements HttpHandler {
        private final AuthService authService;

        ChangePasswordHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            Session session = requireAuth(exchange, authService);
            if (session == null) {
                return;
            }

            Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
            String currentPassword = trim(body.get("currentPassword"));
            String newPassword = trim(body.get("newPassword"));

            if (isBlank(currentPassword) || isBlank(newPassword)) {
                sendJson(exchange, 400, "{\"error\":\"current password and new password are required\"}");
                return;
            }
            if (newPassword.length() < 6) {
                sendJson(exchange, 400, "{\"error\":\"new password must be at least 6 characters\"}");
                return;
            }

            if (!authService.changePassword(session.username(), currentPassword, newPassword)) {
                sendJson(exchange, 400, "{\"error\":\"current password is incorrect\"}");
                return;
            }

            sendJson(exchange, 200, "{\"status\":\"password_changed\"}");
        }
    }

    static class UsersHandler implements HttpHandler {
        private final AuthService authService;

        UsersHandler(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            Session session = requireAuth(exchange, authService);
            if (session == null) {
                return;
            }
            if (!"admin".equalsIgnoreCase(session.role())) {
                sendJson(exchange, 403, "{\"error\":\"admin access required\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<UserRecord> users = authService.listUsers();
                StringBuilder json = new StringBuilder("{\"users\":[");
                for (int i = 0; i < users.size(); i++) {
                    if (i > 0) {
                        json.append(',');
                    }
                    json.append(users.get(i).toJson());
                }
                json.append("]}");
                sendJson(exchange, 200, json.toString());
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
                String action = trim(body.get("action"));
                if ("set_case_access".equalsIgnoreCase(action)) {
                    String targetEmail = trim(firstNonBlank(body.get("email"), body.get("username")));
                    boolean canCaseEdit = "true".equalsIgnoreCase(trim(body.get("canCaseEdit")));
                    if (isBlank(targetEmail)) {
                        sendJson(exchange, 400, "{\"error\":\"email is required\"}");
                        return;
                    }
                    if (!authService.updateCaseAccess(targetEmail, canCaseEdit)) {
                        sendJson(exchange, 404, "{\"error\":\"user not found\"}");
                        return;
                    }
                    sendJson(exchange, 200, "{\"status\":\"case_access_updated\"}");
                    return;
                }

                String username = trim(firstNonBlank(body.get("email"), body.get("username")));
                String password = trim(body.get("password"));
                String role = trim(body.get("role"));

                if (!looksLikeEmail(username) || isBlank(password)) {
                    sendJson(exchange, 400, "{\"error\":\"valid email and password are required\"}");
                    return;
                }
                if (password.length() < 6) {
                    sendJson(exchange, 400, "{\"error\":\"password must be at least 6 characters\"}");
                    return;
                }
                if (isBlank(role)) {
                    role = "operator";
                }
                if (!List.of("admin", "operator").contains(role.toLowerCase(Locale.ROOT))) {
                    sendJson(exchange, 400, "{\"error\":\"role must be admin or operator\"}");
                    return;
                }

                UserRecord created = authService.createUser(username, password, role.toLowerCase(Locale.ROOT), false);
                if (created == null) {
                    sendJson(exchange, 409, "{\"error\":\"username already exists\"}");
                    return;
                }
                sendJson(exchange, 201, "{\"user\":" + created.toJson() + "}");
                return;
            }

            sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    static class DashboardHandler implements HttpHandler {
        private final PersonRepository personRepository;
        private final MatchHistoryRepository historyRepository;
        private final AuthService authService;

        DashboardHandler(PersonRepository personRepository, MatchHistoryRepository historyRepository, AuthService authService) {
            this.personRepository = personRepository;
            this.historyRepository = historyRepository;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            Session session = requireAuth(exchange, authService);
            if (session == null) {
                return;
            }

            List<PersonRecord> persons = personRepository.readAll();
            List<MatchHistoryRecord> history = historyRepository.readAll();
            long matched = history.stream().filter(MatchHistoryRecord::matched).count();
            long unmatched = history.size() - matched;
            long reviewQueue = history.stream().filter(item -> "review_required".equalsIgnoreCase(item.reviewStatus())).count();
            long trackedUnknowns = history.stream()
                    .filter(item -> !item.matched() && !isBlank(item.unknownGroupId()))
                    .map(MatchHistoryRecord::unknownGroupId)
                    .distinct()
                    .count();

            sendJson(exchange, 200, "{"
                    + "\"user\":{\"username\":\"" + escape(session.username()) + "\",\"role\":\"" + escape(session.role()) + "\"},"
                    + "\"stats\":{"
                    + "\"registeredPersons\":" + persons.size() + ","
                    + "\"totalScans\":" + history.size() + ","
                    + "\"successfulMatches\":" + matched + ","
                    + "\"unmatchedScans\":" + unmatched + ","
                    + "\"reviewQueue\":" + reviewQueue + ","
                    + "\"trackedUnknowns\":" + trackedUnknowns
                    + "}"
                    + "}");
        }
    }

    static class HistoryHandler implements HttpHandler {
        private final MatchHistoryRepository historyRepository;
        private final Path storageDir;
        private final AuthService authService;

        HistoryHandler(MatchHistoryRepository historyRepository, Path storageDir, AuthService authService) {
            this.historyRepository = historyRepository;
            this.storageDir = storageDir;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            Session session = requireAuth(exchange, authService);
            if (session == null) {
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!"admin".equalsIgnoreCase(session.role()) && !session.canCaseEdit()) {
                    sendJson(exchange, 403, "{\"error\":\"case edit access requires admin approval\"}");
                    return;
                }
                historyRepository.clear();
                Path matchesDir = storageDir.resolve("matches");
                if (Files.exists(matchesDir)) {
                    try (var stream = Files.list(matchesDir)) {
                        stream.filter(Files::isRegularFile).forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
                    }
                }
                sendJson(exchange, 200, "{\"status\":\"history_cleared\"}");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            List<MatchHistoryRecord> history = historyRepository.readAll();
            history.sort(Comparator.comparing(MatchHistoryRecord::createdAt).reversed());
            StringBuilder json = new StringBuilder("{\"history\":[");
            for (int i = 0; i < history.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(history.get(i).toJson());
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    static class ReviewInsightsHandler implements HttpHandler {
        private final MatchHistoryRepository historyRepository;
        private final AuthService authService;

        ReviewInsightsHandler(MatchHistoryRepository historyRepository, AuthService authService) {
            this.historyRepository = historyRepository;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            if (requireCaseEditor(exchange, authService) == null) {
                return;
            }

            List<MatchHistoryRecord> history = historyRepository.readAll();
            history.sort(Comparator.comparing(MatchHistoryRecord::createdAt).reversed());

            List<MatchHistoryRecord> reviewQueue = history.stream()
                    .filter(item -> "review_required".equalsIgnoreCase(item.reviewStatus()))
                    .limit(10)
                    .toList();

            Map<String, List<MatchHistoryRecord>> unknownGroups = new HashMap<>();
            for (MatchHistoryRecord item : history) {
                if (!item.matched() && !isBlank(item.unknownGroupId())) {
                    unknownGroups.computeIfAbsent(item.unknownGroupId(), ignored -> new ArrayList<>()).add(item);
                }
            }

            List<String> groupedUnknowns = new ArrayList<>();
            for (Map.Entry<String, List<MatchHistoryRecord>> entry : unknownGroups.entrySet()) {
                if (entry.getValue().size() < 2) {
                    continue;
                }
                MatchHistoryRecord latest = entry.getValue().stream()
                        .max(Comparator.comparing(MatchHistoryRecord::createdAt))
                        .orElse(entry.getValue().get(0));
                groupedUnknowns.add("{"
                        + "\"groupId\":\"" + escape(entry.getKey()) + "\","
                        + "\"sightings\":" + entry.getValue().size() + ","
                        + "\"latestSnapshotUrl\":\"/files/matches/" + escape(latest.snapshotPath()) + "\","
                        + "\"latestSeenAt\":\"" + escape(latest.createdAt()) + "\""
                        + "}");
            }

            StringBuilder reviewJson = new StringBuilder();
            reviewJson.append("{\"reviewQueue\":[");
            for (int i = 0; i < reviewQueue.size(); i++) {
                if (i > 0) {
                    reviewJson.append(',');
                }
                reviewJson.append(reviewQueue.get(i).toJson());
            }
            reviewJson.append("],\"unknownSightings\":[");
            for (int i = 0; i < groupedUnknowns.size(); i++) {
                if (i > 0) {
                    reviewJson.append(',');
                }
                reviewJson.append(groupedUnknowns.get(i));
            }
            reviewJson.append("]}");
            sendJson(exchange, 200, reviewJson.toString());
        }
    }

    static class CaseManagementHandler implements HttpHandler {
        private final PersonRepository personRepository;
        private final MatchHistoryRepository historyRepository;
        private final AuthService authService;

        CaseManagementHandler(PersonRepository personRepository, MatchHistoryRepository historyRepository, AuthService authService) {
            this.personRepository = personRepository;
            this.historyRepository = historyRepository;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            if (requireAuth(exchange, authService) == null) {
                return;
            }

            List<PersonRecord> persons = personRepository.readAll();
            List<MatchHistoryRecord> history = historyRepository.readAll();
            persons.sort(Comparator.comparingInt((PersonRecord person) -> casePriority(person, history)).reversed());

            StringBuilder json = new StringBuilder("{\"cases\":[");
            for (int i = 0; i < persons.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                PersonRecord person = persons.get(i);
                long sightingCount = history.stream().filter(item -> item.matched() && person.id().equals(item.personId())).count();
                long pendingReviews = history.stream().filter(item -> person.id().equals(item.personId()) && "review_required".equalsIgnoreCase(item.reviewStatus())).count();
                String latestSighting = history.stream()
                        .filter(item -> item.matched() && person.id().equals(item.personId()))
                        .map(MatchHistoryRecord::createdAt)
                        .max(String::compareTo)
                        .orElse("");
                json.append("{")
                        .append("\"person\":").append(person.toAdminJson()).append(",")
                        .append("\"priority\":").append(casePriority(person, history)).append(",")
                        .append("\"sightingCount\":").append(sightingCount).append(",")
                        .append("\"pendingReviews\":").append(pendingReviews).append(",")
                        .append("\"latestSighting\":\"").append(escape(latestSighting)).append("\"")
                        .append("}");
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    static class ReviewStatusHandler implements HttpHandler {
        private final MatchHistoryRepository historyRepository;
        private final AuthService authService;

        ReviewStatusHandler(MatchHistoryRepository historyRepository, AuthService authService) {
            this.historyRepository = historyRepository;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            if (requireAuth(exchange, authService) == null) {
                return;
            }

            Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
            String historyId = trim(body.get("historyId"));
            String reviewStatus = trim(body.get("reviewStatus"));
            if (isBlank(historyId) || !List.of("review_required", "verified", "rejected").contains(reviewStatus.toLowerCase(Locale.ROOT))) {
                sendJson(exchange, 400, "{\"error\":\"valid historyId and reviewStatus are required\"}");
                return;
            }

            if (!historyRepository.updateReviewStatus(historyId, reviewStatus.toLowerCase(Locale.ROOT))) {
                sendJson(exchange, 404, "{\"error\":\"review item not found\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"review_updated\"}");
        }
    }

    static class CaseStatusHandler implements HttpHandler {
        private final PersonRepository personRepository;
        private final AuthService authService;

        CaseStatusHandler(PersonRepository personRepository, AuthService authService) {
            this.personRepository = personRepository;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            if (requireAuth(exchange, authService) == null) {
                return;
            }

            Map<String, String> body = parseSimpleJson(readString(exchange.getRequestBody()));
            String personId = trim(body.get("personId"));
            String status = trim(body.get("status"));
            if (isBlank(personId) || !List.of("Missing", "Under Investigation", "Sighted", "Found", "Closed")
                    .contains(status)) {
                sendJson(exchange, 400, "{\"error\":\"valid personId and lifecycle status are required\"}");
                return;
            }
            if (!personRepository.updateStatus(personId, status)) {
                sendJson(exchange, 404, "{\"error\":\"case not found\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"case_updated\"}");
        }
    }

    static class PersonsHandler implements HttpHandler {
        private final PersonRepository repository;
        private final ImageStorage imageStorage;
        private final AuthService authService;
        private final RecognitionService recognitionService;

        PersonsHandler(PersonRepository repository, ImageStorage imageStorage, AuthService authService, RecognitionService recognitionService) {
            this.repository = repository;
            this.imageStorage = imageStorage;
            this.authService = authService;
            this.recognitionService = recognitionService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            Session session = requireAuth(exchange, authService);
            if (session == null) {
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<PersonRecord> records = repository.readAll();
                StringBuilder json = new StringBuilder();
                json.append("{\"persons\":[");
                for (int i = 0; i < records.size(); i++) {
                    if (i > 0) {
                        json.append(',');
                    }
                    json.append(records.get(i).toAdminJson());
                }
                json.append("]}");
                sendJson(exchange, 200, json.toString());
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                MultipartForm form = MultipartForm.parse(exchange.getRequestBody(), contentType);
                String action = form.getField("action");

                if ("update-last-seen-notes".equalsIgnoreCase(trim(action))) {
                    String personId = form.getField("personId");
                    String lastSeen = form.getField("lastSeen");
                    String notes = form.getField("notes");
                    String latitude = form.getField("latitude");
                    String longitude = form.getField("longitude");
                    boolean hasLocation = !isBlank(latitude) && !isBlank(longitude);
                    if (isBlank(personId) || (isBlank(lastSeen) && isBlank(notes) && !hasLocation)) {
                        sendJson(exchange, 400, "{\"error\":\"personId and at least one of lastSeen, notes, or location is required\"}");
                        return;
                    }
                    PersonRecord updated = repository.updateLastSeenNotesAndLocation(
                            trim(personId),
                            trim(lastSeen),
                            trim(notes),
                            trim(latitude),
                            trim(longitude)
                    );
                    if (updated == null) {
                        sendJson(exchange, 404, "{\"error\":\"person not found\"}");
                        return;
                    }
                    sendJson(exchange, 200, "{\"person\":" + updated.toAdminJson() + "}");
                    return;
                }

                String name = form.getField("name");
                String age = form.getField("age");
                String notes = form.getField("notes");
                String lastSeen = form.getField("lastSeen");
                String latitude = form.getField("latitude");
                String longitude = form.getField("longitude");
                String contact = form.getField("contact");
                String status = form.getField("status");
                String descriptorRaw = form.getField("descriptor");
                String descriptorSetRaw = form.getField("descriptorSet");
                MultipartForm.FilePart image = form.getFile("image");

                if (isBlank(name) || isBlank(descriptorRaw) || image == null) {
                    sendJson(exchange, 400, "{\"error\":\"name, descriptor and image are required\"}");
                    return;
                }

                float[] descriptor = parseDescriptor(descriptorRaw);
                String normalizedDescriptorSet = isBlank(descriptorSetRaw) ? descriptorToString(descriptor) : trim(descriptorSetRaw);
                DuplicateRegistrationMatch duplicate = recognitionService.findRegistrationDuplicate(
                        repository.readAll(),
                        parseDescriptorSet(normalizedDescriptorSet),
                        0.45d
                );
                if (duplicate != null) {
                    sendJson(exchange, 409, "{"
                            + "\"duplicate\":true,"
                            + "\"error\":\"This face already exists in the database. A duplicate profile cannot be created.\","
                            + "\"message\":\"This image already exists in our database. If you are confident in your information, you can update only the last-seen details and notes for this person.\","
                            + "\"person\":" + duplicate.record().toAdminJson() + ","
                            + "\"lastSeen\":\"" + escape(duplicate.record().lastSeen()) + "\","
                            + "\"notes\":\"" + escape(duplicate.record().notes()) + "\","
                            + "\"latitude\":\"" + escape(duplicate.record().latitude()) + "\","
                            + "\"longitude\":\"" + escape(duplicate.record().longitude()) + "\","
                            + "\"distance\":" + formatDouble(duplicate.distance())
                            + "}");
                    return;
                }

                String id = UUID.randomUUID().toString();
                String imagePath;
                try {
                    imagePath = imageStorage.store("persons", id, image);
                } catch (IOException exception) {
                    System.err.println("Image upload failed: " + exception.getMessage());
                    sendJson(exchange, 502, "{\"error\":\"Image upload failed. Check Cloudinary configuration and try again.\"}");
                    return;
                }

                PersonRecord record = new PersonRecord(
                        id,
                        trim(name),
                        trim(age),
                        trim(lastSeen),
                        trim(latitude),
                        trim(longitude),
                        trim(contact),
                        trim(notes),
                        isBlank(status) ? "Missing" : trim(status),
                        imagePath,
                        descriptor,
                        normalizedDescriptorSet,
                        Instant.now().toString()
                );
                repository.append(record);
                sendJson(exchange, 201, "{\"person\":" + record.toAdminJson() + "}");
                return;
            }

            sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    static class MatchHandler implements HttpHandler {
        private final PersonRepository repository;
        private final MatchHistoryRepository historyRepository;
        private final ImageStorage imageStorage;
        private final AuthService authService;
        private final RecognitionService recognitionService = new RecognitionService();

        MatchHandler(PersonRepository repository, MatchHistoryRepository historyRepository, ImageStorage imageStorage, AuthService authService) {
            this.repository = repository;
            this.historyRepository = historyRepository;
            this.imageStorage = imageStorage;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            if (requireAuth(exchange, authService) == null) {
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            MultipartForm form = MultipartForm.parse(exchange.getRequestBody(), contentType);
            String descriptorRaw = form.getField("descriptor");
            String thresholdRaw = form.getField("threshold");
            MultipartForm.FilePart snapshot = form.getFile("snapshot");

            if (isBlank(descriptorRaw) || snapshot == null) {
                sendJson(exchange, 400, "{\"error\":\"descriptor and snapshot are required\"}");
                return;
            }

            float[] probe = parseDescriptor(descriptorRaw);
            double threshold = isBlank(thresholdRaw) ? 0.52d : Double.parseDouble(thresholdRaw);
            List<PersonRecord> persons = repository.readAll();
            MatchResult result = recognitionService.findBestMatch(persons, probe, threshold);
            List<MatchHistoryRecord> previousHistory = historyRepository.readAll();

            String snapshotName;
            try {
                snapshotName = imageStorage.store("matches", UUID.randomUUID().toString(), snapshot);
            } catch (IOException exception) {
                System.err.println("Snapshot upload failed: " + exception.getMessage());
                sendJson(exchange, 502, "{\"error\":\"Snapshot upload failed. Check Cloudinary configuration and try again.\"}");
                return;
            }
            String confidenceValue = result.record != null ? formatDouble(result.confidence) : "";
            String reviewStatus = result.record != null && result.confidence < 78d ? "review_required" : (result.record != null ? "confirmed" : "unknown");
            String unknownGroupId = result.record == null ? recognitionService.assignUnknownGroup(previousHistory, probe) : "";

            MatchHistoryRecord historyRecord = new MatchHistoryRecord(
                    UUID.randomUUID().toString(),
                    result.record != null,
                    result.record != null ? result.record.id() : "",
                    result.record != null ? result.record.name() : "",
                    result.record != null ? result.record.status() : "",
                    result.record != null ? formatDouble(result.distance) : "",
                    confidenceValue,
                    formatDouble(threshold),
                    reviewStatus,
                    descriptorToString(probe),
                    unknownGroupId,
                    snapshotName,
                    Instant.now().toString()
            );
            historyRepository.append(historyRecord);

            String response;
            if (result.record != null) {
                PersonRecord displayRecord = mergePersonDetails(result.record, persons);
                response = "{"
                        + "\"matched\":true,"
                        + "\"distance\":" + formatDouble(result.distance) + ","
                        + "\"cosineSimilarity\":" + formatDouble(result.cosineSimilarity) + ","
                        + "\"confidence\":" + formatDouble(result.confidence) + ","
                        + "\"reviewStatus\":\"" + escape(reviewStatus) + "\","
                        + "\"threshold\":" + formatDouble(threshold) + ","
                        + "\"person\":" + displayRecord.toPublicMatchJson() + ","
                        + "\"history\":" + historyRecord.toJson()
                        + "}";
            } else {
                response = "{"
                        + "\"matched\":false,"
                        + "\"distance\":null,"
                        + "\"cosineSimilarity\":null,"
                        + "\"confidence\":null,"
                        + "\"reviewStatus\":\"" + escape(reviewStatus) + "\","
                        + "\"unknownGroupId\":\"" + escape(unknownGroupId) + "\","
                        + "\"threshold\":" + formatDouble(threshold) + ","
                        + "\"history\":" + historyRecord.toJson()
                        + "}";
            }
            sendJson(exchange, 200, response);
        }
    }

    static class StaticHandler implements HttpHandler {
        private final Path root;

        StaticHandler(Path root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            String rawPath = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
            String relative = rawPath.equals("/") ? "index.html" : rawPath.substring(1);
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.exists(file) || Files.isDirectory(file)) {
                sendText(exchange, 404, "Not found", "text/plain; charset=utf-8");
                return;
            }
            sendBytes(exchange, 200, Files.readAllBytes(file), contentType(file));
        }
    }

    static class FileHandler implements HttpHandler {
        private final Path storageDir;

        FileHandler(Path storageDir) {
            this.storageDir = storageDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            String rawPath = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
            String relative = rawPath.replaceFirst("^/files/?", "");
            Path file = storageDir.resolve(relative).normalize();
            if (!file.startsWith(storageDir) || !Files.exists(file) || Files.isDirectory(file)) {
                sendText(exchange, 404, "Not found", "text/plain; charset=utf-8");
                return;
            }
            sendBytes(exchange, 200, Files.readAllBytes(file), contentType(file));
        }
    }

    interface ImageStorage {
        String store(String category, String id, MultipartForm.FilePart file) throws IOException;

        String label();

        static ImageStorage fromEnvironment(Path storageDir) {
            String backend = trim(System.getenv("APP_IMAGE_BACKEND"));
            if ("cloudinary".equalsIgnoreCase(backend)) {
                String cloudName = trim(System.getenv("APP_CLOUDINARY_CLOUD_NAME"));
                String uploadPreset = trim(System.getenv("APP_CLOUDINARY_UPLOAD_PRESET"));
                if (!cloudName.isEmpty() && !uploadPreset.isEmpty()) {
                    return new CloudinaryImageStorage(cloudName, uploadPreset, storageDir);
                }
                System.out.println("APP_IMAGE_BACKEND=cloudinary requested, but credentials are missing. Falling back to local files.");
            }
            return new LocalImageStorage(storageDir);
        }
    }

    static class LocalImageStorage implements ImageStorage {
        private final Path storageDir;

        LocalImageStorage(Path storageDir) {
            this.storageDir = storageDir;
        }

        @Override
        public String store(String category, String id, MultipartForm.FilePart file) throws IOException {
            String extension = extensionFor(file.fileName());
            Path target = storageDir.resolve(category).resolve(id + extension);
            Files.write(target, file.content());
            return target.getFileName().toString();
        }

        @Override
        public String label() {
            return "local";
        }
    }

    static class CloudinaryImageStorage implements ImageStorage {
        private final HttpClient client = HttpClient.newHttpClient();
        private final String cloudName;
        private final String uploadPreset;

        CloudinaryImageStorage(String cloudName, String uploadPreset, Path storageDir) {
            this.cloudName = cloudName;
            this.uploadPreset = uploadPreset;
        }

        @Override
        public String store(String category, String id, MultipartForm.FilePart file) throws IOException {
            String boundary = "----mpr" + UUID.randomUUID().toString().replace("-", "");
            String extension = extensionFor(file.fileName());
            String publicId = cloudinaryPublicId(category, id);
            String fileName = publicId + extension;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeMultipartText(body, boundary, "upload_preset", uploadPreset);
            writeMultipartText(body, boundary, "public_id", publicId);
            writeMultipartFile(body, boundary, "file", fileName, contentTypeForName(fileName), file.content());
            body.write(("--" + boundary + "--\r\n\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String responseBody = response.body();
                    if (responseBody.contains("Display name cannot contain slashes")) {
                        throw new IOException("Cloudinary upload failed: " + response.statusCode() + " " + responseBody
                                + ". Check the unsigned upload preset and turn off use_filename_as_display_name.");
                    }
                    throw new IOException("Cloudinary upload failed: " + response.statusCode() + " " + responseBody);
                }
                String secureUrl = jsonValue(response.body(), "secure_url");
                if (isBlank(secureUrl)) {
                    throw new IOException("Cloudinary upload succeeded but secure_url was missing");
                }
                return secureUrl;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Cloudinary upload interrupted", exception);
            }
        }

        @Override
        public String label() {
            return "cloudinary";
        }
    }

    static class MultipartForm {
        private final Map<String, String> fields = new HashMap<>();
        private final Map<String, FilePart> files = new HashMap<>();

        static MultipartForm parse(InputStream inputStream, String contentType) throws IOException {
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                throw new IOException("Expected multipart/form-data");
            }

            String boundaryToken = Arrays.stream(contentType.split(";"))
                    .map(String::trim)
                    .filter(part -> part.startsWith("boundary="))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Missing multipart boundary"));
            String boundary = "--" + boundaryToken.substring("boundary=".length());

            byte[] bodyBytes = readAll(inputStream);
            String body = new String(bodyBytes, StandardCharsets.ISO_8859_1);
            MultipartForm form = new MultipartForm();

            String[] parts = body.split(boundary);
            for (String part : parts) {
                if (part.isBlank() || "--".equals(part.trim())) {
                    continue;
                }
                String normalized = part;
                if (normalized.startsWith("\r\n")) {
                    normalized = normalized.substring(2);
                }
                if (normalized.endsWith("\r\n")) {
                    normalized = normalized.substring(0, normalized.length() - 2);
                }
                if (normalized.endsWith("--")) {
                    normalized = normalized.substring(0, normalized.length() - 2);
                }

                int separator = normalized.indexOf("\r\n\r\n");
                if (separator < 0) {
                    continue;
                }

                String headers = normalized.substring(0, separator);
                String content = normalized.substring(separator + 4);
                String disposition = Arrays.stream(headers.split("\r\n"))
                        .filter(line -> line.toLowerCase(Locale.ROOT).startsWith("content-disposition"))
                        .findFirst()
                        .orElse("");

                String name = extractDispositionValue(disposition, "name");
                String fileName = extractDispositionValue(disposition, "filename");
                if (name == null) {
                    continue;
                }

                byte[] partBytes = content.getBytes(StandardCharsets.ISO_8859_1);
                if (fileName != null && !fileName.isBlank()) {
                    form.files.put(name, new FilePart(fileName, partBytes));
                } else {
                    form.fields.put(name, content.trim());
                }
            }
            return form;
        }

        String getField(String name) {
            return fields.get(name);
        }

        FilePart getFile(String name) {
            return files.get(name);
        }

        static String extractDispositionValue(String disposition, String key) {
            String pattern = key + "=\"";
            int start = disposition.indexOf(pattern);
            if (start < 0) {
                return null;
            }
            int valueStart = start + pattern.length();
            int valueEnd = disposition.indexOf('"', valueStart);
            if (valueEnd < 0) {
                return null;
            }
            return disposition.substring(valueStart, valueEnd);
        }

        record FilePart(String fileName, byte[] content) {
        }
    }

    record DatabaseConfig(boolean enabled, String url, String username, String password, String label) {
        static DatabaseConfig fromEnvironment() {
            String url = trim(System.getenv("APP_DB_URL"));
            if (isBlank(url)) {
                return new DatabaseConfig(false, "", "", "", "");
            }
            return new DatabaseConfig(
                    true,
                    url,
                    trim(System.getenv("APP_DB_USER")),
                    trim(System.getenv("APP_DB_PASSWORD")),
                    "PostgreSQL"
            );
        }

        Connection openConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        void ensureSchema() throws IOException {
            if (!enabled) {
                return;
            }
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS persons ("
                        + "id VARCHAR(80) PRIMARY KEY,"
                        + "name VARCHAR(255) NOT NULL,"
                        + "age VARCHAR(32),"
                        + "last_seen VARCHAR(255),"
                        + "latitude VARCHAR(32),"
                        + "longitude VARCHAR(32),"
                        + "contact VARCHAR(255),"
                        + "notes TEXT,"
                        + "status VARCHAR(64),"
                        + "image_path VARCHAR(255),"
                        + "descriptor_text TEXT NOT NULL,"
                        + "descriptor_set TEXT,"
                        + "created_at VARCHAR(64) NOT NULL"
                        + ")");
                statement.executeUpdate("ALTER TABLE persons ADD COLUMN IF NOT EXISTS descriptor_set TEXT");
                statement.executeUpdate("ALTER TABLE persons ADD COLUMN IF NOT EXISTS latitude VARCHAR(32)");
                statement.executeUpdate("ALTER TABLE persons ADD COLUMN IF NOT EXISTS longitude VARCHAR(32)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS match_history ("
                        + "id VARCHAR(80) PRIMARY KEY,"
                        + "matched BOOLEAN NOT NULL,"
                        + "person_id VARCHAR(80),"
                        + "person_name VARCHAR(255),"
                        + "status VARCHAR(64),"
                        + "distance_value VARCHAR(32),"
                        + "confidence_value VARCHAR(32),"
                        + "threshold_value VARCHAR(32),"
                        + "review_status VARCHAR(64),"
                        + "descriptor_text TEXT,"
                        + "unknown_group_id VARCHAR(80),"
                        + "snapshot_path VARCHAR(255),"
                        + "created_at VARCHAR(64) NOT NULL"
                        + ")");
                statement.executeUpdate("ALTER TABLE match_history ADD COLUMN IF NOT EXISTS confidence_value VARCHAR(32)");
                statement.executeUpdate("ALTER TABLE match_history ADD COLUMN IF NOT EXISTS review_status VARCHAR(64)");
                statement.executeUpdate("ALTER TABLE match_history ADD COLUMN IF NOT EXISTS descriptor_text TEXT");
                statement.executeUpdate("ALTER TABLE match_history ADD COLUMN IF NOT EXISTS unknown_group_id VARCHAR(80)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                        + "email VARCHAR(255) PRIMARY KEY,"
                        + "password_hash VARCHAR(255) NOT NULL,"
                        + "role VARCHAR(32) NOT NULL,"
                        + "can_case_edit BOOLEAN NOT NULL DEFAULT FALSE"
                        + ")");
                statement.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS can_case_edit BOOLEAN NOT NULL DEFAULT FALSE");
            } catch (SQLException exception) {
                throw new IOException("Could not initialize PostgreSQL schema", exception);
            }
        }
    }

    static class PersonRepository {
        private final Path storageFile;
        private final DatabaseConfig databaseConfig;

        PersonRepository(Path storageFile) throws IOException {
            this(storageFile, new DatabaseConfig(false, "", "", "", ""));
        }

        PersonRepository(Path storageFile, DatabaseConfig databaseConfig) throws IOException {
            this.storageFile = storageFile;
            this.databaseConfig = databaseConfig;
            if (!databaseConfig.enabled()) {
                ensureFile(storageFile);
            }
        }

        synchronized void append(PersonRecord record) throws IOException {
            if (!databaseConfig.enabled()) {
                Files.writeString(storageFile, record.toTsv() + System.lineSeparator(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND);
                return;
            }
            try (Connection connection = databaseConfig.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO persons (id, name, age, last_seen, latitude, longitude, contact, notes, status, image_path, descriptor_text, descriptor_set, created_at) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, record.id());
                statement.setString(2, record.name());
                statement.setString(3, record.age());
                statement.setString(4, record.lastSeen());
                statement.setString(5, record.latitude());
                statement.setString(6, record.longitude());
                statement.setString(7, record.contact());
                statement.setString(8, record.notes());
                statement.setString(9, record.status());
                statement.setString(10, record.imagePath());
                statement.setString(11, descriptorToString(record.descriptor()));
                statement.setString(12, record.descriptorSet());
                statement.setString(13, record.createdAt());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IOException("Could not store person in PostgreSQL", exception);
            }
        }

        synchronized List<PersonRecord> readAll() throws IOException {
            if (databaseConfig.enabled()) {
                List<PersonRecord> records = new ArrayList<>();
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "SELECT id, name, age, last_seen, latitude, longitude, contact, notes, status, image_path, descriptor_text, descriptor_set, created_at "
                                     + "FROM persons ORDER BY created_at DESC");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        records.add(new PersonRecord(
                                resultSet.getString("id"),
                                safe(resultSet.getString("name")),
                                safe(resultSet.getString("age")),
                                safe(resultSet.getString("last_seen")),
                                safe(resultSet.getString("latitude")),
                                safe(resultSet.getString("longitude")),
                                safe(resultSet.getString("contact")),
                                safe(resultSet.getString("notes")),
                                safe(resultSet.getString("status")),
                                safe(resultSet.getString("image_path")),
                                parseDescriptor(resultSet.getString("descriptor_text")),
                                safe(resultSet.getString("descriptor_set")),
                                safe(resultSet.getString("created_at"))
                        ));
                    }
                } catch (SQLException exception) {
                    throw new IOException("Could not load persons from PostgreSQL", exception);
                }
                return records;
            }
            List<PersonRecord> records = new ArrayList<>();
            for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    records.add(PersonRecord.fromTsv(line));
                }
            }
            return records;
        }

        synchronized boolean updateStatus(String personId, String newStatus) throws IOException {
            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "UPDATE persons SET status = ? WHERE id = ?")) {
                    statement.setString(1, newStatus);
                    statement.setString(2, personId);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    throw new IOException("Could not update case status in PostgreSQL", exception);
                }
            }

            List<PersonRecord> records = readAll();
            List<String> updated = new ArrayList<>();
            boolean changed = false;
            for (PersonRecord record : records) {
                PersonRecord next = record;
                if (record.id().equals(personId)) {
                    next = new PersonRecord(
                            record.id(),
                            record.name(),
                            record.age(),
                            record.lastSeen(),
                            record.latitude(),
                            record.longitude(),
                            record.contact(),
                            record.notes(),
                            newStatus,
                            record.imagePath(),
                            record.descriptor(),
                            record.descriptorSet(),
                            record.createdAt()
                    );
                    changed = true;
                }
                updated.add(next.toTsv());
            }
            if (changed) {
                Files.writeString(storageFile, String.join(System.lineSeparator(), updated) + System.lineSeparator(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            }
            return changed;
        }

        synchronized PersonRecord updateLastSeenNotesAndLocation(String personId, String newLastSeen, String newNotes, String newLatitude, String newLongitude) throws IOException {
            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "UPDATE persons SET last_seen = ?, notes = ?, latitude = ?, longitude = ? WHERE id = ?")) {
                    statement.setString(1, newLastSeen);
                    statement.setString(2, newNotes);
                    statement.setString(3, newLatitude);
                    statement.setString(4, newLongitude);
                    statement.setString(5, personId);
                    if (statement.executeUpdate() == 0) {
                        return null;
                    }
                } catch (SQLException exception) {
                    throw new IOException("Could not update person details in PostgreSQL", exception);
                }
                return readAll().stream()
                        .filter(record -> record.id().equals(personId))
                        .findFirst()
                        .orElse(null);
            }

            List<PersonRecord> records = readAll();
            List<String> updated = new ArrayList<>();
            PersonRecord updatedRecord = null;
            for (PersonRecord record : records) {
                PersonRecord next = record;
                if (record.id().equals(personId)) {
                    next = new PersonRecord(
                        record.id(),
                        record.name(),
                        record.age(),
                        isBlank(newLastSeen) ? record.lastSeen() : newLastSeen,
                        isBlank(newLatitude) ? record.latitude() : newLatitude,
                        isBlank(newLongitude) ? record.longitude() : newLongitude,
                        record.contact(),
                        isBlank(newNotes) ? record.notes() : newNotes,
                        record.status(),
                            record.imagePath(),
                            record.descriptor(),
                            record.descriptorSet(),
                            record.createdAt()
                    );
                    updatedRecord = next;
                }
                updated.add(next.toTsv());
            }
            if (updatedRecord != null) {
                Files.writeString(storageFile, String.join(System.lineSeparator(), updated) + System.lineSeparator(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            }
            return updatedRecord;
        }
    }

    static class MatchHistoryRepository {
        private final Path storageFile;
        private final DatabaseConfig databaseConfig;

        MatchHistoryRepository(Path storageFile) throws IOException {
            this(storageFile, new DatabaseConfig(false, "", "", "", ""));
        }

        MatchHistoryRepository(Path storageFile, DatabaseConfig databaseConfig) throws IOException {
            this.storageFile = storageFile;
            this.databaseConfig = databaseConfig;
            if (!databaseConfig.enabled()) {
                ensureFile(storageFile);
            }
        }

        synchronized void append(MatchHistoryRecord record) throws IOException {
            if (!databaseConfig.enabled()) {
                Files.writeString(storageFile, record.toTsv() + System.lineSeparator(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND);
                return;
            }
            try (Connection connection = databaseConfig.openConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO match_history (id, matched, person_id, person_name, status, distance_value, confidence_value, threshold_value, review_status, descriptor_text, unknown_group_id, snapshot_path, created_at) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, record.id());
                statement.setBoolean(2, record.matched());
                statement.setString(3, record.personId());
                statement.setString(4, record.personName());
                statement.setString(5, record.status());
                statement.setString(6, record.distance());
                statement.setString(7, record.confidence());
                statement.setString(8, record.threshold());
                statement.setString(9, record.reviewStatus());
                statement.setString(10, record.descriptorText());
                statement.setString(11, record.unknownGroupId());
                statement.setString(12, record.snapshotPath());
                statement.setString(13, record.createdAt());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IOException("Could not store history in PostgreSQL", exception);
            }
        }

        synchronized List<MatchHistoryRecord> readAll() throws IOException {
            if (databaseConfig.enabled()) {
                List<MatchHistoryRecord> records = new ArrayList<>();
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "SELECT id, matched, person_id, person_name, status, distance_value, confidence_value, threshold_value, review_status, descriptor_text, unknown_group_id, snapshot_path, created_at "
                                     + "FROM match_history ORDER BY created_at DESC");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        records.add(new MatchHistoryRecord(
                                resultSet.getString("id"),
                                resultSet.getBoolean("matched"),
                                safe(resultSet.getString("person_id")),
                                safe(resultSet.getString("person_name")),
                                safe(resultSet.getString("status")),
                                safe(resultSet.getString("distance_value")),
                                safe(resultSet.getString("confidence_value")),
                                safe(resultSet.getString("threshold_value")),
                                safe(resultSet.getString("review_status")),
                                safe(resultSet.getString("descriptor_text")),
                                safe(resultSet.getString("unknown_group_id")),
                                safe(resultSet.getString("snapshot_path")),
                                safe(resultSet.getString("created_at"))
                        ));
                    }
                } catch (SQLException exception) {
                    throw new IOException("Could not load history from PostgreSQL", exception);
                }
                return records;
            }
            List<MatchHistoryRecord> records = new ArrayList<>();
            for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    records.add(MatchHistoryRecord.fromTsv(line));
                }
            }
            return records;
        }

        synchronized void clear() throws IOException {
            if (!databaseConfig.enabled()) {
                Files.writeString(storageFile, "", StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                return;
            }
            try (Connection connection = databaseConfig.openConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM match_history")) {
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IOException("Could not clear history in PostgreSQL", exception);
            }
        }

        synchronized boolean updateReviewStatus(String historyId, String reviewStatus) throws IOException {
            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "UPDATE match_history SET review_status = ? WHERE id = ?")) {
                    statement.setString(1, reviewStatus);
                    statement.setString(2, historyId);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    throw new IOException("Could not update review status in PostgreSQL", exception);
                }
            }

            List<MatchHistoryRecord> records = readAll();
            List<String> updated = new ArrayList<>();
            boolean changed = false;
            for (MatchHistoryRecord record : records) {
                MatchHistoryRecord next = record;
                if (record.id().equals(historyId)) {
                    next = new MatchHistoryRecord(
                            record.id(),
                            record.matched(),
                            record.personId(),
                            record.personName(),
                            record.status(),
                            record.distance(),
                            record.confidence(),
                            record.threshold(),
                            reviewStatus,
                            record.descriptorText(),
                            record.unknownGroupId(),
                            record.snapshotPath(),
                            record.createdAt()
                    );
                    changed = true;
                }
                updated.add(next.toTsv());
            }
            if (changed) {
                Files.writeString(storageFile, String.join(System.lineSeparator(), updated) + System.lineSeparator(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            }
            return changed;
        }
    }

    static class AuthService {
        private final Path userFile;
        private final DatabaseConfig databaseConfig;
        private final Map<String, Session> sessions = new ConcurrentHashMap<>();
        private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

        AuthService(Path userFile) throws IOException {
            this(userFile, new DatabaseConfig(false, "", "", "", ""));
        }

        AuthService(Path userFile, DatabaseConfig databaseConfig) throws IOException {
            this.userFile = userFile;
            this.databaseConfig = databaseConfig;
            if (!databaseConfig.enabled()) {
                ensureFile(userFile);
                if (Files.readString(userFile, StandardCharsets.UTF_8).isBlank()) {
                    Files.writeString(userFile,
                            "admin\t" + sha256("admin123") + "\tadmin\t1" + System.lineSeparator(),
                            StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                }
            } else if (listUsers().isEmpty()) {
                createUser("admin", "admin123", "admin", true);
            }
        }

        Session login(String username, String password) throws IOException {
            if (isBlank(username) || isBlank(password)) {
                return null;
            }

            if (FIXED_ADMIN_USERNAME.equals(username) && FIXED_ADMIN_PASSWORD.equals(password)) {
                Session session = new Session(randomToken(), FIXED_ADMIN_USERNAME, "admin", true, Instant.now().toString());
                sessions.put(session.token(), session);
                return session;
            }

            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "SELECT email, role, can_case_edit FROM users WHERE email = ? AND password_hash = ?")) {
                    statement.setString(1, username);
                    statement.setString(2, sha256(password));
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            Session session = new Session(
                                    randomToken(),
                                    resultSet.getString("email"),
                                    resultSet.getString("role"),
                                    resultSet.getBoolean("can_case_edit"),
                                    Instant.now().toString()
                            );
                            sessions.put(session.token(), session);
                            return session;
                        }
                    }
                } catch (SQLException exception) {
                    throw new IOException("Could not verify login against PostgreSQL", exception);
                }
                return null;
            }

            for (String line : Files.readAllLines(userFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) {
                    continue;
                }
                if (parts[0].equals(username) && parts[1].equals(sha256(password))) {
                    Session session = new Session(randomToken(), username, parts[2], parts.length >= 4 && "1".equals(parts[3]), Instant.now().toString());
                    sessions.put(session.token(), session);
                    return session;
                }
            }
            return null;
        }

        Session require(String token) {
            return sessions.get(token);
        }

        synchronized UserRecord createUser(String username, String password, String role, boolean canCaseEdit) throws IOException {
            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection()) {
                    try (PreparedStatement exists = connection.prepareStatement("SELECT email FROM users WHERE LOWER(email) = LOWER(?)")) {
                        exists.setString(1, username);
                        try (ResultSet resultSet = exists.executeQuery()) {
                            if (resultSet.next()) {
                                return null;
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users (email, password_hash, role, can_case_edit) VALUES (?, ?, ?, ?)")) {
                        statement.setString(1, username);
                        statement.setString(2, sha256(password));
                        statement.setString(3, role);
                        statement.setBoolean(4, canCaseEdit);
                        statement.executeUpdate();
                    }
                    return new UserRecord(username, role, canCaseEdit);
                } catch (SQLException exception) {
                    throw new IOException("Could not create user in PostgreSQL", exception);
                }
            }

            List<String> lines = Files.readAllLines(userFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length >= 1 && parts[0].equalsIgnoreCase(username)) {
                    return null;
                }
            }

            String entry = username + "\t" + sha256(password) + "\t" + role + "\t" + (canCaseEdit ? "1" : "0");
            Files.writeString(userFile, entry + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
            return new UserRecord(username, role, canCaseEdit);
        }

        synchronized String startPasswordReset(String email) throws IOException {
            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT email FROM users WHERE LOWER(email) = LOWER(?)")) {
                    statement.setString(1, email);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return null;
                        }
                    }
                } catch (SQLException exception) {
                    throw new IOException("Could not start password reset in PostgreSQL", exception);
                }
                String code = String.format(Locale.US, "%06d", (int) (Math.random() * 1_000_000));
                resetCodes.put(email.toLowerCase(Locale.ROOT), code);
                return code;
            }

            for (String line : Files.readAllLines(userFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length >= 3 && parts[0].equalsIgnoreCase(email)) {
                    String code = String.format(Locale.US, "%06d", (int) (Math.random() * 1_000_000));
                    resetCodes.put(email.toLowerCase(Locale.ROOT), code);
                    return code;
                }
            }
            return null;
        }

        synchronized boolean resetPassword(String email, String code, String newPassword) throws IOException {
            String expected = resetCodes.get(email.toLowerCase(Locale.ROOT));
            if (expected == null || !expected.equals(code)) {
                return false;
            }

            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "UPDATE users SET password_hash = ? WHERE LOWER(email) = LOWER(?)")) {
                    statement.setString(1, sha256(newPassword));
                    statement.setString(2, email);
                    if (statement.executeUpdate() == 0) {
                        return false;
                    }
                } catch (SQLException exception) {
                    throw new IOException("Could not reset password in PostgreSQL", exception);
                }
                resetCodes.remove(email.toLowerCase(Locale.ROOT));
                return true;
            }

            List<String> lines = Files.readAllLines(userFile, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>();
            boolean changed = false;
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) {
                    updated.add(line);
                    continue;
                }
                if (parts[0].equalsIgnoreCase(email)) {
                    updated.add(parts[0] + "\t" + sha256(newPassword) + "\t" + parts[2]);
                    changed = true;
                } else {
                    updated.add(line);
                }
            }

            if (!changed) {
                return false;
            }

            Files.writeString(
                    userFile,
                    String.join(System.lineSeparator(), updated) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
            resetCodes.remove(email.toLowerCase(Locale.ROOT));
            return true;
        }

        synchronized List<UserRecord> listUsers() throws IOException {
            if (databaseConfig.enabled()) {
                List<UserRecord> users = new ArrayList<>();
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT email, role, can_case_edit FROM users ORDER BY email");
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                    users.add(new UserRecord(resultSet.getString("email"), resultSet.getString("role"), resultSet.getBoolean("can_case_edit")));
                }
            } catch (SQLException exception) {
                throw new IOException("Could not list users from PostgreSQL", exception);
            }
                return users;
            }

            List<UserRecord> users = new ArrayList<>();
            for (String line : Files.readAllLines(userFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length >= 3) {
                    users.add(new UserRecord(parts[0], parts[2], parts.length >= 4 && "1".equals(parts[3])));
                }
            }
            return users;
        }

        synchronized boolean updateCaseAccess(String username, boolean canCaseEdit) throws IOException {
            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "UPDATE users SET can_case_edit = ? WHERE LOWER(email) = LOWER(?)")) {
                    statement.setBoolean(1, canCaseEdit);
                    statement.setString(2, username);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    throw new IOException("Could not update case access in PostgreSQL", exception);
                }
            }

            List<String> lines = Files.readAllLines(userFile, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>();
            boolean changed = false;
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) {
                    updated.add(line);
                    continue;
                }
                if (parts[0].equalsIgnoreCase(username)) {
                    updated.add(parts[0] + "\t" + parts[1] + "\t" + parts[2] + "\t" + (canCaseEdit ? "1" : "0"));
                    changed = true;
                } else if (parts.length >= 4) {
                    updated.add(line);
                } else {
                    updated.add(line + "\t0");
                }
            }
            if (changed) {
                Files.writeString(userFile, String.join(System.lineSeparator(), updated) + System.lineSeparator(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            }
            return changed;
        }

        synchronized boolean changePassword(String username, String currentPassword, String newPassword) throws IOException {
            if (databaseConfig.enabled()) {
                try (Connection connection = databaseConfig.openConnection();
                     PreparedStatement verify = connection.prepareStatement(
                             "SELECT email FROM users WHERE email = ? AND password_hash = ?");
                     PreparedStatement update = connection.prepareStatement(
                             "UPDATE users SET password_hash = ? WHERE email = ?")) {
                    verify.setString(1, username);
                    verify.setString(2, sha256(currentPassword));
                    try (ResultSet resultSet = verify.executeQuery()) {
                        if (!resultSet.next()) {
                            return false;
                        }
                    }
                    update.setString(1, sha256(newPassword));
                    update.setString(2, username);
                    return update.executeUpdate() > 0;
                } catch (SQLException exception) {
                    throw new IOException("Could not change password in PostgreSQL", exception);
                }
            }

            List<String> lines = Files.readAllLines(userFile, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>();
            boolean changed = false;

            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) {
                    updated.add(line);
                    continue;
                }

                if (parts[0].equals(username)) {
                    if (!parts[1].equals(sha256(currentPassword))) {
                        return false;
                    }
                    updated.add(parts[0] + "\t" + sha256(newPassword) + "\t" + parts[2]);
                    changed = true;
                } else {
                    updated.add(line);
                }
            }

            if (!changed) {
                return false;
            }

            Files.writeString(
                    userFile,
                    String.join(System.lineSeparator(), updated) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
            return true;
        }

        void logout(String token) {
            sessions.remove(token);
        }
    }

    record PersonRecord(
            String id,
            String name,
            String age,
            String lastSeen,
            String latitude,
            String longitude,
            String contact,
            String notes,
            String status,
            String imagePath,
            float[] descriptor,
            String descriptorSet,
            String createdAt
    ) {
        String toAdminJson() {
            return "{"
                    + "\"id\":\"" + escape(id) + "\","
                    + "\"name\":\"" + escape(name) + "\","
                    + "\"age\":\"" + escape(age) + "\","
                    + "\"lastSeen\":\"" + escape(lastSeen) + "\","
                    + "\"latitude\":\"" + escape(latitude) + "\","
                    + "\"longitude\":\"" + escape(longitude) + "\","
                    + "\"contact\":\"" + escape(contact) + "\","
                    + "\"notes\":\"" + escape(notes) + "\","
                    + "\"status\":\"" + escape(status) + "\","
                    + "\"imageUrl\":\"" + escape(publicAssetUrl("persons", imagePath)) + "\","
                    + "\"referenceCount\":" + descriptorVariantCount() + ","
                    + "\"createdAt\":\"" + escape(createdAt) + "\""
                    + "}";
        }

        String toPublicMatchJson() {
            return "{"
                    + "\"id\":\"" + escape(id) + "\","
                    + "\"name\":\"" + escape(name) + "\","
                    + "\"age\":\"" + escape(age) + "\","
                    + "\"lastSeen\":\"" + escape(lastSeen) + "\","
                    + "\"latitude\":\"" + escape(latitude) + "\","
                    + "\"longitude\":\"" + escape(longitude) + "\","
                    + "\"contact\":\"" + escape(contact) + "\","
                    + "\"notes\":\"" + escape(notes) + "\","
                    + "\"imageUrl\":\"" + escape(publicAssetUrl("persons", imagePath)) + "\","
                    + "\"createdAt\":\"" + escape(createdAt) + "\""
                    + "}";
        }

        String toTsv() {
            return String.join("\t",
                    safe(id),
                    safe(name),
                    safe(age),
                    safe(lastSeen),
                    safe(latitude),
                    safe(longitude),
                    safe(contact),
                    safe(notes),
                    safe(status),
                    safe(imagePath),
                    descriptorToString(descriptor),
                    safe(descriptorSet),
                    safe(createdAt)
            );
        }

        List<float[]> descriptorVariants() {
            return parseDescriptorSet(descriptorSet);
        }

        int descriptorVariantCount() {
            return descriptorVariants().size();
        }

        static PersonRecord fromTsv(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length == 9) {
                return new PersonRecord(
                        parts[0],
                        parts[1],
                        parts[2],
                        parts[3],
                        "",
                        "",
                        parts[4],
                        parts[5],
                        "Missing",
                        parts[6],
                        parseDescriptor(parts[7]),
                        parts[7],
                        parts[8]
                );
            }
            if (parts.length == 10) {
                return new PersonRecord(
                        parts[0],
                        parts[1],
                        parts[2],
                        parts[3],
                        "",
                        "",
                        parts[4],
                        parts[5],
                        parts[6],
                        parts[7],
                        parseDescriptor(parts[8]),
                        parts[8],
                        parts[9]
                );
            }
            if (parts.length == 11) {
                return new PersonRecord(
                        parts[0],
                        parts[1],
                        parts[2],
                        parts[3],
                        "",
                        "",
                        parts[4],
                        parts[5],
                        parts[6],
                        parts[7],
                        parseDescriptor(parts[8]),
                        parts[9],
                        parts[10]
                );
            }
            if (parts.length == 13) {
                return new PersonRecord(
                        parts[0],
                        parts[1],
                        parts[2],
                        parts[3],
                        parts[4],
                        parts[5],
                        parts[6],
                        parts[7],
                        parts[8],
                        parts[9],
                        parseDescriptor(parts[10]),
                        parts[11],
                        parts[12]
                );
            }
            return new PersonRecord(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parts[6],
                    parts[7],
                    parts[8],
                    parts[9],
                    parseDescriptor(parts[10]),
                    parts[11],
                    parts[12]
            );
        }
    }

    record MatchHistoryRecord(
            String id,
            boolean matched,
            String personId,
            String personName,
            String status,
            String distance,
            String confidence,
            String threshold,
            String reviewStatus,
            String descriptorText,
            String unknownGroupId,
            String snapshotPath,
            String createdAt
    ) {
        String toJson() {
            return "{"
                    + "\"id\":\"" + escape(id) + "\","
                    + "\"matched\":" + matched + ","
                    + "\"personId\":\"" + escape(personId) + "\","
                    + "\"personName\":\"" + escape(personName) + "\","
                    + "\"status\":\"" + escape(status) + "\","
                    + "\"distance\":" + (isBlank(distance) ? "null" : distance) + ","
                    + "\"confidence\":" + (isBlank(confidence) ? "null" : confidence) + ","
                    + "\"threshold\":" + (isBlank(threshold) ? "null" : threshold) + ","
                    + "\"reviewStatus\":\"" + escape(reviewStatus) + "\","
                    + "\"unknownGroupId\":\"" + escape(unknownGroupId) + "\","
                    + "\"snapshotUrl\":\"" + escape(publicAssetUrl("matches", snapshotPath)) + "\","
                    + "\"createdAt\":\"" + escape(createdAt) + "\""
                    + "}";
        }

        String toTsv() {
            return String.join("\t",
                    safe(id),
                    matched ? "1" : "0",
                    safe(personId),
                    safe(personName),
                    safe(status),
                    safe(distance),
                    safe(confidence),
                    safe(threshold),
                    safe(reviewStatus),
                    safe(descriptorText),
                    safe(unknownGroupId),
                    safe(snapshotPath),
                    safe(createdAt)
            );
        }

        static MatchHistoryRecord fromTsv(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length >= 13) {
                return new MatchHistoryRecord(
                        parts[0],
                        "1".equals(parts[1]),
                        parts[2],
                        parts[3],
                        parts[4],
                        parts[5],
                        parts[6],
                        parts[7],
                        parts[8],
                        parts[9],
                        parts[10],
                        parts[11],
                        parts[12]
                );
            }
            if (parts.length >= 10) {
                return new MatchHistoryRecord(
                        parts[0],
                        "1".equals(parts[1]),
                        parts[2],
                        parts[3],
                        parts[5],
                        parts[6],
                        parts[7],
                        "",
                        "",
                        "",
                        "",
                        parts[8],
                        parts[9]
                );
            }
            return new MatchHistoryRecord(
                    parts[0],
                    "1".equals(parts[1]),
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parts[6],
                    "",
                    "",
                    "",
                    "",
                    parts[7],
                    parts[8]
            );
        }
    }

    static class RecognitionService {
        MatchResult findBestMatch(List<PersonRecord> records, float[] probe, double threshold) {
            return records.stream()
                    .map(record -> bestResultForRecord(record, probe, threshold))
                    .filter(result -> result.distance <= threshold)
                    .max(Comparator.comparingDouble(result -> result.confidence))
                    .orElse(new MatchResult(null, Double.NaN, Double.NaN, Double.NaN));
        }

        DuplicateRegistrationMatch findRegistrationDuplicate(List<PersonRecord> records, List<float[]> probes, double threshold) {
            DuplicateRegistrationMatch best = null;
            for (PersonRecord record : records) {
                for (float[] existing : record.descriptorVariants()) {
                    for (float[] probe : probes) {
                        double distance = distance(existing, probe);
                        if (distance > threshold) {
                            continue;
                        }
                        if (best == null || distance < best.distance()) {
                            best = new DuplicateRegistrationMatch(record, distance);
                        }
                    }
                }
            }
            return best;
        }

        private MatchResult bestResultForRecord(PersonRecord record, float[] probe, double threshold) {
            MatchResult best = null;
            for (float[] candidate : record.descriptorVariants()) {
                double distance = distance(candidate, probe);
                double cosineSimilarity = cosineSimilarity(candidate, probe);
                double confidence = confidenceScore(distance, cosineSimilarity, threshold);
                MatchResult current = new MatchResult(record, distance, cosineSimilarity, confidence);
                if (best == null || current.confidence > best.confidence) {
                    best = current;
                }
            }
            return best == null ? new MatchResult(record, Double.NaN, Double.NaN, Double.NaN) : best;
        }

        String assignUnknownGroup(List<MatchHistoryRecord> history, float[] probe) {
            MatchHistoryRecord closest = history.stream()
                    .filter(item -> !item.matched() && !isBlank(item.descriptorText()))
                    .map(item -> new MatchHistoryRecordDistance(item, distance(parseDescriptor(item.descriptorText()), probe)))
                    .filter(item -> item.distance <= 0.45d)
                    .min(Comparator.comparingDouble(item -> item.distance))
                    .map(item -> item.record)
                    .orElse(null);
            if (closest == null) {
                return "unknown-" + UUID.randomUUID().toString().substring(0, 8);
            }
            return isBlank(closest.unknownGroupId()) ? "unknown-" + closest.id().substring(0, Math.min(8, closest.id().length())) : closest.unknownGroupId();
        }
    }

    record MatchHistoryRecordDistance(MatchHistoryRecord record, double distance) {
    }

    record DuplicateRegistrationMatch(PersonRecord record, double distance) {
    }

    record MatchResult(PersonRecord record, double distance, double cosineSimilarity, double confidence) {
    }

    record Session(String token, String username, String role, boolean canCaseEdit, String createdAt) {
    }

    record UserRecord(String username, String role, boolean canCaseEdit) {
        String toJson() {
            return "{"
                    + "\"email\":\"" + escape(username) + "\","
                    + "\"role\":\"" + escape(role) + "\","
                    + "\"canCaseEdit\":" + canCaseEdit
                    + "}";
        }
    }

    static Session requireAuth(HttpExchange exchange, AuthService authService) throws IOException {
        String token = bearerToken(exchange);
        Session session = authService.require(token);
        if (session == null) {
            sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return null;
        }
        return session;
    }

    static Session requireCaseEditor(HttpExchange exchange, AuthService authService) throws IOException {
        Session session = requireAuth(exchange, authService);
        if (session == null) {
            return null;
        }
        if (!"admin".equalsIgnoreCase(session.role()) && !session.canCaseEdit()) {
            sendJson(exchange, 403, "{\"error\":\"case edit access requires admin approval\"}");
            return null;
        }
        return session;
    }

    static boolean handleCors(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    static PersonRecord mergePersonDetails(PersonRecord selected, List<PersonRecord> allPersons) {
        if (selected == null) {
            return null;
        }
        List<PersonRecord> sameName = allPersons.stream()
                .filter(person -> trim(person.name()).equalsIgnoreCase(trim(selected.name())))
                .toList();
        return new PersonRecord(
                selected.id(),
                firstNonBlank(selected.name(), sameName.stream().map(PersonRecord::name).toList()),
                firstNonBlank(selected.age(), sameName.stream().map(PersonRecord::age).toList()),
                firstNonBlank(selected.lastSeen(), sameName.stream().map(PersonRecord::lastSeen).toList()),
                firstNonBlank(selected.latitude(), sameName.stream().map(PersonRecord::latitude).toList()),
                firstNonBlank(selected.longitude(), sameName.stream().map(PersonRecord::longitude).toList()),
                firstNonBlank(selected.contact(), sameName.stream().map(PersonRecord::contact).toList()),
                firstNonBlank(selected.notes(), sameName.stream().map(PersonRecord::notes).toList()),
                selected.status(),
                firstNonBlank(selected.imagePath(), sameName.stream().map(PersonRecord::imagePath).toList()),
                selected.descriptor(),
                selected.descriptorSet(),
                selected.createdAt()
        );
    }

    static String firstNonBlank(String current, List<String> candidates) {
        if (!isBlank(current)) {
            return current;
        }
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        sendText(exchange, statusCode, body, "application/json; charset=utf-8");
    }

    static void sendText(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        sendBytes(exchange, statusCode, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    static void sendBytes(HttpExchange exchange, int statusCode, byte[] bytes, String contentType) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    static String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".bin")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    static String extensionFor(String fileName) {
        if (fileName == null) {
            return ".bin";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            return ".bin";
        }
        String ext = fileName.substring(lastDot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,5}") ? ext : ".bin";
    }

    static String cloudinaryPublicId(String category, String id) {
        String normalized = (safe(category) + "_" + safe(id))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (!isBlank(normalized)) {
            return normalized;
        }
        return "upload_" + UUID.randomUUID().toString().replace("-", "");
    }

    static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }

    static String readString(InputStream inputStream) throws IOException {
        return new String(readAll(inputStream), StandardCharsets.UTF_8);
    }

    static float[] parseDescriptor(String raw) {
        String[] parts = raw.split(",");
        float[] values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Float.parseFloat(parts[i].trim());
        }
        return values;
    }

    static List<float[]> parseDescriptorSet(String raw) {
        List<float[]> descriptors = new ArrayList<>();
        String normalized = trim(raw);
        if (isBlank(normalized)) {
            return descriptors;
        }
        for (String part : normalized.split("\\|")) {
            String candidate = trim(part);
            if (!isBlank(candidate)) {
                descriptors.add(parseDescriptor(candidate));
            }
        }
        return descriptors;
    }

    static String descriptorToString(float[] descriptor) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < descriptor.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(descriptor[i]);
        }
        return builder.toString();
    }

    static double distance(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Descriptor length mismatch");
        }
        double sum = 0d;
        for (int i = 0; i < left.length; i++) {
            double diff = left[i] - right[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    static double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Descriptor length mismatch");
        }
        double dot = 0d;
        double leftNorm = 0d;
        double rightNorm = 0d;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0d || rightNorm == 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    static double confidenceScore(double distance, double cosineSimilarity, double threshold) {
        if (Double.isNaN(distance) || Double.isNaN(cosineSimilarity)) {
            return Double.NaN;
        }
        double normalizedDistance = Math.max(0d, 1d - (distance / Math.max(threshold, 0.0001d)));
        double normalizedCosine = Math.max(0d, Math.min(1d, (cosineSimilarity + 1d) / 2d));
        return (normalizedDistance * 0.7d + normalizedCosine * 0.3d) * 100d;
    }

    static int casePriority(PersonRecord person, List<MatchHistoryRecord> history) {
        int score = 40;
        int age = parseIntOrDefault(person.age(), 25);
        if (age <= 12 || age >= 60) {
            score += 25;
        } else if (age <= 18) {
            score += 15;
        }
        long sightings = history.stream().filter(item -> item.matched() && person.id().equals(item.personId())).count();
        score += Math.min(20, (int) sightings * 5);
        long pendingReviews = history.stream().filter(item -> person.id().equals(item.personId()) && "review_required".equalsIgnoreCase(item.reviewStatus())).count();
        score += Math.min(15, (int) pendingReviews * 5);
        String status = safe(person.status()).toLowerCase(Locale.ROOT);
        if (status.contains("missing")) {
            score += 15;
        } else if (status.contains("under investigation")) {
            score += 10;
        } else if (status.contains("sighted")) {
            score += 8;
        } else if (status.contains("found")) {
            score -= 10;
        } else if (status.contains("closed")) {
            score -= 20;
        }
        return Math.max(0, Math.min(100, score));
    }

    static Map<String, String> parseSimpleJson(String raw) {
        Map<String, String> values = new HashMap<>();
        String body = raw == null ? "" : raw.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return values;
        }

        for (String part : body.split(",")) {
            String[] pair = part.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = stripJson(pair[0]);
            String value = stripJson(pair[1]);
            values.put(key, value);
        }
        return values;
    }

    static String stripJson(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.replace("\\\"", "\"");
    }

    static String bearerToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "";
        }
        return authHeader.substring("Bearer ".length()).trim();
    }

    static void ensureFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "", StandardCharsets.UTF_8);
        }
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    static String randomToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    }

    static String escape(String input) {
        return safe(input)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }

    static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static boolean looksLikeEmail(String value) {
        return !isBlank(value) && value.contains("@") && value.indexOf('@') > 0 && value.indexOf('@') < value.length() - 1;
    }

    static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "null";
        }
        return String.format(Locale.US, "%.4f", value);
    }

    static int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(trim(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static String urlEncode(String value) {
        return java.net.URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    static String publicAssetUrl(String category, String value) {
        String normalized = safe(value);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "/files/" + category + "/" + normalized;
    }

    static void writeMultipartText(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    static void writeMultipartFile(ByteArrayOutputStream out, String boundary, String name, String fileName, String contentType, byte[] bytes) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    static String jsonValue(String raw, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = raw.indexOf(pattern);
        if (start < 0) {
            return "";
        }
        int valueStart = start + pattern.length();
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int index = valueStart; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (current == '"' && !escaped) {
                break;
            }
            if (escaped) {
                builder.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            builder.append(current);
        }
        return builder.toString().replace("\\/", "/");
    }

    static String contentTypeForName(String fileName) {

        String lower = safe(fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
