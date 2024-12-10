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

import lombok.Getter;
import lombok.Setter;
import lombok.val;
import net.clydo.jedis.messaging.annotations.JedisChannels;
import net.clydo.jedis.messaging.annotations.JedisEvent;
import net.clydo.jedis.messaging.bridge.DataBridge;
import net.clydo.jedis.messaging.bridge.JedisBridge;
import net.clydo.jedis.messaging.callback.CallbacksHandler;
import net.clydo.jedis.messaging.callback.ReceiveCallback;
import net.clydo.jedis.messaging.callback.SendCallback;
import net.clydo.jedis.messaging.listener.InvokableListener;
import net.clydo.jedis.messaging.listener.Listener;
import net.clydo.jedis.messaging.listener.ListenerHandler;
import net.clydo.jedis.messaging.messenger.impl.JedisMessenger;
import net.clydo.jedis.messaging.packet.Packet;
import net.clydo.jedis.messaging.packet.PacketType;
import net.clydo.jedis.messaging.util.Multithreading;
import net.clydo.jedis.messaging.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JedisMessaging is a messaging system built on top of Redis, utilizing Jedis.
 * It provides mechanisms to publish and subscribe to messages, handle callbacks,
 * and manage listeners for specific events or patterns.
 */
public class JedisMessaging<D> implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(JedisMessaging.class.getName());

    private final DataBridge<D> dataBridge;
    private final JedisMessenger messenger;
    private final Map<String, ListenerHandler<D>> listenerHandlers;
    private final Map<String, CallbacksHandler<D>> callbacksHandlers;
    @Getter
    private final String signature; // Unique identifier for this instance of JedisMessaging.
    @Getter
    private final long callbacksExpiresIn;
    @Setter
    private String defaultPublishChannel;

    /**
     * Constructor that initializes the JedisMessaging instance.
     *
     * @param jedisBridge the Redis bridge
     * @param dataBridge  dataBridge for serialization/deserialization
     */
    public JedisMessaging(final JedisBridge jedisBridge, final DataBridge<D> dataBridge) {
        this(jedisBridge, dataBridge, 20);
    }

    /**
     * Constructor that initializes the JedisMessaging instance with specified callback expiration time.
     *
     * @param jedisBridge        the Redis bridge
     * @param dataBridge         dataBridge for serialization/deserialization
     * @param callbacksExpiresIn time in seconds after which callbacks expire
     */
    public JedisMessaging(final JedisBridge jedisBridge, final DataBridge<D> dataBridge, final long callbacksExpiresIn) {
        this.callbacksExpiresIn = callbacksExpiresIn;
        this.dataBridge = dataBridge;
        this.messenger = new JedisMessenger(jedisBridge);
        this.listenerHandlers = new ConcurrentHashMap<>();
        this.callbacksHandlers = new ConcurrentHashMap<>();
        this.signature = UUID.randomUUID().toString();

        Multithreading.scheduleAtFixedRate(() -> {
            for (var iterator = this.callbacksHandlers.entrySet().iterator(); iterator.hasNext(); ) {
                val entry = iterator.next();
                val value = entry.getValue();

                value.cleanup();

                if (value.isEmpty()) {
                    iterator.remove();
                }
            }
        }, callbacksExpiresIn, callbacksExpiresIn, TimeUnit.SECONDS);
    }

    /**
     * Publishes a message to a specified channel with an optional callback and the option to skip the sender.
     *
     * @param channel         the channel to publish the message to
     * @param event           the event type of the message
     * @param message         the message to be published
     * @param receiveCallback the callback to handle the response (can be null)
     * @param skipSelf        whether to skip receiving the message on the same instance
     */
    public void publish(final String channel, final String event, final Object message, final ReceiveCallback receiveCallback, final boolean skipSelf) {
        Multithreading.execute(() -> {
            String callbackId = null;
            if (receiveCallback != null) {
                callbackId = this.putCallback(channel, receiveCallback);
            }

            val packet = new Packet<>(this.signature, PacketType.EVENT, event, this.dataBridge.encodeData(message), callbackId, skipSelf);

            this._publishPacket(channel, packet);
        });
    }

    /**
     * Publishes a message to a specified channel without a callback.
     *
     * @param channel  the channel to publish the message to
     * @param event    the event type of the message
     * @param message  the message to be published
     * @param skipSelf whether to skip receiving the message on the same instance
     */
    public void publish(final String channel, final String event, final Object message, final boolean skipSelf) {
        this.publish(channel, event, message, null, skipSelf);
    }

    /**
     * Publishes a message to the default channel with an optional callback and the option to skip the sender.
     *
     * @param event           the event type of the message
     * @param message         the message to be published
     * @param receiveCallback the callback to handle the response (can be null)
     * @param skipSelf        whether to skip receiving the message on the same instance
     */
    public void publish(final String event, final Object message, final ReceiveCallback receiveCallback, final boolean skipSelf) {
        if (this.defaultPublishChannel == null) {
            throw new IllegalStateException("No default channel specified, use setDefaultPublishChannel");
        }

        this.publish(this.defaultPublishChannel, event, message, receiveCallback, skipSelf);
    }

    /**
     * Publishes a message to the default channel without a callback.
     *
     * @param event    the event type of the message
     * @param message  the message to be published
     * @param skipSelf whether to skip receiving the message on the same instance
     */
    public void publish(final String event, final Object message, final boolean skipSelf) {
        this.publish(event, message, null, skipSelf);
    }

    /**
     * Registers a callback for a specific channel.
     *
     * @param channel         the channel where the callback is registered
     * @param receiveCallback the callback to handle the response
     * @return the callback ID
     */
    private String putCallback(final String channel, final ReceiveCallback receiveCallback) {
        val callbackId = UUID.randomUUID().toString();

        var handler = this.callbacksHandlers.get(channel);
        if (handler == null) {
            handler = new CallbacksHandler<>(this, this.dataBridge);
            this.callbacksHandlers.put(channel, handler);

            val finalHandler = handler;
            Multithreading.execute(() -> this.messenger.subscribePattern(finalHandler, channel));
        }

        handler.register(callbackId, receiveCallback);

        return callbackId;
    }

    /**
     * Publishes a packet to a specific channel.
     *
     * @param channel the channel to publish the packet to
     * @param packet  the packet to be published
     * @return the number of clients that received the message, minus the sender
     */
    public long _publishPacket(final String channel, final Packet<D> packet) {
        val json = this.dataBridge.encodePacket(packet);
        return this.messenger.publish(channel, json) - 1;
    }

    /**
     * Subscribes a listener to events or patterns as defined by the JedisListener annotation.
     *
     * @param listener the listeners to subscribe
     */
    public void subscribe(final @NotNull Listener listener) {
        val clazz = listener.getClass();
        val jedisChannels = ReflectionUtil.validateAnnotation(clazz, JedisChannels.class);
        val jedisEvent = ReflectionUtil.validateAnnotation(clazz, JedisEvent.class);

        val pattern = jedisChannels.pattern();
        val event = jedisEvent.value();
        val channels = jedisChannels.value();

        if (pattern) {
            this._subscribePattern(listener, event, channels);
        } else {
            this._subscribeChannel(listener, event, channels);
        }
    }

    /**
     * Subscribes a listener to events or patterns as defined by the JedisListener annotation.
     *
     * @param listeners the listeners to subscribe
     */
    public <L> void subscribeFrom(final @NotNull L listeners) {
        val clazz = listeners.getClass();

        var jedisChannels = ReflectionUtil.getAnnotation(clazz, JedisChannels.class, true);

        for (Method method : clazz.getDeclaredMethods()) {
            val methodClass = method.getClass();

            val annotation = ReflectionUtil.getAnnotation(methodClass, JedisChannels.class, true);
            if (annotation != null) {
                jedisChannels = annotation;
            }

            val jedisEvent = ReflectionUtil.getAnnotation(method, JedisEvent.class, true);

            if (jedisChannels != null && jedisEvent != null) {
                try {
                    val expectedTypes = new Class<?>[]{String.class, null, SendCallback.class};

                    ReflectionUtil.validateMethodParameters(method, expectedTypes);
                } catch (IllegalArgumentException e) {
                    LOGGER.log(Level.WARNING, "", e);
                    continue; // Skip this method and move to the next one
                }

                val pattern = jedisChannels.pattern();
                val event = jedisEvent.value();
                val channels = jedisChannels.value();

                val listener = new InvokableListener<>(method, listeners);
                if (pattern) {
                    this._subscribePattern(listener, event, channels);
                } else {
                    this._subscribeChannel(listener, event, channels);
                }
            }
        }
    }

    /**
     * Subscribes a listener to specific channels.
     *
     * @param listener the listener to subscribe
     * @param event    the event type to listen for (can be null)
     * @param channels the channels to subscribe to
     */
    private void _subscribeChannel(final Listener listener, @Nullable final String event, final String... channels) {
        if (channels == null) {
            throw new IllegalStateException("Cannot subscribe without a channel");
        }

        for (String channel : channels) {
            var handler = this.listenerHandlers.get(channel);
            if (handler == null) {
                handler = new ListenerHandler<>(this, this.dataBridge);
                this.listenerHandlers.put(channel, handler);

                val finalHandler = handler;
                Multithreading.execute(() -> this.messenger.subscribe(finalHandler, channel));
            }

            if (event != null) {
                handler.register(event, listener);
            }
        }
    }

    /**
     * Subscribes a listener to patterns.
     *
     * @param listener the listener to subscribe
     * @param event    the event type to listen for (can be null)
     * @param patterns the patterns to subscribe to
     */
    private void _subscribePattern(final Listener listener, @Nullable final String event, final String... patterns) {
        if (patterns == null) {
            throw new IllegalStateException("Cannot subscribe without a pattern");
        }

        for (String pattern : patterns) {
            var handler = this.listenerHandlers.get(pattern);
            if (handler == null) {
                handler = new ListenerHandler<>(this, this.dataBridge);
                this.listenerHandlers.put(pattern, handler);

                val finalHandler = handler;
                Multithreading.execute(() -> this.messenger.subscribePattern(finalHandler, pattern));
            }

            if (event != null) {
                handler.register(event, listener);
            }
        }
    }

    /**
     * Closes the JedisMessaging instance, shutting down executors and closing the Jedis pool.
     */
    @Override
    public void close() {
        Multithreading.shutdownExecutors();
    }
}
