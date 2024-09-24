// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.psi.*
import com.intellij.testFramework.*
import org.intellij.lang.annotations.Language
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@CanChangeDocumentDuringHighlighting
class LocalInspectionsInDumbModeTest : DaemonAnalyzerTestCase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    DaemonProgressIndicator.setDebug(true)
  }

  fun testLocalInspectionInDumbMode() {
    enableInspectionTools(project, testRootDisposable, DumbInspection(), SmartInspection())

    @Language("JAVA")
    val text = """
      // comment
    """
    configureByText(JavaFileType.INSTANCE, text)

    // only dumb inspection runs in dumb mode
    val dumbInfos = doHighlightingInDumbMode()
    assertOneElement(dumbInfos)
    assertExistsInfo(dumbInfos, "Dumb0")

    DaemonCodeAnalyzer.getInstance(project).restart()
    // dumb and smart inspections run in dumb mode
    val smartInfos = doHighlighting()
    assertSize(2, smartInfos)
    assertExistsInfo(smartInfos, "Dumb1")
    assertExistsInfo(smartInfos, "Smart0")

    DaemonCodeAnalyzer.getInstance(project).restart()
    // only dumb inspection runs in dumb mode, but the results of smart inspection are frozen from the previous run
    val dumbInfos2 = doHighlightingInDumbMode()
    assertSize(2, dumbInfos2)
    assertExistsInfo(dumbInfos2, "Dumb2")
    assertExistsInfo(dumbInfos2, "Smart0")
  }

  fun testLocalInspectionsInSmartModeThenInDumbMode() {
    enableInspectionTools(project, testRootDisposable, DumbInspection(), SmartInspection())

    @Language("JAVA")
    val text = """
      // comment
    """
    configureByText(JavaFileType.INSTANCE, text)

    // dumb and smart inspections run in dumb mode
    val smartInfos = doHighlighting()
    assertSize(2, smartInfos)
    assertExistsInfo(smartInfos, "Dumb0")
    assertExistsInfo(smartInfos, "Smart0")

    DaemonCodeAnalyzer.getInstance(project).restart()
    // only dumb inspection runs in dumb mode, but the results of smart inspection are frozen from the previous run
    val dumbInfos = doHighlightingInDumbMode()
    assertSize(2, dumbInfos)
    assertExistsInfo(dumbInfos, "Dumb1")
    assertExistsInfo(dumbInfos, "Smart0")
  }

  fun testLocalInspectionInDumbModeDontInitializeUnrelatedTools() {
    enableInspectionTools(project, testRootDisposable, DumbInspection(), SmartInspection())

    val unrelatedToolWrapper = createUnrelatedToolWrapper()
    enableInspectionTool(project, unrelatedToolWrapper, testRootDisposable)
    LocalInspectionsPass.forceNoDuplicateCheckInTests(testRootDisposable)

    @Language("JAVA")
    val text = """
      // comment
    """
    configureByText(JavaFileType.INSTANCE, text)

    doHighlightingInDumbMode()

    assertFalse(unrelatedToolWrapper.isToolInstantiated())
  }

  fun testLocalInspectionDontInitializeUnrelatedTools() {
    enableInspectionTools(project, testRootDisposable, DumbInspection(), SmartInspection())

    val unrelatedToolWrapper = createUnrelatedToolWrapper()
    enableInspectionTool(project, unrelatedToolWrapper, testRootDisposable)
    LocalInspectionsPass.forceNoDuplicateCheckInTests(testRootDisposable)

    @Language("JAVA")
    val text = """
      // comment
    """
    configureByText(JavaFileType.INSTANCE, text)

    doHighlighting()

    assertFalse(unrelatedToolWrapper.isToolInstantiated())
  }

  fun testJavaSuppressor() {
    enableInspectionTools(project, testRootDisposable, RedundantSuppressInspection(), StringInspection())

    val javaRedundantSuppressor = LanguageInspectionSuppressors.INSTANCE.allForLanguage(JavaLanguage.INSTANCE)
      .filterIsInstance<RedundantSuppressionDetector>()
      .firstOrNull() // Java Redundant Suppressor is expected to exist
    requireNotNull(javaRedundantSuppressor) // Java Redundant Suppressor is expected to exist
    assertFalse(javaRedundantSuppressor.isDumbAware) // update the test if JavaRedundantSuppressor has become dumb-aware

    @Language("JAVA")
    val text = """
      class A {
        void foo() {
          //noinspection String
          Object s = "abc";
        }
      }
    """
    configureByText(JavaFileType.INSTANCE, text)

    // smart infos don't contain String because Suppressor works in smart mode and suppresses it
    val smartInfos = doHighlighting().map { it.description }
    assertDoesntContain(smartInfos, "String")

    // dumb infos contain String because Suppressor does not work in dumb mode
    val dumbInfos = doHighlightingInDumbMode().map { it.description }
    assertContainsElements(dumbInfos, "String")
  }

  fun testRedundantJavaSuppression() {
    enableInspectionTools(project, testRootDisposable, RedundantSuppressInspection(), StringInspection())

    val javaRedundantSuppressor = LanguageInspectionSuppressors.INSTANCE.allForLanguage(JavaLanguage.INSTANCE)
      .filterIsInstance<RedundantSuppressionDetector>()
      .firstOrNull()
    assertNotNull(javaRedundantSuppressor)// Java Redundant Suppressor is expected to exist

    @Language("JAVA")
    val text = """
      class A {
        void foo() {
          //noinspection String
          Object s;
        }
      }
    """
    configureByText(JavaFileType.INSTANCE, text)

    // dumb infos contain a redundant suppression because it's not removed as java suppressor does not work in dumb mode
    val initialDumbInfos = doHighlightingInDumbMode().map { it.description }
    assertDoesntContain(initialDumbInfos, "Redundant suppression")

    // smart infos contain a redundant suppression, because suppression is in fact redundant,
    // and redundant suppressor for Java works in smart mode
    val smartInfos = doHighlighting().map { it.description }
    assertContainsElements(smartInfos, "Redundant suppression")

    // dumb infos contain a redundant suppression because it's not removed as java suppressor does not work in dumb mode
    val dumbInfos = doHighlightingInDumbMode().map { it.description }
    assertContainsElements(dumbInfos, "Redundant suppression")
  }


  fun testSuppressorInDumbMode2() {
    enableInspectionTools(project, testRootDisposable, RedundantSuppressInspection(), StringInspection())

    @Language("JAVA")
    val text = """
      class A {
        void foo() {
          Object s = "abc";
        }
      }
    """
    configureByText(JavaFileType.INSTANCE, text)

    val dumbInfos = doHighlightingInDumbMode()
    assertDoesntContain(dumbInfos, "String")
  }

  private fun assertExistsInfo(infos: List<HighlightInfo>, text: String) {
    assert(infos.any { it.description == text }) {
      "List [${infos.joinToString { it.description }}] does not contain `$text`"
    }
  }

  private fun doHighlightingInDumbMode(): List<HighlightInfo> {
    var result: MutableList<HighlightInfo>? = null
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      Disposer.newDisposable(testRootDisposable).use { disposable ->
        (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).mustWaitForSmartMode(false, disposable)
        result = doHighlighting()
      }
    }
    return result!!
  }

  private class DumbInspection : LocalInspectionTool(), DumbAware {
    @Volatile
    var counter = 0

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : PsiElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          holder.registerProblem(comment, "Dumb$counter")
          counter++
        }
      }
    }
  }

  private class SmartInspection : LocalInspectionTool() {
    @Volatile
    var counter = 0

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : PsiElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          holder.registerProblem(comment, "Smart$counter")
          counter++
        }
      }
    }
  }

  private class StringInspection : LocalInspectionTool(), DumbAware {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : JavaElementVisitor() {
        override fun visitLiteralExpression(expression: PsiLiteralExpression) {
          holder.registerProblem(expression, "String")
        }
      }
    }
  }


  private fun createUnrelatedToolWrapper(): UnrelatedToolWrapper {
    val ep = LocalInspectionEP()
    ep.dumbAware = true
    ep.implementationClass = "foo.bar.Baz"
    ep.id = "Baz"
    ep.language = "TEXT"
    ep.displayName = "Baz"
    return UnrelatedToolWrapper(ep)
  }

  private class UnrelatedToolWrapper(ep: LocalInspectionEP) : LocalInspectionToolWrapper(ep) {
    private val toolIsInstantiated = AtomicBoolean(false)

    override fun getTool(): LocalInspectionTool {
      toolIsInstantiated.set(true)
      return MyLocalInspection()
    }

    fun isToolInstantiated(): Boolean {
      return toolIsInstantiated.get()
    }

    private class MyLocalInspection : LocalInspectionTool() {
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
      }
    }
  }
}