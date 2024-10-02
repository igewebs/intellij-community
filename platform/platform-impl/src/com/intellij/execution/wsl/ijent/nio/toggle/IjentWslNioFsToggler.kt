// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.eel.EelApiWithPathsMapping
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.EelProvider
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.BufferedReader
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isSameFileAs

/**
 * This service, along with listeners inside it, enables and disables access to WSL drives through IJent.
 */
@Internal
@Service
@VisibleForTesting
class IjentWslNioFsToggler(private val coroutineScope: CoroutineScope) {
  companion object {
    suspend fun instanceAsync(): IjentWslNioFsToggler = serviceAsync()
    fun instance(): IjentWslNioFsToggler = service()
  }

  init {
    if (!SystemInfo.isWindows) {
      thisLogger().error("${javaClass.name} should be requested only on Windows")
    }
  }

  val isAvailable: Boolean get() = strategy != null

  suspend fun enableForAllWslDistributions() {
    strategy?.enableForAllWslDistributions()
  }

  @TestOnly
  suspend fun switchToIjentFs(distro: WSLDistribution) {
    strategy ?: error("Not available")
    strategy.switchToIjentFs(distro)
  }

  @TestOnly
  fun switchToTracingWsl9pFs(distro: WSLDistribution) {
    strategy ?: error("Not available")
    strategy.switchToTracingWsl9pFs(distro)
  }

  @TestOnly
  fun unregisterAll() {
    strategy ?: error("Not available")
    strategy.unregisterAll()
  }

  // TODO Move to ijent.impl?
  internal class WslEelProvider : EelProvider {
    override suspend fun getEelApi(path: Path): EelApi? {
      val enabledDistros = serviceAsync<IjentWslNioFsToggler>().strategy?.enabledInDistros

      return enabledDistros?.firstOrNull { distro -> distro.getUNCRootPath().isSameFileAs(path.root) }?.let { distro ->
        /**
         * NOTE: In [IjentWslNioFsToggleStrategy], the [com.intellij.execution.ijent.nio.IjentEphemeralRootAwareFileSystemProvider] is not currently
         * used because [com.intellij.execution.wsl.ijent.nio.IjentWslNioFileSystem] has its own logic for handling WSL roots (prefixes).
         * Therefore, in this case, [com.intellij.execution.eel.EelEphemeralRootAwareMapper.getOriginalPath] will return null.
         */
        EelApiWithPathsMapping(
          ephemeralRoot = path.root,
          original = WslIjentManager.getInstance().getIjentApi(distro, null, rootUser = false)
        )
      }
    }
  }

  private val strategy = run {
    val defaultProvider = FileSystems.getDefault().provider()
    when {
      !WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem() -> null

      defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name -> {
        IjentWslNioFsToggleStrategy(defaultProvider, coroutineScope)
      }

      else -> {
        val vmOptions = runCatching {
          VMOptions.getUserOptionsFile()?.bufferedReader()?.use<BufferedReader, String> { it.readText() }
          ?: "<null>"
        }.getOrElse<String, String> { err -> err.stackTraceToString() }

        val systemProperties = runCatching {
          System.getProperties().entries.joinToString("\n") { (k, v) -> "$k=$v" }
        }.getOrElse<String, String> { err -> err.stackTraceToString() }

        val message = "The default filesystem ${FileSystems.getDefault()} is not ${MultiRoutingFileSystemProvider::class.java}"

        if (ApplicationManager.getApplication().isUnitTestMode) {
          logger<IjentWslNioFsToggler>().warn("$message\nVM Options:\n$vmOptions\nSystem properties:\n$systemProperties")
        }
        else {
          logger<IjentWslNioFsToggler>().error(
            message,
            Attachment("user vmOptions.txt", vmOptions),
            Attachment("system properties.txt", systemProperties),
          )
        }
        null
      }
    }
  }
}