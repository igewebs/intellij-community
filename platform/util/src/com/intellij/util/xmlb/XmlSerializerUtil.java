// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class XmlSerializerUtil {
  private XmlSerializerUtil() {
  }

  public static <T> void copyBean(@NotNull T from, @NotNull T to) {
    assert from.getClass().isAssignableFrom(to.getClass()) : "Beans of different classes specified: Cannot assign " +
                                                             from.getClass() + " to " + to.getClass();
    for (MutableAccessor accessor : BeanBindingKt.getBeanAccessors(from.getClass())) {
      accessor.set(to, accessor.read(from));
    }
  }

  public static <T> T createCopy(@NotNull T from) {
    try {
      @SuppressWarnings("unchecked")
      T to = (T)ReflectionUtil.newInstance(from.getClass());
      copyBean(from, to);
      return to;
    }
    catch (Exception ignored) {
      return null;
    }
  }

  public static @NotNull List<MutableAccessor> getAccessors(@NotNull Class<?> aClass) {
    return BeanBindingKt.getBeanAccessors(aClass);
  }

  static @Nullable SerializationFilter getPropertyFilter(@NotNull Property property) {
    Class<? extends SerializationFilter> filter = property.filter();
    return filter == SerializationFilter.class ? null : ReflectionUtil.newInstance(filter);
  }
}
