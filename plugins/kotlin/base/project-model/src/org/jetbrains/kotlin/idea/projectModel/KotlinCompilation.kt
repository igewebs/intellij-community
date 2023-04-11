// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import org.jetbrains.kotlin.tooling.core.HasMutableExtras

interface KotlinCompilation : KotlinComponent, HasMutableExtras {

    @Deprecated("This property is removed in master, but still used in the KotlinAndroidMPPGradleModuleDataService", level = DeprecationLevel.ERROR)
    val sourceSets: Collection<KotlinSourceSet>
        get() = declaredSourceSets
    /**
     * All source sets participated in this compilation, including those available
     * via dependsOn.
     */
    val allSourceSets: Set<KotlinSourceSet>

    @Deprecated(message = "For binary compatibility", level = DeprecationLevel.HIDDEN)
    @Suppress("unused")
    fun getAllSourceSets(): Collection<KotlinSourceSet> = allSourceSets

    /**
     * Only directly declared source sets of this compilation, i.e. those which are included
     * into compilations directly.
     *
     * Usually, those are automatically created source sets for automatically created
     * compilations (like jvmMain for JVM compilations) or manually included source sets
     * (like 'jvm().compilations["main"].source(mySourceSet)' )
     */
    val declaredSourceSets: Set<KotlinSourceSet>

    @Deprecated(message = "For binary compatibility", level = DeprecationLevel.HIDDEN)
    @Suppress("unused")
    fun getDeclaredSourceSets(): Collection<KotlinSourceSet> = declaredSourceSets

    val associateCompilations: Set<KotlinCompilationCoordinates>

    val output: KotlinCompilationOutput

    @Deprecated(
        "Raw dependency classpath is not available anymore",
        ReplaceWith("cachedArgsInfo#dependencyClasspath"),
        level = DeprecationLevel.ERROR
    )
    val dependencyClasspath: Array<String>

    val cachedArgsInfo: CachedArgsInfo<*>?

    val compilerArguments: List<String>?

    val disambiguationClassifier: String?
    val platform: KotlinPlatform
    val kotlinTaskProperties: KotlinTaskProperties
    val nativeExtensions: KotlinNativeCompilationExtensions?

    companion object {
        const val MAIN_COMPILATION_NAME = "main"
        const val TEST_COMPILATION_NAME = "test"
    }
}
