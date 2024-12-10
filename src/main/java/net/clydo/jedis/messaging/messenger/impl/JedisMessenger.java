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

import net.clydo.jedis.messaging.messenger.IJedisMessenger;
import net.clydo.jedis.messaging.bridge.JedisBridge;
import redis.clients.jedis.JedisPubSub;

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
        this.jedisBridge.bridge(jedis -> {
            jedis.subscribe(jedisPubSub, channels);
        });
    }

    @Override
    public void subscribePattern(JedisPubSub jedisPubSub, String... patterns) {
        this.jedisBridge.bridge(jedis -> {
            jedis.psubscribe(jedisPubSub, patterns);
        });
    }
}
