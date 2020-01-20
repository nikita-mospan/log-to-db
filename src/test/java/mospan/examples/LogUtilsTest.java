package mospan.examples;

import mospan.db_log_with_hierarchy.LogEntry;
import mospan.db_log_with_hierarchy.LogUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

    @Test(expected = Exception.class)
    public void startStopLogFailAsync() {
        final Future<LogEntry> startLogEntryFuture = LogUtils.startLogAsync("test1");
        try {
            throw new RuntimeException("Some exception message.");
        } catch (Exception e) {
            LogUtils.stopLogFailAsync(startLogEntryFuture, e);
            throw new RuntimeException(e);
        } finally {
            System.out.println("In finally section");
            LogUtils.waitForLogThreadsToComplete();
            try {
                logger.info("start_log_id: " + startLogEntryFuture.get().getLogId());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void logSmokeTest() {
        final LogEntry startLogEntry = LogUtils.startLog("test2");
        try {
            logger.info("start_log_id: " + startLogEntry.getLogId());
            final LogEntry logEntry1 = startLogEntry.createChild("Action 1", "Some comments");
            final LogEntry logEntry2 = logEntry1.createChild("Action 2", "Some comments");
            logEntry2.info("Action info", "Some info");
            try {
                throw new RuntimeException("Some exception message.");
            } catch (Exception e) {
                logEntry2.fail(null, e);
            }
            final LogEntry logEntry3 = startLogEntry.createChild("Action 3", "Some comments");
            logEntry3.addComments("\nMore and more comments");
            logEntry3.complete(null);
            logEntry1.complete(null);
            LogUtils.stopLogSuccess(startLogEntry);
        } catch (Exception e) {
            LogUtils.stopLogFail(startLogEntry, e);
        }
    }

    @Test
    public void logSmokeTestAsync() throws ExecutionException, InterruptedException {
        final Future<LogEntry> startLogEntryFuture = LogUtils.startLogAsync("test2");
        try {
            final Future<LogEntry> logEntryFuture1 = LogUtils.createChildAsync(startLogEntryFuture, "Action 1", "Some comments");
            final Future<LogEntry> logEntryFuture2 = LogUtils.createChildAsync(logEntryFuture1, "Action 2", "Some comments");
            LogUtils.infoAsync(logEntryFuture2, "Action info", "Some info");
            try {
                throw new RuntimeException("Some exception message.");
            } catch (Exception e) {
                LogUtils.failAsync(logEntryFuture2, "Failed", e);
            }
            final Future<LogEntry> logEntryFuture3 = LogUtils.createChildAsync(startLogEntryFuture, "Action 3", "Some comments");
            LogEntry.addCommentsAsync(logEntryFuture3, "\nMore and more comments");
            LogUtils.completeAsync(logEntryFuture3, null);
            LogUtils.completeAsync(logEntryFuture1, null);
            LogUtils.stopLogSuccessAsync(startLogEntryFuture);
        } catch (Exception e) {
            LogUtils.stopLogFailAsync(startLogEntryFuture, e);
        } finally {
            logger.info("Waiting for logging threads to complete");
            LogUtils.waitForLogThreadsToComplete();
            logger.info("Logging threads completed.");
            logger.info("start log id: " + startLogEntryFuture.get().getLogId());
        }
    }

}
