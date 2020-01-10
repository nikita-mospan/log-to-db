package mospan.db_log_with_hierarchy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

class LogDataSource {
    private static final HikariConfig config = new HikariConfig("src/main/resources/log_datasource.properties");
    private static final HikariDataSource dataSource = new HikariDataSource(config);

    private LogDataSource() {
        throw new RuntimeException("Class LogDataSource must not be initialized!");
    }

    static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

}
