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

import com.google.gson.Gson;
import lombok.val;
import net.clydo.jedis.messaging.JedisMessaging;
import net.clydo.jedis.messaging.packet.Packet;
import net.clydo.jedis.messaging.packet.PacketType;
import redis.clients.jedis.JedisPubSub;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class ListenerHandler extends JedisPubSub {
    private final Gson gson;
    private final ConcurrentMap<String, ConcurrentLinkedQueue<Listener>> listeners;
    private final JedisMessaging messaging;

    public ListenerHandler(JedisMessaging messaging, Gson gson) {
        this.listeners = new ConcurrentHashMap<>();
        this.messaging = messaging;
        this.gson = gson;
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

        if (packetType == PacketType.EVENT) {
            val packetEvent = packet.event();

            val listeners = this.listeners.get(packetEvent);
            if (listeners != null) {
                val iterator = listeners.iterator();

                //noinspection WhileLoopReplaceableByForEach
                while (iterator.hasNext()) {
                    val listener = iterator.next();
                    listener.call(channel, packetData, (callbackId != null ? this.messaging.callback(channel, callbackId, this.messaging.getSignature(), signature != null) : null));
                }
            }
        }
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
