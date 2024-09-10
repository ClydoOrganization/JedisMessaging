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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public final class PacketData {
    private final JsonElement data;
    private final Gson gson;

    public JsonElement raw() {
        return this.data;
    }

    @SuppressWarnings("unchecked")
    public <T> T as(final @NotNull Class<T> clazz) {
        if (PacketData.class.equals(clazz)) {
            return (T) this;
        }
        return this.gson.fromJson(this.data, clazz);
    }

    public <T extends JsonElement> T cast(final @NotNull Class<T> clazz) {
        return clazz.cast(this.data);
    }
}
