// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.serialization.MutableAccessor
import com.intellij.util.xml.dom.XmlElement
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Type

@JvmField
internal val LOG: Logger = logger<Binding>()

interface Serializer {
  fun getRootBinding(aClass: Class<*>, originalType: Type): Binding

  fun getRootBinding(aClass: Class<*>): Binding = getRootBinding(aClass = aClass, originalType = aClass)

  fun getBinding(accessor: MutableAccessor): Binding?

  fun getBinding(aClass: Class<*>, type: Type): Binding?
}

fun interface SerializationFilter {
  fun accepts(accessor: Accessor, bean: Any): Boolean
}

interface Binding {
  fun serialize(bean: Any, context: Any?, filter: SerializationFilter?): Any?

  fun isBoundTo(element: Element): Boolean

  fun isBoundTo(element: XmlElement): Boolean

  fun init(originalType: Type, serializer: Serializer) {
  }

  fun deserializeUnsafe(context: Any?, element: Element): Any?

  fun deserializeUnsafe(context: Any?, element: XmlElement): Any?
}

internal fun deserializeJdomList(binding: Binding, context: Any?, nodes: List<Element>): Any? {
  return when {
    binding is MultiNodeBinding -> binding.deserializeJdomList(context = context, elements = nodes)
    nodes.size == 1 -> binding.deserializeUnsafe(context, nodes.get(0))
    nodes.isEmpty() -> null
    else -> throw AssertionError("Duplicate data for $binding will be ignored")
  }
}

internal fun deserializeList(binding: Binding, context: Any?, nodes: List<XmlElement>): Any? {
  return when {
    binding is MultiNodeBinding -> binding.deserializeList(context = context, elements = nodes)
    nodes.size == 1 -> binding.deserializeUnsafe(context, nodes[0])
    nodes.isEmpty() -> null
    else -> throw AssertionError("Duplicate data for $binding will be ignored")
  }
}

@ApiStatus.Internal
interface MultiNodeBinding : Binding {
  val isMulti: Boolean

  fun deserializeJdomList(context: Any?, elements: List<Element>): Any?

  fun deserializeList(context: Any?, elements: List<XmlElement>): Any?
}

interface NotNullDeserializeBinding : Binding {
  fun deserialize(context: Any?, element: Element): Any

  fun deserialize(context: Any?, element: XmlElement): Any

  override fun deserializeUnsafe(context: Any?, element: Element): Any = deserialize(context = context, element = element)

  override fun deserializeUnsafe(context: Any?, element: XmlElement): Any = deserialize(context = context, element = element)
}