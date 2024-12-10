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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JedisMessaging. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2024 ClydoNetwork
 */

package net.clydo.jedis.messaging.bridge;

import redis.clients.jedis.Jedis;

import java.util.function.Consumer;
import java.util.function.Function;

@FunctionalInterface
public interface JedisBridge {

    <T> T bridge(Function<Jedis, T> function);

    default void bridge(Consumer<Jedis> consumer) {
        bridge(jedis -> {
            consumer.accept(jedis);
            return null;
        });
    }

}
