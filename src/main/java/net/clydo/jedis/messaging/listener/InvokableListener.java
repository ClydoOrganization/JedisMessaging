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

import com.google.gson.JsonElement;
import net.clydo.jedis.messaging.callback.SendCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InvokableListener implements Listener {
    private final Method method;
    private final Object instance;

    public InvokableListener(final Method method, final Object instance) {
        this.method = method;
        this.instance = instance;
    }

    @Override
    public void call(@NotNull String channel, @NotNull JsonElement json, @Nullable SendCallback sender) {
        try {
            this.method.invoke(instance, channel, json, sender);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke method " + method.getName(), e);
        }
    }
}
