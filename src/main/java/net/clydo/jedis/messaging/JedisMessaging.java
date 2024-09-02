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

package net.clydo.jedis.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import net.clydo.jedis.messaging.annotations.JedisListener;
import net.clydo.jedis.messaging.callback.CallbacksHandler;
import net.clydo.jedis.messaging.callback.ReceiveCallback;
import net.clydo.jedis.messaging.callback.SendCallback;
import net.clydo.jedis.messaging.listener.Listener;
import net.clydo.jedis.messaging.listener.ListenerHandler;
import net.clydo.jedis.messaging.messenger.impl.JedisMessenger;
import net.clydo.jedis.messaging.packet.Packet;
import net.clydo.jedis.messaging.packet.PacketType;
import net.clydo.jedis.messaging.util.MultiThreading;
import net.clydo.jedis.messaging.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.JedisPool;

import java.io.Closeable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JedisMessaging implements Closeable {
    private final JedisMessenger messenger;
    private final Map<String, ListenerHandler> listenerHandlers;
    private final Map<String, CallbacksHandler> callbacksHandlers;
    private final Gson gson;
    @Getter
    private final String signature;
    private final JedisPool jedisPool;
    @Setter
    @Getter
    private String defaultPublishChannel;
    //private final Queue<Pair<String, Packet<JsonElement>>> packetQueue;
    //private final int maxPacketQueue;

    public JedisMessaging(final JedisPool jedisPool, final Gson gson) {
        this(20, jedisPool, gson);
    }

    public JedisMessaging(/*int maxPacketCache, */ final long callbacksExpiresIn, final JedisPool jedisPool, final Gson gson) {
        //this.maxPacketQueue = maxPacketCache;
        this.jedisPool = jedisPool;
        this.listenerHandlers = new ConcurrentHashMap<>();
        this.callbacksHandlers = new ConcurrentHashMap<>();
        //if (this.maxPacketQueue <= 0) {
        //    this.packetQueue = new LinkedList<>();
        //} else {
        //    this.packetQueue = null;
        //}
        this.messenger = new JedisMessenger(jedisPool);
        this.signature = UUID.randomUUID().toString();
        this.gson = gson;

        MultiThreading.scheduleAtFixedRate(() -> {
            for (var iterator = this.callbacksHandlers.entrySet().iterator(); iterator.hasNext(); ) {
                val entry = iterator.next();
                val value = entry.getValue();

                value.cleanup(callbacksExpiresIn);

                if (value.isEmpty()) {
                    iterator.remove();
                }
            }
        }, callbacksExpiresIn, callbacksExpiresIn, TimeUnit.SECONDS);
    }

    public void publish(final String channel, final String event, final Object message, final ReceiveCallback receiveCallback, final boolean skipSelf) {
        MultiThreading.execute(() -> {
            String callbackId = null;
            if (receiveCallback != null) {
                callbackId = this.putCallback(channel, receiveCallback);
            }

            val packet = new Packet<>(skipSelf ? this.signature : null, PacketType.EVENT, event, this.gson.toJsonTree(message), callbackId);

            this.publishPacket(channel, packet);
        });
    }

    public void publish(final String channel, final String event, final Object message, final boolean skipSelf) {
        this.publish(channel, event, message, null, skipSelf);
    }

    public void publish(final String event, final Object message, final ReceiveCallback receiveCallback, final boolean skipSelf) {
        if (this.defaultPublishChannel == null) {
            throw new IllegalStateException("No default channel specified, use setDefaultPublishChannel");
        }

        this.publish(this.defaultPublishChannel, event, message, receiveCallback, skipSelf);
    }

    public void publish(final String event, final Object message, final boolean skipSelf) {
        this.publish(event, message, null, skipSelf);
    }

    private String putCallback(final String channel, final ReceiveCallback receiveCallback) {
        val callbackId = UUID.randomUUID().toString();

        var handler = this.callbacksHandlers.get(channel);
        if (handler == null) {
            handler = new CallbacksHandler(this, this.gson);
            this.callbacksHandlers.put(channel, handler);

            val finalHandler = handler;
            MultiThreading.execute(() -> this.messenger.subscribePattern(finalHandler, channel));
        }

        handler.register(callbackId, receiveCallback);

        return callbackId;
    }

    public SendCallback callback(final String channel, final String callbackId, final String signature, final boolean skipSelf) {
        val sent = new boolean[]{false};
        return (data) -> MultiThreading.execute(() -> {
            if (!sent[0]) {
                sent[0] = true;

                val packet = new Packet<>(skipSelf ? signature : null, PacketType.CALLBACK, channel, data, callbackId);

                this.publishPacket(channel, packet);
            }
        });
    }

    //public void publishQueue() {
    //    if (this.packetQueue == null) {
    //        return;
    //    }
    //
    //    synchronized (this.packetQueue) {
    //        Pair<String, Packet<JsonElement>> pair;
    //        while ((pair = this.packetQueue.poll()) != null) {
    //            this.publishPacket(pair.left(), pair.right());
    //        }
    //
    //        this.packetQueue.clear();
    //    }
    //}

    private long publishPacket(final String channel, final Packet<JsonElement> packet) {
        val json = this.gson.toJson(packet);

        return this.messenger.publish(channel, json) - 1;

        //if (receivedCount == 0 && this.packetQueue != null && this.packetQueue.size() < this.maxPacketQueue) {
        //    this.packetQueue.add(Pair.of(channel, packet));
        //}
    }

    public void subscribe(final @NotNull Listener listener) {
        val jedisListener = ReflectionUtil.validateAnnotation(listener.getClass(), JedisListener.class);

        val pattern = jedisListener.pattern();
        val event = jedisListener.event();
        val channels = jedisListener.channels();

        if (pattern) {
            this._subscribePattern(listener, event, channels);
        } else {
            this._subscribeChannel(listener, event, channels);
        }
    }

    private void _subscribeChannel(final Listener listener, @Nullable final String event, final String... channels) {
        if (channels == null) {
            throw new IllegalStateException("Cannot subscribe without a channel");
        }

        for (String channel : channels) {
            var handler = this.listenerHandlers.get(channel);
            if (handler == null) {
                handler = new ListenerHandler(this, this.gson);
                this.listenerHandlers.put(channel, handler);

                val finalHandler = handler;
                MultiThreading.execute(() -> this.messenger.subscribe(finalHandler, channel));
            }

            if (event != null) {
                handler.register(event, listener);
            }
        }
    }

    private void _subscribePattern(final Listener listener, @Nullable final String event, final String... patterns) {
        if (patterns == null) {
            throw new IllegalStateException("Cannot subscribe without a pattern");
        }

        for (String pattern : patterns) {
            var handler = this.listenerHandlers.get(pattern);
            if (handler == null) {
                handler = new ListenerHandler(this, this.gson);
                this.listenerHandlers.put(pattern, handler);

                val finalHandler = handler;
                MultiThreading.execute(() -> this.messenger.subscribePattern(finalHandler, pattern));
            }

            if (event != null) {
                handler.register(event, listener);
            }
        }
    }

    @Override
    public void close() {
        MultiThreading.shutdownExecutors();

        this.jedisPool.close();
    }
}
