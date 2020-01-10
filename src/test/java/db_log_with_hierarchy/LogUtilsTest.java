package db_log_with_hierarchy;

import mospan.db_log_with_hierarchy.LogEntry;
import mospan.db_log_with_hierarchy.LogUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

}
