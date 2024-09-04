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
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.JedisPubSub;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class CallbacksHandler extends JedisPubSub {
    private final Gson gson;
    private final JedisMessaging messaging;
    private final ConcurrentMap<String, ConcurrentLinkedQueue<Pair<Instant, ReceiveCallback>>> callbacks;

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

        if (this.shouldSkipProcessing(packet)) {
            return;
        }

        if (packet.type() == PacketType.CALLBACK.getId()) {
            val callbackId = packet.callbackId();
            if (callbackId != null) {
                this.processCallback(callbackId, channel, packet.data());
            }
        }
    }

    private boolean shouldSkipProcessing(@NotNull Packet<JsonElement> packet) {
        return packet.skipSelf() && Objects.equals(packet.signature(), this.messaging.getSignature());
    }

    public void processCallback(final String callbackId, final String channel, final JsonElement data) {
        val callbacksQueue = this.callbacks.get(callbackId);
        if (callbacksQueue != null) {
            callbacksQueue.forEach(pair -> {
                if (this.expired(pair)) {
                    return;
                }
                pair.right().call(channel, data);
            });
        }
    }

    public void register(String callbackId, ReceiveCallback receiveCallback) {
        this.callbacks
                .computeIfAbsent(callbackId, k -> new ConcurrentLinkedQueue<>())
                .add(Pair.of(Instant.now().plusSeconds(this.messaging.getCallbacksExpiresIn()), receiveCallback));
    }

    public void cleanup() {
        this.callbacks.forEach((key, queue) -> {
            queue.removeIf(this::expired);

            if (queue.isEmpty()) {
                this.callbacks.remove(key);
            }
        });
    }

    private boolean expired(@NotNull Pair<Instant, ReceiveCallback> pair) {
        return Instant.now().isAfter(pair.left());
    }

    public boolean isEmpty() {
        return this.callbacks.isEmpty();
    }
}
