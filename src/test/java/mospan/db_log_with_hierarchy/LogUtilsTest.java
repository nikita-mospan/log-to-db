package mospan.db_log_with_hierarchy;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LogUtilsTest {
    private static final Logger logger = LoggerFactory.getLogger(LogUtilsTest.class);

    @Test
    public void startStopLogSuccess() {
        final LogEntry startLogEntry = LogUtils.startLog("test1");
        logger.info("start_log_id: " + startLogEntry.getLogId());
        LogUtils.stopLogSuccess(startLogEntry);
    }

    @Test(expected = Exception.class)
    public void startStopLogFail() {
        final LogEntry startLogEntry = LogUtils.startLog("test1");
        logger.info("start_log_id: " + startLogEntry.getLogId());
        try {
            throw new RuntimeException("Some exception message.");
        } catch (Exception e) {
            LogUtils.stopLogFail(startLogEntry, e);
            throw new RuntimeException(e);
        }
    }

    @Test
    public void logSmokeTest() {
        final LogEntry startLogEntry = LogUtils.startLog("test2");
        logger.info("start_log_id: " + startLogEntry.getLogId());
        final LogEntry logEntry1 = startLogEntry.openLevel("Action 1", "Some comments");
        final LogEntry logEntry2 = logEntry1.openLevel("Action 2", "Some comments");
        logEntry2.closeLevelSuccess(null);
        final LogEntry logEntry3 = startLogEntry.openLevel("Action 3", "Some comments");
        logEntry3.addComments("\nMore and more comments");
        logEntry3.closeLevelSuccess(null);
        logEntry1.closeLevelSuccess(null);
        LogUtils.stopLogSuccess(startLogEntry);
    }

    @Test
    public void logSmokeTestAsync() throws ExecutionException, InterruptedException {
        final Future<LogEntry> startLogEntryFuture = LogUtils.startLogAsync("test2");
        final Future<LogEntry> logEntryFuture1 = LogEntry.openLevelAsync(startLogEntryFuture, "Action 1", "Some comments");
        final Future<LogEntry> logEntryFuture2 = LogEntry.openLevelAsync(logEntryFuture1, "Action 2", "Some comments");
        LogEntry.closeLevelSuccessAsync(logEntryFuture2, null);
        final Future<LogEntry> logEntryFuture3 = LogEntry.openLevelAsync(startLogEntryFuture,"Action 3", "Some comments");
        LogEntry.addCommentsAsync(logEntryFuture3, "\nMore and more comments");
        LogEntry.closeLevelSuccessAsync(logEntryFuture3, null);
        LogEntry.closeLevelSuccessAsync(logEntryFuture1, null);
        LogUtils.stopLogSuccessAsync(startLogEntryFuture);
        logger.info("Waiting for logging threads to complete");
        LogUtils.logExecutorService.shutdown();
        LogUtils.logExecutorService.awaitTermination(10, TimeUnit.HOURS);
        logger.info("Logging threads completed.");
        logger.info("start log id: " + startLogEntryFuture.get().getLogId());
    }

}
