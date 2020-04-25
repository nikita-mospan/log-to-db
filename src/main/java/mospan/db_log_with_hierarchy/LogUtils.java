package mospan.db_log_with_hierarchy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class LogUtils {

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

    private static long getLogSequenceNextVal() {
        try (Connection connection = LogDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT nextval('SEQ_LOG_TABLE')")) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void startLog(final String logInstanceName) {
        isFirstLogEntryLocal.set(true);
        startLogIdLocal.set(LogUtils.getLogSequenceNextVal());
        currentLogIdLocal.set(startLogIdLocal.get());
        parentLogIdLocal.set(null);
        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        insertIntoLogInstances(startLogIdLocal.get(), logInstanceName, startTimestamp);
        openNextLevel(logInstanceName, null);
    }

    public static void openNextLevel(final String actionName, final String comments) {
        if (isFirstLogEntryLocal.get()) {
            isFirstLogEntryLocal.set(false);
        } else {
            parentLogIdLocal.set(currentLogIdLocal.get());
            currentLogIdLocal.set(LogUtils.getLogSequenceNextVal());
        }

        insertIntoLogTable(actionName, comments);
    }

    private static void insertIntoLogTable(String actionName, String comments) {
        final String SQL_INSERT = "INSERT INTO LOG_TABLE (action_name,\n" +
                "log_id,\n" +
                "parent_log_id,\n" +
                "start_ts,\n" +
                "status,\n" +
                "comments) VALUES (?,?,?,?,?,?)";

        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        try (final Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
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
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        closeLogInstance(logStatus.getStatus(), endTimestamp, startLogIdLocal.get());
        closeLevel(logStatus, exception);
    }

    public static void closeLevelSuccess() {
        closeLevel(LogStatus.COMPLETED, null);
    }

    public static void closeLevelFail(final Exception exception) {
        closeLevel(LogStatus.FAILED, exception);
    }

    private static void closeLevel(final LogStatus logStatus, final Exception exception) {
        Long newParentLogId = null;
        final String SELECT_NEW_PARENT_LOG_ID_SQL = "SELECT t.parent_log_id\n" +
                "            FROM   log_table t\n" +
                "            WHERE  t.log_id = ?";

        updateLogTable(logStatus, exception);
        if (parentLogIdLocal.get() != null) {
            currentLogIdLocal.set(parentLogIdLocal.get());
            try (Connection connection = LogDataSource.getConnection();
                 final PreparedStatement preparedStatement = connection.prepareStatement(SELECT_NEW_PARENT_LOG_ID_SQL)) {
                preparedStatement.setLong(1, parentLogIdLocal.get());
                final ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    newParentLogId = resultSet.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            currentLogIdLocal.set(startLogIdLocal.get());
        }

        parentLogIdLocal.set(newParentLogId);
    }

    private static void updateLogTable(final LogStatus logStatus, final Exception exception) {
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

        try (Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
            preparedStatement.setString(1, logStatus.getStatus());
            preparedStatement.setString(2, exceptionMessage);
            preparedStatement.setTimestamp(3, endTimestamp);
            preparedStatement.setLong(4, currentLogIdLocal.get());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void closeLogInstance(final String status, final Timestamp endTimestamp, final long startLogId) {
        final String UPDATE_LOG_INSTANCE_SQL = "update LOG_INSTANCES set status = ?, end_ts = ? where start_log_id = ?";
        try (final Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_INSTANCE_SQL)) {
            preparedStatement.setString(1, status);
            preparedStatement.setTimestamp(2, endTimestamp);
            preparedStatement.setLong(3, startLogId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void insertIntoLogInstances(long startLogId, String logInstanceName, java.sql.Timestamp startTimestamp) {
        final String SQL_INSERT = "INSERT INTO LOG_INSTANCES (start_log_id,\n" +
                "name,\n" +
                "start_ts,\n" +
                "status) VALUES (?,?,?,?)";
        try (final Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
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
        try (Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
            preparedStatement.setString(1, comments);
            preparedStatement.setLong(2, currentLogIdLocal.get());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
