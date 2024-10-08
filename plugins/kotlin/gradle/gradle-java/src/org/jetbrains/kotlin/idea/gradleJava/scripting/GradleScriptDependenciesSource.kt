// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEPENDENCIES_SOURCES
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.getUpdatedStorage
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm


class GradleScriptModel(
    override val virtualFile: VirtualFile,
    val classPath: List<String> = listOf(),
    val sourcePath: List<String> = listOf(),
    val imports: List<String> = listOf(),
    val javaHome: String? = null
) : BaseScriptModel(virtualFile)

open class GradleScriptDependenciesSource(override val project: Project) : ScriptDependenciesSource<GradleScriptModel>(project) {
    private val gradleEntitySourceFilter: (EntitySource) -> Boolean =
        { entitySource -> entitySource is KotlinGradleScriptModuleEntitySource }

    override suspend fun updateModules(dependencies: ScriptDependenciesData, storage: MutableEntityStorage?) {
        val storageWithGradleScriptModules = getUpdatedStorage(
            project, dependencies
        ) { KotlinGradleScriptModuleEntitySource(it) }

        if (storage != null) {
            storage.replaceBySource(gradleEntitySourceFilter, storageWithGradleScriptModules)
        } else {
            val workspaceModel = project.workspaceModel
            workspaceModel.update("Updating Gradle Kotlin Scripts modules") {
                it.replaceBySource(gradleEntitySourceFilter, storageWithGradleScriptModules)
            }
        }
    }

    override fun resolveDependencies(scripts: Iterable<GradleScriptModel>): ScriptDependenciesData {
        val newClasses = mutableSetOf<VirtualFile>()
        val newSources = mutableSetOf<VirtualFile>()
        val sdks = mutableMapOf<Path, Sdk>()

        val newConfigurations = mutableMapOf<VirtualFile, ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>>()

        for (script in scripts) {

            val sourceCode = VirtualFileScriptSource(script.virtualFile)
            val definition = findScriptDefinition(project, sourceCode)

            val javaProjectSdk = ProjectRootManager.getInstance(project).projectSdk?.takeIf { it.sdkType is JavaSdkType }

            val javaHomePath = (javaProjectSdk?.homePath ?: script.javaHome)?.let { Path.of(it) }

            val configuration = definition.compilationConfiguration.with {
                javaHomePath?.let {
                    jvm.jdkHome(it.toFile())
                }
                defaultImports(script.imports)
                dependencies(JvmDependency(script.classPath.map { File(it) }))
                ide.dependenciesSources(JvmDependency(script.sourcePath.map { File(it) }))
            }.adjustByDefinition(definition)

            val updatedConfiguration = project.runReadActionInSmartMode {
                refineScriptCompilationConfiguration(sourceCode, definition, project, configuration)
            }
            newConfigurations[script.virtualFile] = updatedConfiguration

            val configurationWrapper = updatedConfiguration.valueOrNull() ?: continue

            newClasses.addAll(toVfsRoots(configurationWrapper.dependenciesClassPath))
            newSources.addAll(toVfsRoots(configurationWrapper.dependenciesSources))

            if (javaProjectSdk != null) {
                javaProjectSdk.homePath?.let { path ->
                    sdks.computeIfAbsent(Path.of(path)) { javaProjectSdk }
                }
            } else if (javaHomePath != null) {
                sdks.computeIfAbsent(javaHomePath) {
                    ExternalSystemJdkUtil.lookupJdkByPath(it.pathString)
                }
            }
        }

        return ScriptDependenciesData(
            newConfigurations, newClasses, newSources, sdks
        )
    }

    companion object {
        fun getInstance(project: Project): GradleScriptDependenciesSource? =
            SCRIPT_DEPENDENCIES_SOURCES.getExtensions(project).filterIsInstance<GradleScriptDependenciesSource>().firstOrNull()
                .safeAs<GradleScriptDependenciesSource>()
    }

    data class KotlinGradleScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)
}
