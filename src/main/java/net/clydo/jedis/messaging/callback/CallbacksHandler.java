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

package net.clydo.jedis.messaging.callback;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.val;
import net.clydo.jedis.messaging.JedisMessaging;
import net.clydo.jedis.messaging.packet.Packet;
import net.clydo.jedis.messaging.packet.PacketType;
import net.clydo.jedis.messaging.util.Pair;
import redis.clients.jedis.JedisPubSub;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class CallbacksHandler extends JedisPubSub {
    private final Gson gson;
    private final JedisMessaging messaging;
    private final ConcurrentMap<String, ConcurrentLinkedQueue<Pair<Long, ReceiveCallback>>> callbacks;

    public CallbacksHandler(JedisMessaging messaging, Gson gson) {
        this.messaging = messaging;
        this.gson = gson;
        this.callbacks = new ConcurrentHashMap<>();
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        this.onPacket(channel, message);
    }

    @Override
    public void onMessage(String channel, String message) {
        this.onPacket(channel, message);
    }

    private void onPacket(String channel, String message) {
        val packet = this.gson.fromJson(message, Packet.PACKET_JSON_TYPE_TOKEN);

        val packetType = PacketType.ofId(packet.type());
        val signature = packet.signature();
        if (signature != null && Objects.equals(signature, this.messaging.getSignature())) {
            return;
        }

        val callbackId = packet.callbackId();
        val packetData = packet.data();

        if (packetType == PacketType.CALLBACK) {
            if (callbackId != null) {
                this.onCallback(callbackId, channel, packetData);
            }
        }
    }

    public void onCallback(final String callbackId, final String channel, final JsonElement data) {
        val callbacks = this.callbacks.get(callbackId);
        if (callbacks != null) {
            val iterator = callbacks.iterator();

            //noinspection WhileLoopReplaceableByForEach
            while (iterator.hasNext()) {
                val callback = iterator.next().right();
                callback.call(channel, data);
            }
        }
    }

    public void register(String callbackId, ReceiveCallback receiveCallback) {
        var callbacks = this.callbacks.get(callbackId);
        if (callbacks == null) {
            callbacks = new ConcurrentLinkedQueue<>();
            val tempListeners = this.callbacks.putIfAbsent(callbackId, callbacks);
            if (tempListeners != null) {
                callbacks = tempListeners;
            }
        }

        callbacks.add(Pair.of(nowEpochSecond(), receiveCallback));
    }

    public void cleanup(long expiresIn) {
        for (var iterator = this.callbacks.entrySet().iterator(); iterator.hasNext(); ) {
            val entry = iterator.next();
            val queue = entry.getValue();

            queue.removeIf(pair ->
                    pair.left() > toExpiresAt(expiresIn)
            );

            if (queue.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static long toExpiresAt(long expiresIn) {
        return nowEpochSecond() + expiresIn;
    }

    private static long nowEpochSecond() {
        return ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
    }

    public boolean isEmpty() {
        return this.callbacks.isEmpty();
    }
}
