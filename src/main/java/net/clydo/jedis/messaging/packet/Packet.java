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

package net.clydo.jedis.messaging.packet;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

public record Packet(
        String signature,
        int type,
        String event,
        JsonElement data,
        String callbackId,
        boolean skipSelf
) {
    public static final PacketJsonTypeToken PACKET_JSON_TYPE_TOKEN = new PacketJsonTypeToken();

    public Packet(final String signature, final @NotNull PacketType type, final String event, final JsonElement data, final String callbackId, final boolean skipSelf) {
        this(signature, type.getId(), event, data, callbackId, skipSelf);
    }
}
