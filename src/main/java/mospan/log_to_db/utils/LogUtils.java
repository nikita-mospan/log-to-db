package mospan.log_to_db.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class LogUtils {

    private static final Logger logger = LoggerFactory.getLogger(LogUtils.class);

    private static final ThreadLocal<Boolean> isFirstLogEntryLocal = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> startLogIdLocal = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> currentLogIdLocal = new InheritableThreadLocal<>();
    private static final ThreadLocal<Long> parentLogIdLocal = new InheritableThreadLocal<>();

    private LogUtils() {
        throw new RuntimeException("class LogUtils contains static methods and must not be instantiated!");
    }

    public static long getStartLogId() {
        return startLogIdLocal.get();
    }

    public static long getCurrentLogId() {
        return currentLogIdLocal.get();
    }

    public static long getParentLogId() {
        return parentLogIdLocal.get();
    }

    private static long getLogSequenceNextVal(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT nextval('SEQ_LOG_TABLE')")) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void startLog(final String logInstanceName) {
        startLog(logInstanceName, null);
    }

    public static void startLog(final String logInstanceName, final String comments) {
        try (Connection connection = LogDataSource.getConnection()) {
            if (connection != null) {
                isFirstLogEntryLocal.set(true);
                startLogIdLocal.set(LogUtils.getLogSequenceNextVal(connection));
                currentLogIdLocal.set(startLogIdLocal.get());
                parentLogIdLocal.set(null);
                Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
                insertIntoLogInstances(startLogIdLocal.get(), logInstanceName, startTimestamp, connection);
                openNextLevel(logInstanceName, comments, connection);
                logger.info("startLogId: " + getStartLogId());
            }
        } catch (SQLException e) {
            logger.warn("startLog failed due to: " + e.getMessage());
        }
    }

    public static void openNextLevel(final String actionName, final String comments) {
        try (Connection connection = LogDataSource.getConnection()) {
            if (connection != null) {
                openNextLevel(actionName, comments, connection);
            }
        }catch (SQLException e) {
            logger.warn("openNextLevel failed due to: " + e.getMessage());
        }
    }

    private static void openNextLevel(final String actionName, final String comments, final Connection connection) {
        if (isFirstLogEntryLocal.get()) {
            isFirstLogEntryLocal.set(false);
        } else {
            parentLogIdLocal.set(currentLogIdLocal.get());
            currentLogIdLocal.set(LogUtils.getLogSequenceNextVal(connection));
        }

        insertIntoLogTable(actionName, comments, connection);
    }

    private static void insertIntoLogTable(String actionName, String comments, Connection connection) {
        final String SQL_INSERT = "INSERT INTO LOG_TABLE (action_name,\n" +
                "log_id,\n" +
                "parent_log_id,\n" +
                "start_ts,\n" +
                "status,\n" +
                "comments) VALUES (?,?,?,?,?,?)";

        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        try (final PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
            preparedStatement.setString(1, actionName);
            preparedStatement.setLong(2, currentLogIdLocal.get());
            preparedStatement.setObject(3, parentLogIdLocal.get());
            preparedStatement.setTimestamp(4, startTimestamp);
            preparedStatement.setString(5, LogStatus.RUNNING.getStatus());
            preparedStatement.setString(6, comments);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void stopLogSuccess() {
        stopLog(LogStatus.COMPLETED, null);
    }

    public static void stopLogFail(final Exception exception) {
        stopLog(LogStatus.FAILED, exception);
    }

    private static void stopLog(final LogStatus logStatus, final Exception exception) {
        try(Connection connection = LogDataSource.getConnection()) {
            if (connection != null) {
                final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
                closeLogInstance(logStatus.getStatus(), endTimestamp, startLogIdLocal.get(), connection);
                closeLevel(logStatus, exception, connection);
            }
        } catch (SQLException e) {
            logger.warn("stopLog failed due to: " + e.getMessage());
        }
    }

    public static void closeLevelSuccess() {
        try(Connection connection = LogDataSource.getConnection()) {
            if (connection != null) {
                closeLevel(LogStatus.COMPLETED, null, connection);
            }
        } catch (SQLException e) {
            logger.warn("closeLevelSuccess failed due to: " + e.getMessage());
        }
    }

    public static void closeLevelFail(final Exception exception) {
        try(Connection connection = LogDataSource.getConnection()) {
            if (connection != null) {
                closeLevel(LogStatus.FAILED, exception, connection);
            }
        } catch (SQLException e) {
            logger.warn("closeLevelSuccess failed due to: " + e.getMessage());
        }
    }

    private static void closeLevel(final LogStatus logStatus, final Exception exception, Connection connection) {
        Long newParentLogId = null;
        final String SELECT_NEW_PARENT_LOG_ID_SQL = "SELECT t.parent_log_id\n" +
                "            FROM   log_table t\n" +
                "            WHERE  t.log_id = ?";

        updateLogTable(logStatus, exception, connection);
        if (parentLogIdLocal.get() != null) {
            currentLogIdLocal.set(parentLogIdLocal.get());
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SELECT_NEW_PARENT_LOG_ID_SQL)) {
                preparedStatement.setLong(1, parentLogIdLocal.get());
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        newParentLogId = resultSet.getLong(1);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            currentLogIdLocal.set(startLogIdLocal.get());
        }

        parentLogIdLocal.set(newParentLogId);
    }

    private static void updateLogTable(final LogStatus logStatus, final Exception exception, Connection connection) {
        final String UPDATE_LOG_TABLE_SQL = "UPDATE LOG_TABLE \n" +
                "        SET    status            = ?\n" +
                "              ,exception_message = ?\n" +
                "              ,end_ts            = ?\n" +
                "        WHERE  log_id = ?";
        String exceptionMessage = null;
        if (exception != null) {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            exceptionMessage = sw.toString();
        }

        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());

        try (final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
            preparedStatement.setString(1, logStatus.getStatus());
            preparedStatement.setString(2, exceptionMessage);
            preparedStatement.setTimestamp(3, endTimestamp);
            preparedStatement.setLong(4, currentLogIdLocal.get());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void closeLogInstance(final String status, final Timestamp endTimestamp, final long startLogId, final Connection connection) {
        final String UPDATE_LOG_INSTANCE_SQL = "update LOG_INSTANCES set status = ?, end_ts = ? where start_log_id = ?";
        try (final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_INSTANCE_SQL)) {
            preparedStatement.setString(1, status);
            preparedStatement.setTimestamp(2, endTimestamp);
            preparedStatement.setLong(3, startLogId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void insertIntoLogInstances(long startLogId, String logInstanceName, java.sql.Timestamp startTimestamp, Connection connection) {
        final String SQL_INSERT = "INSERT INTO LOG_INSTANCES (start_log_id,\n" +
                "name,\n" +
                "start_ts,\n" +
                "status) VALUES (?,?,?,?)";
        try (final PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
            preparedStatement.setLong(1, startLogId);
            preparedStatement.setString(2, logInstanceName);
            preparedStatement.setTimestamp(3, startTimestamp);
            preparedStatement.setString(4, LogStatus.RUNNING.getStatus());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void info(final String actionName, final String comments) {
        openNextLevel(actionName, comments);
        closeLevelSuccess();
    }

    public static void addComments(final String comments) {
        final String UPDATE_LOG_TABLE_SQL = "UPDATE LOG_TABLE SET comments = comments || ?\n" +
                "        WHERE  log_id = ?";
        try (Connection connection = LogDataSource.getConnection()) {
            if (connection != null) {
                try (final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
                    preparedStatement.setString(1, comments);
                    preparedStatement.setLong(2, currentLogIdLocal.get());
                    preparedStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.warn("addComments failed due to: " + e.getMessage());
        }
    }

}
