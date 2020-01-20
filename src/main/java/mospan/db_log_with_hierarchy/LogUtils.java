package mospan.db_log_with_hierarchy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LogUtils {

    private static final ExecutorService logExecutorService = Executors.newFixedThreadPool(10);

    private LogUtils() {
        throw new RuntimeException("class LogUtils contains static methods and must not be instantiated!");
    }

    static long getLogSequenceNextVal() {
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
        return startLog(logId, logInstanceName, startTimestamp);
    }

    private static LogEntry startLog(final long logId, final String logInstanceName, final Timestamp startTimestamp) {
        insertIntoLogInstances(logId, logInstanceName, startTimestamp);
        LogEntry logEntry = new LogEntry(logInstanceName, logId, null, startTimestamp, null, LogStatus.RUNNING,
                null, null);
        logEntry.insertIntoLogTable();
        return logEntry;
    }

    public static Future<LogEntry> startLogAsync(final String logInstanceName) {
        long logId = LogUtils.getLogSequenceNextVal();
        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        return CompletableFuture.supplyAsync(() -> startLog(logId, logInstanceName, startTimestamp), logExecutorService);
    }

    public static void stopLogSuccess(final LogEntry startLogEntry) {
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        stopLogSuccess(startLogEntry, endTimestamp);
    }

    public static void stopLogSuccessAsync(final Future<LogEntry> startLogEntryFuture) {
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        CompletableFuture.runAsync(() -> {
            try {
                stopLogSuccess(startLogEntryFuture.get(), endTimestamp);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, logExecutorService);
    }

    private static void stopLogSuccess(final LogEntry startLogEntry, final Timestamp endTimestamp) {
        stopLog(startLogEntry, LogStatus.COMPLETED, null, endTimestamp);
    }

    public static void stopLogFail(final LogEntry startLogEntry, final Exception exception) {
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        stopLogFail(startLogEntry, exception, endTimestamp);
    }

    public static void stopLogFailAsync(final Future<LogEntry> startLogEntryFuture, final Exception exception) {
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        CompletableFuture.runAsync(() -> {
            try {
                stopLogFail(startLogEntryFuture.get(), exception, endTimestamp);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, logExecutorService);
    }

    private static void stopLogFail(final LogEntry startLogEntry, final Exception exception, final Timestamp endTimestamp) {
        stopLog(startLogEntry, LogStatus.FAILED, exception, endTimestamp);
    }

    private static void stopLog(final LogEntry startLogEntry, final LogStatus logStatus, final Exception exception, final Timestamp endTimestamp) {
        final long startLogId = startLogEntry.getLogId();
        startLogEntry.closeEntry(logStatus, exception, null, endTimestamp);
        closeLogInstance(logStatus.getStatus(), endTimestamp, startLogId);
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

    public static void waitForLogThreadsToComplete() {
        LogUtils.logExecutorService.shutdown();
        try {
            LogUtils.logExecutorService.awaitTermination(10, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Future<LogEntry> createChildAsync(Future<LogEntry> parentEntryFuture, final String actionName, final String comments) {
        long newLogId = LogUtils.getLogSequenceNextVal();
        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        return CompletableFuture.supplyAsync(() -> {
            try {
                return parentEntryFuture.get().createChild(actionName, newLogId, comments, startTimestamp);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, LogUtils.logExecutorService);
    }

    public static void completeAsync(Future<LogEntry> logEntryFuture, final String comments) {
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        CompletableFuture.runAsync(() -> {
            try {
                logEntryFuture.get().closeEntry(LogStatus.COMPLETED, null, comments, endTimestamp);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, LogUtils.logExecutorService);
    }

    public static void failAsync(Future<LogEntry> logEntryFuture, final String comments, final Exception exception) {
        final Timestamp endTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        CompletableFuture.runAsync(() -> {
            try {
                logEntryFuture.get().closeEntry(LogStatus.FAILED, exception, comments, endTimestamp);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, LogUtils.logExecutorService);
    }

    public static void infoAsync(Future<LogEntry> logEntryFuture, final String actionName, final String comments) {
        final Future<LogEntry> infoLogEntryFuture = createChildAsync(logEntryFuture, actionName, comments);
        completeAsync(infoLogEntryFuture, null);
    }

}
