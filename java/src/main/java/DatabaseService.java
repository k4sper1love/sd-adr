import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.*;

public class DatabaseService {
    private final Pool client;

    public DatabaseService(Vertx vertx) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(5432)
                .setHost("localhost")
                .setDatabase("db")
                .setUser("postgres")
                .setPassword("pass");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        client = Pool.pool(vertx, connectOptions, poolOptions);
    }

    public Future<Void> saveToken(int clientId, String token, long expiry) {
        return client.preparedQuery("INSERT INTO clients (client_id, token, expired_at) " +
                        "VALUES ($1, $2, $3) ON CONFLICT (client_id) DO UPDATE SET token = $2, expired_at = $3")
                .execute(Tuple.of(clientId, token, expiry))
                .mapEmpty();
    }

    public Future<AuthService.TokenData> getToken(int clientId) {
        return client.preparedQuery("SELECT token, expired_at FROM clients WHERE client_id = $1")
                .execute(Tuple.of(clientId))
                .map(rows -> {
                    if (rows.rowCount() > 0) {
                        Row row = rows.iterator().next();
                        return new AuthService.TokenData(row.getString("token"), row.getLong("expired_at"));
                    }
                    return null;
                });
    }
}
