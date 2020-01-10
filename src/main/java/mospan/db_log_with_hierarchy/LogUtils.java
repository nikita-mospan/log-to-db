package mospan.db_log_with_hierarchy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class LogUtils {

    private LogUtils() {
        throw new RuntimeException("class LogUtils contains static methods and must not be instantiated!");
    }

    public static long getLogSequenceNextVal() {
        try (Connection connection = LogDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT nextval('SEQ_LOG_TABLE')")) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static LogEntry startLog(final String logInstanceName) {
        long logId = LogUtils.getLogSequenceNextVal();
        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        insertIntoLogInstances(logId, logInstanceName, startTimestamp);
        LogEntry logEntry = new LogEntry(logInstanceName, logId, null, startTimestamp, null, LogStatus.RUNNING,
                null, null, null);
        logEntry.insertIntoLogTable();
        return logEntry;
    }

    public static void stopLogSuccess(final LogEntry startLogEntry) {
        stopLog(startLogEntry, LogStatus.COMPLETED, null);
    }

    public static void stopLogFail(final LogEntry startLogEntry, final Exception exception) {
        stopLog(startLogEntry, LogStatus.FAILED, exception);
    }

    private static void stopLog(final LogEntry startLogEntry, final LogStatus logStatus, final Exception exception) {
        final long startLogId = startLogEntry.getLogId();
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        closeLogInstance(logStatus.getStatus(), endTimestamp, startLogId);
        startLogEntry.closeLevel(logStatus, exception, null, null, endTimestamp);
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

}
