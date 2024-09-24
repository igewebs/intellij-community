package com.intellij.searchEverywhereMl.ranking.core.model

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking

class SearchEverywhereModelProvider {
  private val cache: Cache<String, SearchEverywhereRankingModel> = Caffeine.newBuilder().maximumSize(modelCount.toLong()).build()
  private val modelCount: Int
    get() = SearchEverywhereMLRankingModelLoader.allLoaders.size

  internal fun getModel(tabId: String): SearchEverywhereRankingModel {
    return cache.get(tabId) {
      if (isTabWithExactMatchIssue(tabId))
        return@get getRankingModelForExactMatchIssue(tabId)
      else
        return@get getRankingModel(tabId)
    }
  }

  /**
   * We have tabs that suffer from exact match issue, where the exactly matched result does not appear at the top,
   * we have introduced additional logic to address that issue.
   */
  private fun isTabWithExactMatchIssue(tabId: String): Boolean {
    val tab = SearchEverywhereTabWithMlRanking.findById(tabId)
    return tab == SearchEverywhereTabWithMlRanking.CLASSES || tab == SearchEverywhereTabWithMlRanking.FILES
  }

  private fun getRankingModelForExactMatchIssue(tabId: String): SearchEverywhereRankingModel {
    val loader = SearchEverywhereMLRankingModelLoader.getForTab(tabId)
    return ExactMatchSearchEverywhereRankingModel(loader.loadModel())
  }

  private fun getRankingModel(tabId: String): SearchEverywhereRankingModel {
    val loader = SearchEverywhereMLRankingModelLoader.getForTab(tabId)
    return SimpleSearchEverywhereRankingModel(loader.loadModel())
  }
}