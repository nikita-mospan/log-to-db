package mospan.examples;

import mospan.aspectj.LogToDb;
import mospan.aspectj.RootLog;
import mospan.db_log_with_hierarchy.LogUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LogUtilsTest {
    private static final Logger logger = LoggerFactory.getLogger(LogUtilsTest.class);

    @Test
    @RootLog
    public void startStopLogSuccess() {
    }

    @Test(expected = Exception.class)
    @RootLog
    public void startStopLogFail() {
        throw new RuntimeException("Some exception message.");
    }

    @Test
    @RootLog
    public void logSmokeTest() {
        LogUtils.openNextLevel("Action 1", "Some comments");
        LogUtils.openNextLevel("Action 2", "Some comments");
        LogUtils.info("Action info", "Some info");
        try {
            throw new RuntimeException("Some exception message.");
        } catch (Exception e) {
            LogUtils.closeLevelFail(e);
        }
        LogUtils.openNextLevel("Action 3", "Some comments");
        LogUtils.addComments("\nMore and more comments");
        LogUtils.closeLevelSuccess();
        LogUtils.closeLevelSuccess();
        LogUtils.stopLogSuccess();
    }

    @Test
    @RootLog
    public void logFromSeveralThreads() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 5; i++) {
            executorService.submit(() -> LogUtils.info(Thread.currentThread().getName(), "Comments from thread: " + Thread.currentThread().getId()));
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);
    }

    @LogToDb
    private String concatStringAndInt(String s, int i) {
        return s + i;
    }

    @Test
    @RootLog
    public void testLogToDbAspect() {
        logger.info(concatStringAndInt("hello", 5));
    }

}
