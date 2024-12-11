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

package net.clydo.jedis.messaging.messenger.impl;

import lombok.val;
import net.clydo.jedis.messaging.bridge.JedisBridge;
import net.clydo.jedis.messaging.messenger.IJedisMessenger;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.concurrent.atomic.AtomicInteger;

public class JedisMessenger implements IJedisMessenger {
    private final JedisBridge jedisBridge;

    public JedisMessenger(final JedisBridge jedisBridge) {
        this.jedisBridge = jedisBridge;
    }

    @Override
    public long publish(String channel, String message) {
        return this.jedisBridge.bridge(jedis -> {
            return jedis.publish(channel, message);
        });
    }

    @Override
    public void subscribe(JedisPubSub jedisPubSub, String... channels) {
        val retryAttempts = new AtomicInteger(0);

        while (true) {
            this.jedisBridge.bridge(jedis -> {
                try {
                    jedis.subscribe(jedisPubSub, channels);
                    retryAttempts.set(0);
                } catch (JedisConnectionException e) {
                    val attempts = retryAttempts.incrementAndGet();
                    val backoffTime = Math.min(1000 * attempts, 30000);

                    try {
                        Thread.sleep(backoffTime);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    @Override
    public void subscribePattern(JedisPubSub jedisPubSub, String... patterns) {
        val retryAttempts = new AtomicInteger(0);

        while (true) {
            this.jedisBridge.bridge(jedis -> {
                try {
                    jedis.psubscribe(jedisPubSub, patterns);
                    retryAttempts.set(0);
                } catch (JedisConnectionException e) {
                    val attempts = retryAttempts.incrementAndGet();
                    val backoffTime = Math.min(1000 * attempts, 30000);

                    try {
                        Thread.sleep(backoffTime);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

}
