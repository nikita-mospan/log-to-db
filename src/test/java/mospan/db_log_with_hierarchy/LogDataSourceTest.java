package mospan.db_log_with_hierarchy;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LogDataSourceTest {

    private static final Logger logger = LoggerFactory.getLogger(LogDataSourceTest.class);

    @Test
    public void testGetLogSequenceNextVal() {
        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        final List<Future<Long>> futures = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> executorService.submit(LogUtils::getLogSequenceNextVal))
                .collect(Collectors.toList());
        futures.forEach(longFuture -> {
            try {
                logger.info(longFuture.get().toString());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        executorService.shutdown();
    }

}
