package mospan.db_log_with_hierarchy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class LogEntry {

    private final String actionName;
    private final long logId;
    private final Long parentLogId;
    private final java.sql.Timestamp startTimestamp;
    private java.sql.Timestamp endTimestamp;
    private LogStatus logStatus;
    private String comments;
    private String exceptionMessage;

    LogEntry(String actionName, long logId, Long parentLogId, Timestamp startTimestamp, Timestamp endTimestamp, LogStatus logStatus,
             String comments, String exceptionMessage) {
        this.actionName = actionName;
        this.logId = logId;
        this.parentLogId = parentLogId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.logStatus = logStatus;
        this.comments = comments;
        this.exceptionMessage = exceptionMessage;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "actionName='" + actionName + '\'' +
                ", logId=" + logId +
                ", parentLogId=" + parentLogId +
                ", startTimestamp=" + startTimestamp +
                ", endTimestamp=" + endTimestamp +
                ", status='" + logStatus.getStatus() + '\'' +
                ", comments='" + comments + '\'' +
                ", exceptionMessage='" + exceptionMessage + '\'' +
                '}';
    }

    public LogEntry createChild(final String actionName, final String comments) {
        long newLogId = LogUtils.getLogSequenceNextVal();
        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        return createChild(actionName, newLogId, comments, startTimestamp);
    }

    LogEntry createChild(final String actionName, long newLogId, final String comments, Timestamp startTimestamp) {
        LogEntry newLogEntry = new LogEntry(actionName, newLogId, logId, startTimestamp, null,
                LogStatus.RUNNING,  comments, null);
        newLogEntry.insertIntoLogTable();
        return newLogEntry;
    }

    public void complete(final String comments) {
        closeEntry(LogStatus.COMPLETED, null, comments, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    public void fail(final String comments, final Exception exception) {
        closeEntry(LogStatus.FAILED, exception, comments, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    void closeEntry(final LogStatus logStatus, final Exception exception,
                    final String comments, final Timestamp endTimestamp) {
        final String UPDATE_LOG_TABLE_SQL = "UPDATE LOG_TABLE \n" +
                "        SET    status            = ?\n" +
                "              ,exception_message = ?\n" +
                "              ,end_ts            = ?\n" +
                "              ,comments          = coalesce(comments, '') || coalesce(?, '')\n" +
                "        WHERE  log_id = ?";
        String exceptionMessage = null;
        if (exception != null) {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            exceptionMessage = sw.toString();
        }
        setExceptionMessage(exceptionMessage);
        setComments(this.comments + comments);
        setLogStatus(logStatus);
        setEndTimestamp(endTimestamp);
        try (Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
            preparedStatement.setString(1, logStatus.getStatus());
            preparedStatement.setString(2, exceptionMessage);
            preparedStatement.setTimestamp(3, endTimestamp);
            preparedStatement.setString(4, comments);
            preparedStatement.setLong(5, logId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addComments(final String comments) {
        final String UPDATE_LOG_TABLE_SQL = "UPDATE LOG_TABLE SET comments = comments || ?\n" +
                "        WHERE  log_id = ?";
        setComments(this.comments + comments);
        try (Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
            preparedStatement.setString(1, comments);
            preparedStatement.setLong(2, logId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addCommentsAsync(final Future<LogEntry> logEntryFuture, final String comments) {
        CompletableFuture.runAsync(() -> {
            try {
                logEntryFuture.get().addComments(comments);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void info(final String actionName, final String comments) {
        final LogEntry infoLogEntry = createChild(actionName, comments);
        infoLogEntry.complete(null);
    }

    void insertIntoLogTable() {
        final String SQL_INSERT = "INSERT INTO LOG_TABLE (action_name,\n" +
                "log_id,\n" +
                "parent_log_id,\n" +
                "start_ts,\n" +
                "status,\n" +
                "comments) VALUES (?,?,?,?,?,?)";
        try (final Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
            preparedStatement.setString(1, actionName);
            preparedStatement.setLong(2, logId);
            preparedStatement.setObject(3, parentLogId);
            preparedStatement.setTimestamp(4, startTimestamp);
            preparedStatement.setString(5, logStatus.getStatus());
            preparedStatement.setString(6, comments);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long getLogId() {
        return logId;
    }

    private void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    private void setEndTimestamp(Timestamp endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    private void setLogStatus(LogStatus logStatus) {
        this.logStatus = logStatus;
    }

    private void setComments(String comments) {
        this.comments = comments;
    }

}
