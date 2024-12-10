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

package net.clydo.jedis.messaging.listener;

import lombok.val;
import net.clydo.jedis.messaging.JedisMessaging;
import net.clydo.jedis.messaging.bridge.DataBridge;
import net.clydo.jedis.messaging.callback.SendCallback;
import net.clydo.jedis.messaging.packet.Packet;
import net.clydo.jedis.messaging.packet.PacketData;
import net.clydo.jedis.messaging.packet.PacketType;
import net.clydo.jedis.messaging.util.Multithreading;
import redis.clients.jedis.JedisPubSub;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class ListenerHandler<D> extends JedisPubSub {
    private final DataBridge<D> dataBridge;
    private final ConcurrentMap<String, ConcurrentLinkedQueue<Listener>> listeners;
    private final JedisMessaging<D> messaging;

    public ListenerHandler(JedisMessaging<D> messaging, DataBridge<D> dataBridge) {
        this.listeners = new ConcurrentHashMap<>();
        this.messaging = messaging;
        this.dataBridge = dataBridge;
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
        val packet = this.dataBridge.decodePacket(message);

        val packetType = PacketType.ofId(packet.type());
        val signature = packet.signature();
        val skipSelf = packet.skipSelf();
        if (skipSelf && Objects.equals(signature, this.messaging.getSignature())) {
            return;
        }

        val callbackId = packet.callbackId();
        val packetData = packet.data();

        if (packetType == PacketType.EVENT) {
            val packetEvent = packet.event();

            val listeners = this.listeners.get(packetEvent);
            if (listeners != null) {
                val iterator = listeners.iterator();

                //noinspection WhileLoopReplaceableByForEach
                while (iterator.hasNext()) {
                    val listener = iterator.next();
                    listener.call(channel, new PacketData<>(packetData, this.dataBridge), (callbackId != null ? this.callback(channel, callbackId, this.messaging.getSignature(), signature != null) : null));
                }
            }
        }
    }

    public SendCallback callback(final String channel, final String callbackId, final String signature, final boolean skipSelf) {
        val sent = new boolean[]{false};

        return (data) -> {
            if (!sent[0]) {
                sent[0] = true;
                Multithreading.execute(() -> {
                    val packet = new Packet<>(signature, PacketType.CALLBACK, channel, this.dataBridge.encodeData(data), callbackId, skipSelf);
                    this.messaging._publishPacket(channel, packet);
                });
            }
        };
    }

    public void register(String event, Listener listener) {
        var listeners = this.listeners.get(event);
        if (listeners == null) {
            listeners = new ConcurrentLinkedQueue<>();
            val tempListeners = this.listeners.putIfAbsent(event, listeners);
            if (tempListeners != null) {
                listeners = tempListeners;
            }
        }

        listeners.add(listener);
    }
}
