package myau.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadUtil {

    private static final ExecutorService POOL = Executors.newCachedThreadPool(
        r -> new Thread(r, "AIMyau-Async-" + System.currentTimeMillis())
    );

    public static void runAsync(Runnable runnable) {
        if (runnable != null) {
            POOL.execute(runnable);
        }
    }

    public static void shutdown() {
        POOL.shutdown();
    }
}
