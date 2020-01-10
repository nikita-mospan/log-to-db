package mospan.db_log_with_hierarchy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import static mospan.db_log_with_hierarchy.LogUtils.COMPLETED_STATUS;
import static mospan.db_log_with_hierarchy.LogUtils.FAILED_STATUS;
import static mospan.db_log_with_hierarchy.LogUtils.RUNNING_STATUS;
import static mospan.db_log_with_hierarchy.LogUtils.insertIntoLogTable;

public final class LogEntry {

    private final String actionName;
    private final long logId;
    private final Long parentLogId;
    private final java.sql.Timestamp startTimestamp;
    private java.sql.Timestamp endTimestamp;
    private String status;
    private Long rowCount;
    private String comments;
    private String exceptionMessage;

    LogEntry(String actionName, long logId, Long parentLogId, Timestamp startTimestamp, Timestamp endTimestamp, String status,
             Long rowCount, String comments, String exceptionMessage) {
        this.actionName = actionName;
        this.logId = logId;
        this.parentLogId = parentLogId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.status = status;
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
                ", status='" + status + '\'' +
                ", rowCount=" + rowCount +
                ", comments='" + comments + '\'' +
                ", exceptionMessage='" + exceptionMessage + '\'' +
                '}';
    }

    public LogEntry openLevel(final String actionName, final String comments) {
        long newLogId = LogUtils.getLogSequenceNextVal();
        Timestamp startTimestamp = new java.sql.Timestamp(System.currentTimeMillis());
        LogEntry newLogEntry = new LogEntry(actionName, newLogId, logId, startTimestamp, null,
                RUNNING_STATUS, null, comments, null);
        insertIntoLogTable(newLogEntry);
        return newLogEntry;
    }

    public void closeLevelSuccess(final String comments, final Long rowCount) {
        closeLevel(COMPLETED_STATUS, null, comments, rowCount, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    public void closeLevelFail(final String comments, final Exception exception, final Long rowCount) {
        closeLevel(FAILED_STATUS, exception, comments, rowCount, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    void closeLevel(final String status, final Exception exception,
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
        setStatus(status);
        setRowCount(rowCount);
        setEndTimestamp(endTimestamp);
        try (Connection connection = LogDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_LOG_TABLE_SQL)) {
            preparedStatement.setString(1, status);
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

    public String getActionName() {
        return actionName;
    }

    public long getLogId() {
        return logId;
    }

    public Long getParentLogId() {
        return parentLogId;
    }

    public Timestamp getStartTimestamp() {
        return startTimestamp;
    }

    public Timestamp getEndTimestamp() {
        return endTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public String getComments() {
        return comments;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    private void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    private void setEndTimestamp(Timestamp endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    private void setStatus(String status) {
        this.status = status;
    }

    private void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    private void setComments(String comments) {
        this.comments = comments;
    }

}
