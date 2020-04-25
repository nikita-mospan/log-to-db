package mospan.examples;

import mospan.aspectj.LogToDb;
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
    public void startStopLogSuccess() {
        LogUtils.startLog("test1");
        LogUtils.stopLogSuccess();
    }

    @Test(expected = Exception.class)
    public void startStopLogFail() {
        LogUtils.startLog("test2");
        try {
            throw new RuntimeException("Some exception message.");
        } catch (Exception e) {
            LogUtils.stopLogFail(e);
            throw new RuntimeException(e);
        }
    }

    @Test
    public void logSmokeTest() {
        LogUtils.startLog("test3");
        try {
            LogUtils.openNextLevel ("Action 1", "Some comments");
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
        } catch (Exception e) {
            LogUtils.stopLogFail(e);
        }
    }

    @Test
    public void logFromSeveralThreads() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        LogUtils.startLog("test4");
        for (int i = 0; i < 5; i++) {
            executorService.submit(() -> LogUtils.info(Thread.currentThread().getName(), "Comments from thread: " + Thread.currentThread().getId()));
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);
        LogUtils.stopLogSuccess();
    }

    @LogToDb
    private String concatStringAndInt (String s, int i) {
        return s + i;
    }

    @Test
    public void testLogToDbAspect() {
        LogUtils.startLog("test5");
        logger.info(concatStringAndInt("hello", 5));
        LogUtils.stopLogSuccess();
    }

}
