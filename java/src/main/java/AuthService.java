import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AuthService {
    private final JWTAuth jwtAuth;
    private final Map<Integer, TokenData> cache = new ConcurrentHashMap<>();

    public AuthService(Vertx vertx, DatabaseService databaseService) {

        String secretKey = "testsecret";

        jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256").setBuffer(secretKey)));
    }

    public void handleToken(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null || !body.containsKey("client_id")) {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Missing client_id").encode());
            return;
        }

        int clientId = body.getInteger("client_id");

        if (cache.containsKey(clientId)) {
            TokenData tokenData = cache.get(clientId);
            if (tokenData.expiry > (System.currentTimeMillis() / 1000) + 1800) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("token", tokenData.token).encode());
                return;
            }
        }
//        databaseService.getToken(clientId).onSuccess(tokenData -> {
//            if (tokenData != null && tokenData.expiry > (System.currentTimeMillis() / 1000) + 1800) {
//                logger.info("Returning database token for client_id: " + clientId);
//                cache.put(clientId, tokenData);
//                ctx.response().end(new JsonObject().put("token", tokenData.token).encode());
//            } else {
//                logger.info("Generating new token for client_id: " + clientId);
//                long now = System.currentTimeMillis() / 1000; // Текущее время в секундах
//                long expiry = now + 7200; // Токен живёт 2 часа
//
//                JsonObject claims = new JsonObject()
//                        .put("client_id", clientId)
//                        .put("iat", now)
//                        .put("exp", expiry);
//
//                String token = jwtAuth.generateToken(claims, new JWTOptions().setExpiresInSeconds(7200));
//
//                logger.info("Token generated with exp: " + expiry);
//
//                TokenData data = new TokenData(token, expiry);
//                cache.put(clientId, data);
//
//                databaseService.saveToken(clientId, token, expiry).onComplete(res -> {
//                    if (res.succeeded()) {
//                        logger.info("Token saved for client_id: " + clientId);
//                        ctx.response().end(new JsonObject().put("token", token).encode());
//                    } else {
//                        logger.severe("Failed to save token for client_id: " + clientId);
//                        ctx.response().setStatusCode(500).end("Failed to save token");
//                    }
//                });
//            }
//        }).onFailure(err -> {
//            logger.severe("Database error: " + err.getMessage());
//            err.printStackTrace();
//            ctx.response().setStatusCode(500).end("Database error");
//        });

        long now = System.currentTimeMillis() / 1000;
        long expiry = now + 7200;

        JsonObject claims = new JsonObject()
                .put("client_id", clientId)
                .put("iat", now)
                .put("exp", expiry);

        String token = jwtAuth.generateToken(claims, new JWTOptions().setExpiresInSeconds(7200));

        TokenData data = new TokenData(token, expiry);
        cache.put(clientId, data);

        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("token", token).encode());
    }

    public void handleCheck(RoutingContext ctx) {
        String token = ctx.request().getHeader("Authorization");
        if (token == null) {
            ctx.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Missing token").encode());
            return;
        }

        jwtAuth.authenticate(new TokenCredentials(token))
                .onSuccess(user -> {
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("message", "Authorized").encode());
                })
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.response()
                            .setStatusCode(401)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "Invalid token").encode());
                });
    }

    static class TokenData {
        String token;
        long expiry;

        TokenData(String token, long expiry) {
            this.token = token;
            this.expiry = expiry;
        }
    }
}
