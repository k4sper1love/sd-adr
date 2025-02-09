import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends AbstractVerticle {
    public static void main(String[] args) {
        System.out.println("Starting application");
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle(), res -> {
            if (res.succeeded()) {
                System.out.println("MainVerticle deployed");
            } else {
                System.err.println("Failed to deploy MainVerticle: " + res.cause());
            }
        });
    }

    @Override
    public void start() {
        System.out.println("MainVerticle starting");

        try {
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());

            DatabaseService databaseService = new DatabaseService(vertx);
            AuthService authService = new AuthService(vertx, databaseService);

            router.post("/token").handler(authService::handleToken);
            router.get("/check").handler(authService::handleCheck);

            vertx.createHttpServer().requestHandler(router).listen(8080, result -> {
                if (result.succeeded()) {
                    System.out.println("Server started on port 8080");
                } else {
                    System.err.println("Failed to start server: " + result.cause());
                }
            });
        } catch (Exception e) {
            System.err.println("Exception in start(): " + e.getMessage());
            e.printStackTrace();
        }
    }
}
