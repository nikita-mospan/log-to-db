package mospan.log_to_db.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

class LogDataSource {
    private static final Logger logger = LoggerFactory.getLogger(LogDataSource.class);

    private static final HikariConfig config = new HikariConfig("src/main/resources/log_datasource.properties");
    private static final HikariDataSource dataSource = getDataSource();

    private LogDataSource() {
        throw new RuntimeException("Class LogDataSource must not be initialized!");
    }

    static Connection getConnection() throws SQLException  {
        return dataSource != null ? dataSource.getConnection() : null;
    }

    private static HikariDataSource getDataSource(){
        try {
            return new HikariDataSource(config);
        } catch (Exception e) {
            logger.warn("Can't create log data source due to: " + e.getMessage());
            logger.warn("Check log_datasource.properties");
            return null;
        }
    }

}
