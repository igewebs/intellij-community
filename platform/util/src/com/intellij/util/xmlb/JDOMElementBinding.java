// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class JDOMElementBinding implements MultiNodeBinding, NestedBinding, NotNullDeserializeBinding {
  private final String tagName;
  private final MutableAccessor accessor;

  JDOMElementBinding(@NotNull MutableAccessor accessor) {
    this.accessor = accessor;

    Tag tag = this.accessor.getAnnotation(Tag.class);
    String tagName = tag == null ? null : tag.value();
    this.tagName = tagName == null || tagName.isEmpty() ? this.accessor.getName() : tagName;
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public Object serialize(@NotNull Object bean, @Nullable Object context, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value == null) {
      return null;
    }

    if (value instanceof Element) {
      Element targetElement = ((Element)value).clone();
      assert targetElement != null;
      targetElement.setName(tagName);
      return targetElement;
    }
    if (value instanceof Element[]) {
      ArrayList<Element> result = new ArrayList<>();
      for (Element element : ((Element[])value)) {
        result.add(element.clone().setName(tagName));
      }
      return result;
    }
    throw new XmlSerializationException("org.jdom.Element expected but " + value + " found");
  }

  @Override
  public @NotNull Object deserializeJdomList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<? extends Element> elements) {
    if (accessor.getValueClass().isArray()) {
      accessor.set(context, elements.toArray(new Element[0]));
    }
    else {
      accessor.set(context, elements.get(0));
    }
    return context;
  }

  @Override
  public @NotNull Object deserializeList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<XmlElement> elements) {
    throw new UnsupportedOperationException("XmlElement is not supported by JDOMElementBinding");
  }

  @Override
  public boolean isMulti() {
    return true;
  }

  @Override
  public @NotNull Object deserialize(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull Element element) {
    accessor.set(context, element);
    return context;
  }

  @Override
  public @NotNull Object deserialize(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull XmlElement element) {
    throw new UnsupportedOperationException("XmlElement is not supported by JDOMElementBinding");
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return element.getName().equals(tagName);
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    return element.name.equals(tagName);
  }
}
