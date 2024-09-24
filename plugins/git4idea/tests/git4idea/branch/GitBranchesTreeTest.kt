// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.FilteringSpeedSearch
import com.intellij.ui.tree.TreeTestUtil
import com.intellij.ui.treeStructure.Tree
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.repo.GitRemote
import git4idea.ui.branch.dashboard.BranchInfo
import git4idea.ui.branch.dashboard.BranchNodeDescriptor
import git4idea.ui.branch.dashboard.BranchTreeNode
import git4idea.ui.branch.dashboard.FilteringBranchesTreeBase
import junit.framework.TestCase.assertEquals

abstract class GitBranchesTreeTest: LightPlatformTestCase() {
  internal fun branchesTreeTest(groupByDirectories: Boolean = true, groupByRepos: Boolean = false, test: GitBranchesTreeTestContext.() -> Unit) =
    with(GitBranchesTreeTestContext(groupByDirectories, groupByRepos, project)) { test() }
}

internal class GitBranchesTreeTestContext(private val groupByDirectories: Boolean, private val groupByRepos: Boolean, private val project: Project) {
  val tree = Tree()
  val branchesTree = GitBranchesTestTree()
  val searchTextField = branchesTree.installSearchField()

  fun assertTree(expected: String) {
    assertEquals("Tree state doesn't match expected. Search field - '${searchTextField.text}'", expected.trim(), TreeTestUtil(tree).setSelection(true).toString().trim())
  }

  fun setState(localBranches: Collection<String>, remoteBranches: Collection<String>, expanded: Boolean = false) {
    val local = localBranches.map {
      BranchInfo(GitLocalBranch(it), isCurrent = false, isFavorite = false, repositories = emptyList())
    }
    val remote = remoteBranches.map {
      BranchInfo(GitStandardRemoteBranch(ORIGIN, it), isCurrent = false, isFavorite = false, repositories = emptyList())
    }
    setRawState(local, remote, expanded)
  }

  fun setRawState(localBranches: Collection<BranchInfo>, remoteBranches: Collection<BranchInfo>, expanded: Boolean = false) {
    branchesTree.refreshNodeDescriptorsModel(localBranches = localBranches, remoteBranches = remoteBranches, showOnlyMy = false)
    branchesTree.searchModel.updateStructure()
    if (expanded) {
      TreeTestUtil(tree).expandAll()
    }
  }

  fun selectBranch(branch: String) {
    val speedSearch = branchesTree.speedSearch
    speedSearch.iterate(null, true).forEach { node ->
      if (branchesTree.getText(node.getNodeDescriptor()) == branch) {
        speedSearch.select(node)
        return
      }
    }
    throw AssertionError("Node with text $branch not found")
  }

  internal inner class GitBranchesTestTree(): FilteringBranchesTreeBase(tree, project = project) {
    @Suppress("UNCHECKED_CAST")
    val speedSearch: FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>
      get() = searchModel.speedSearch as FilteringSpeedSearch<BranchTreeNode, BranchNodeDescriptor>

    override val groupingConfig: Map<GroupingKey, Boolean> = buildMap {
      this[GroupingKey.GROUPING_BY_REPOSITORY] = groupByRepos
      this[GroupingKey.GROUPING_BY_DIRECTORY] = groupByDirectories
    }
  }

  companion object {
    val ORIGIN_URLS = listOf("ssh://origin")
    val ORIGIN = GitRemote(GitRemote.ORIGIN, ORIGIN_URLS, ORIGIN_URLS, listOf(), listOf())
  }
}

