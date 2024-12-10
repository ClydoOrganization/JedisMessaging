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

import lombok.RequiredArgsConstructor;
import net.clydo.jedis.messaging.bridge.DataBridge;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public final class PacketData<D> {
    private final D data;
    private final DataBridge<D> dataBridge;

    public D raw() {
        return this.data;
    }

    @SuppressWarnings("unchecked")
    public <T> T as(final @NotNull Class<T> clazz) {
        if (PacketData.class.equals(clazz)) {
            return (T) this;
        }
        return this.dataBridge.dataAs(this.data, clazz);
    }

    public <T> T cast(final @NotNull Class<T> clazz) {
        return clazz.cast(this.data);
    }
}
