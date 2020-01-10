package mospan.db_log_with_hierarchy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public final class LogEntry {

    private final String actionName;
    private final long logId;
    private final Long parentLogId;
    private final java.sql.Timestamp startTimestamp;
    private java.sql.Timestamp endTimestamp;
    private LogStatus logStatus;
    private Long rowCount;
    private String comments;
    private String exceptionMessage;

    LogEntry(String actionName, long logId, Long parentLogId, Timestamp startTimestamp, Timestamp endTimestamp, LogStatus logStatus,
             Long rowCount, String comments, String exceptionMessage) {
        this.actionName = actionName;
        this.logId = logId;
        this.parentLogId = parentLogId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.logStatus = logStatus;
        this.rowCount = rowCount;
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
                ", rowCount=" + rowCount +
                ", comments='" + comments + '\'' +
                ", exceptionMessage='" + exceptionMessage + '\'' +
                '}';
    }

    public LogEntry openLevel(final String actionName, final String comments) {
        long newLogId = LogUtils.getLogSequenceNextVal();
        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        LogEntry newLogEntry = new LogEntry(actionName, newLogId, logId, startTimestamp, null,
                LogStatus.RUNNING, null, comments, null);
        newLogEntry.insertIntoLogTable();
        return newLogEntry;
    }

    public void closeLevelSuccess(final String comments) {
        closeLevel(LogStatus.COMPLETED, null, comments, null, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    public void closeLevelFail(final String comments, final Exception exception) {
        closeLevel(LogStatus.FAILED, exception, comments, null, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    void closeLevel(final LogStatus logStatus, final Exception exception,
                    final String comments, final Long rowCount, final Timestamp endTimestamp) {
        final String UPDATE_LOG_TABLE_SQL = "UPDATE LOG_TABLE \n" +
                "        SET    status            = ?\n" +
                "              ,exception_message = ?\n" +
                "              ,row_count         = ?\n" +
                "              ,end_ts            = ?\n" +
                "              ,comments            = comments || ?\n" +
                "        WHERE  log_id = ?";
        String exceptionMessage = null;
        if (exception != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            exceptionMessage = sw.toString();
        }
        setExceptionMessage(exceptionMessage);
        setComments(comments);
        setLogStatus(logStatus);
        setRowCount(rowCount);
        setEndTimestamp(endTimestamp);
        try (Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
            preparedStatement.setString(1, logStatus.getStatus());
            preparedStatement.setString(2, exceptionMessage);
            preparedStatement.setObject(3, rowCount);
            preparedStatement.setTimestamp(4, endTimestamp);
            preparedStatement.setString(5, comments);
            preparedStatement.setLong(6, logId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

    private void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    private void setComments(String comments) {
        this.comments = comments;
    }

}
