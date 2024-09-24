// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.prevLeafs
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.Replacement
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.expressionContainsOperationForbiddenToReplace
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.kotlinVersionIsEqualOrHigher
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.getReplacementForOldKotlinOptionIfNeeded
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.AbstractKotlinGradleScriptInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.resolveExpression
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val kotlinCompileTasksNames = setOf(
    "org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile",
    "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
    "org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile",
    "org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile"
)

internal class KotlinOptionsToCompilerOptionsInGradleScriptInspection : AbstractKotlinGradleScriptInspection() {

    override fun isAvailableForFile(file: PsiFile): Boolean {
        if (super.isAvailableForFile(file)) {
            if (isUnitTestMode()) {
                // Inspection tests don't treat tested build script files properly, and thus they ignore Kotlin versions used in scripts
                return true
            } else {
                return kotlinVersionIsEqualOrHigher(major = 2, minor = 0, patch = 0, file)
            }
        } else {
            return false
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) = object : KtVisitorVoid() {

        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            if (expression.text.equals("kotlinOptions")) {

                if (isDescendantOfDslInWhichReplacementIsNotNeeded(expression)) return

                val expressionParent = expression.parent

                if (!isUnitTestMode()) { // ATM, we don't have proper dependencies for tests on Gradle build scripts
                    analyze(expression) {
                        val jvmClassForKotlinCompileTask = (expression.resolveToCall()
                            ?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.signature?.symbol
                            ?.containingDeclaration as? KaClassLikeSymbol)?.importableFqName?.toString()

                            ?: expression.resolveExpression()?.containingSymbol?.importableFqName?.toString() ?: return
                        if (!kotlinCompileTasksNames.contains(jvmClassForKotlinCompileTask)) {
                            return
                        }
                    }
                }
                when (expressionParent) {
                    is KtDotQualifiedExpression -> { // like `kotlinOptions.sourceMapEmbedSources` OR kotlinOptions.options
                        val parentOfExpressionParent = expressionParent.parent
                        if (elementContainsOperationForbiddenToReplaceOrCantBeProcessed(parentOfExpressionParent)) return
                    }

                    is KtCallExpression -> {
                        /*
                        Like the following. Raise a problem for this.
                        compileKotlin.kotlinOptions {
                            jvmTarget = "1.8"
                            freeCompilerArgs += listOf("-module-name", "TheName")
                            apiVersion = "1.9"
                        }
                         */
                        val lambdaStatements = expressionParent.lambdaArguments.getOrNull(0)
                            ?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()

                        if (lambdaStatements?.isNotEmpty() == true) { // compileKotlin.kotlinOptions { .. }
                            lambdaStatements.forEach {
                                if (binaryExpressionsContainForbiddenOperations(it)) return
                            }
                        }
                    }

                    else -> return
                }

                holder.problem(
                    expression,
                    KotlinBundle.message("inspection.kotlin.options.to.compiler.options.display.name")
                )
                    .highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    .fix(
                        ReplaceKotlinOptionsWithCompilerOptionsFix()
                    ).register()
            }
        }
    }

    private fun elementContainsOperationForbiddenToReplaceOrCantBeProcessed(psiElement: PsiElement): Boolean {
        when (psiElement) {
            is KtBinaryExpression -> {
                return expressionContainsOperationForbiddenToReplace(psiElement)
            }

            is KtDotQualifiedExpression -> {
                val psiElementParent = psiElement.parent
                if (psiElementParent is KtBinaryExpression) {
                    return expressionContainsOperationForbiddenToReplace(psiElementParent)
                } else { // Can't be processed
                    return true
                }
            }

            else -> return true
        }
    }

    private fun isDescendantOfDslInWhichReplacementIsNotNeeded(ktExpression: KtExpression): Boolean {
        val scriptText = ktExpression.containingFile.text
        if (scriptText.contains("android")) {
            ktExpression.prevLeafs.forEach {
                if ("android" == it.text) {
                    return true
                }
            }
        }
        return false
    }

    private fun binaryExpressionsContainForbiddenOperations(element: PsiElement): Boolean {
        if (element is KtBinaryExpression) { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
            if (expressionContainsOperationForbiddenToReplace(element)) return true
        } else {
            element.children.forEach {
                if (binaryExpressionsContainForbiddenOperations(it)) return true
            }
        }
        return false
    }
}

private class ReplaceKotlinOptionsWithCompilerOptionsFix() : KotlinModCommandQuickFix<KtExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("replace.kotlin.options.with.compiler.options")
    }

    override fun applyFix(
        project: Project,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {

        val expressionsToFix = mutableListOf<Replacement>()
        val expressionParent = element.parent
        when (expressionParent) {
            is KtDotQualifiedExpression -> { // for sth like `kotlinOptions.sourceMapEmbedSources` || `kotlinOptions.options.jvmTarget`
                val parentOfExpressionParent = expressionParent.parent
                when (parentOfExpressionParent) {
                    is KtBinaryExpression -> { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
                        getReplacementForOldKotlinOptionIfNeeded(parentOfExpressionParent)?.let { expressionsToFix.add(it) }
                    }

                    is KtDotQualifiedExpression -> {
                        val parent = parentOfExpressionParent.parent
                        if (parent is KtBinaryExpression) { // like `kotlinOptions.options.jvmTarget = JvmTarget.JVM_11`
                            getReplacementForOldKotlinOptionIfNeeded(parent)?.let { expressionsToFix.add(it) }
                        }
                    }
                }
            }

            is KtCallExpression -> {
                /* Example:
                compileKotlin.kotlinOptions {
                    jvmTarget = "1.8"
                    freeCompilerArgs += listOf("-module-name", "TheName")
                    apiVersion = "1.9"
                }

                OR
                tasks.withType<KotlinCompile> {
                    kotlinOptions {
                        freeCompilerArgs += listOf("-module-name", "TheName")
                    }
                }
                */

                expressionsToFix.add(Replacement(element, "compilerOptions"))

                val lambdaStatements = expressionParent.lambdaArguments.getOrNull(0)
                    ?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()

                /**
                 * Test case:
                 * K2LocalInspectionTestGenerated.InspectionsLocal.KotlinOptionsToCompilerOptions#testLambdaWithSeveralStatements_gradle())
                 */
                if (lambdaStatements?.isNotEmpty() == true) { // compileKotlin.kotlinOptions { .. }
                    lambdaStatements.forEach {
                        searchAndProcessBinaryExpressionChildren(it, expressionsToFix)
                    }
                }
            }
        }

        expressionsToFix.forEach {
            val newExpression = KtPsiFactory(project).createExpression(it.replacement)

            val replacedElement = it.expressionToReplace.replaced(newExpression)

            val classToImport = it.classToImport
            if (classToImport != null) {
                (replacedElement.containingFile as? KtFile)?.addImport(classToImport)
            }
        }
    }

    /**
     * Test case:
     * K2LocalInspectionTestGenerated.InspectionsLocal.KotlinOptionsToCompilerOptions#testDontMergeConvertedOptionsToAnotherCompilerOptions_gradle
     */
    private fun searchAndProcessBinaryExpressionChildren(element: PsiElement, expressionsToFix: MutableList<Replacement>) {
        if (element is KtBinaryExpression) { // for sth like `kotlinOptions.sourceMapEmbedSources = "inlining"`
            getReplacementForOldKotlinOptionIfNeeded(element)?.let { expressionsToFix.add(it) }
        } else {
            element.children.forEach {
                searchAndProcessBinaryExpressionChildren(it, expressionsToFix)
            }
        }
    }
}

