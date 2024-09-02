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

package net.clydo.jedis.messaging.util;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

@UtilityClass
public class ReflectionUtil {

    public <T extends Annotation> T validateAnnotation(@NotNull AnnotatedElement element, Class<T> annotationClass) {
        return validateAnnotation(element, annotationClass, true);
    }

    public <T extends Annotation> T validateAnnotation(@NotNull AnnotatedElement element, Class<T> annotationClass, boolean inRoot) {
        val annotation = ReflectionUtil.getAnnotation(element, annotationClass, inRoot);
        if (annotation == null) {
            throw new IllegalStateException(element + " is not annotated with @" + annotationClass.getSimpleName());
        }
        return annotation;
    }

    public <T extends Annotation> T getAnnotation(@NotNull AnnotatedElement element, Class<T> annotationClass, boolean inRoot) {
        var annotation = element.getAnnotation(annotationClass);
        if (annotation != null || inRoot) {
            return annotation;
        }

        val declaredAnnotations = element.getDeclaredAnnotations();
        for (Annotation declaredAnnotation : declaredAnnotations) {
            annotation = declaredAnnotation.annotationType().getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }
}
