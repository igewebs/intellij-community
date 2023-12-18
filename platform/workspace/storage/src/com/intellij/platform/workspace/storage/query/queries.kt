// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.query

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.PersistentMultiOccurenceMap
import com.intellij.platform.workspace.storage.impl.query.*
import kotlinx.collections.immutable.toPersistentList
import kotlin.reflect.KClass

// Basic interface
public sealed interface StorageQuery<T>

/**
 * Queries with collections as a result of operations
 *
 * Should not be used directly, but via [entities], [map] and other functions.
 */
public sealed interface CollectionQuery<T> : StorageQuery<Collection<T>> {
  public class EachOfType<T : WorkspaceEntity>(public val type: KClass<T>) : CollectionQuery<T>
  public class FlatMapTo<T, K>(public val from: CollectionQuery<T>,
                               public val map: (T, EntityStorageSnapshot) -> Iterable<K>) : CollectionQuery<K>
}

/**
 * Queries with Maps as a result of operation
 *
 * Should not be used directly, but via [groupBy] function
 */
public sealed interface AssociationQuery<K, V> : StorageQuery<Map<K, V>> {
  public class GroupBy<T, K, V>(
    public val from: CollectionQuery<T>,
    public val keySelector: (T) -> K,
    public val valueTransformer: (T) -> V,
  ) : AssociationQuery<K, List<V>>
}

/**
 * Convert a [StorageQuery] to [CellChain] that can be executed to calculate the cache.
 */
internal fun <T> StorageQuery<T>.compile(cellCollector: MutableList<Cell<*>> = mutableListOf()): CellChain {
  when (this) {
    is CollectionQuery<*> -> {
      when (this) {
        is CollectionQuery.EachOfType<*> -> {
          cellCollector.prepend(EntityCell(CellId(), type))
        }
        is CollectionQuery.FlatMapTo<*, *> -> {
          cellCollector.prepend(FlatMapCell(CellId(), map, PersistentMultiOccurenceMap()))
          this.from.compile(cellCollector)
        }
      }
    }
    is AssociationQuery<*, *> -> {
      when (this) {
        is AssociationQuery.GroupBy<*, *, *> -> {
          cellCollector.prepend(GroupByCell(CellId(), keySelector, valueTransformer, PersistentMultiOccurenceMap()))
          this.from.compile(cellCollector)
        }
      }
    }
  }
  return CellChain(cellCollector.toPersistentList(), CellChainId())
}

private fun <T> MutableList<T>.prepend(data: T) {
  this.add(0, data)
}
