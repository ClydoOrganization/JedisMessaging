/*
 * This file is part of JedisMessaging.
 *
 * JedisMessaging is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * JedisMessaging is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JedisMessaging.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2024 ClydoNetwork
 */

package net.clydo.jedis.messaging.util;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@UtilityClass
public class Multithreading {
    private static final Logger LOGGER = Logger.getLogger(Multithreading.class.getName());

    private static final ThreadFactory THREAD_FACTORY = new CustomThreadFactory("JedisMessaging");
    @Getter
    private final ExecutorService POOL = Executors.newCachedThreadPool(THREAD_FACTORY);
    @Getter
    private final ScheduledExecutorService SCHEDULED_POOL = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors() + 1,
            THREAD_FACTORY
    );

    public void execute(Runnable task) {
        POOL.execute(() -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                LOGGER.log(Level.SEVERE, "Task threw exception", throwable);
                throw throwable;
            }
        });
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long initialDelay, long delay, TimeUnit unit) {
        return SCHEDULED_POOL.scheduleAtFixedRate(r, initialDelay, delay, unit);
    }

    public static void shutdownExecutors() {
        shutdownExecutor(POOL);
        shutdownExecutor(SCHEDULED_POOL);
    }

    public static void shutdownExecutor(@NotNull ExecutorService service) {
        service.shutdown();
        boolean flag;

        try {
            flag = service.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            flag = false;
        }

        if (!flag) {
            service.shutdownNow();
        }
    }

    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r, this.namePrefix + "-thread-" + this.threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
