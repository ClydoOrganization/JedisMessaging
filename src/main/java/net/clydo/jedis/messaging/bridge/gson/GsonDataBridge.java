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

package net.clydo.jedis.messaging.bridge.gson;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.RequiredArgsConstructor;
import net.clydo.jedis.messaging.bridge.DataBridge;
import net.clydo.jedis.messaging.packet.Packet;

@RequiredArgsConstructor
public class GsonDataBridge implements DataBridge<JsonElement> {

    private static final GsonPacketTypeToken PACKET_TYPE_TOKEN = new GsonPacketTypeToken();

    private final Gson gson;

    @Override
    public <T> T dataAs(JsonElement data, Class<T> as) {
        return this.gson.fromJson(data, as);
    }

    @Override
    public JsonElement encodeData(Object src) {
        return this.gson.toJsonTree(src);
    }

    @Override
    public String encodePacket(Packet<JsonElement> src) {
        return this.gson.toJson(src);
    }

    @Override
    public Packet<JsonElement> decodePacket(String data) {
        return this.gson.fromJson(data, PACKET_TYPE_TOKEN);
    }

}
