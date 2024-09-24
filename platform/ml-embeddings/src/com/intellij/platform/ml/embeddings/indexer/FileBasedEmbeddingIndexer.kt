// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readActionUndispatched
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.platform.ml.embeddings.files.SemanticSearchFileChangeListener
import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableFile
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettings
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
import com.intellij.platform.ml.embeddings.utils.SemanticSearchCoroutineScope
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiManager
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.APP)
class FileBasedEmbeddingIndexer(private val cs: CoroutineScope) : Disposable {
  private val indexingScope = cs.childScope("Embedding indexing scope")
  private val isFileListenerAdded = AtomicBoolean(false)

  private val indexedProjects = mutableSetOf<Project>()
  private val indexingJobs = mutableMapOf<Project, Job>()
  private val jobsMutex = Mutex()

  private val storageManagerWrapper = EmbeddingsConfiguration.getConfiguration().toStorageManagerWrapper()

  private val filesLimit: Int?
    get() {
      return if (Registry.`is`("intellij.platform.ml.embeddings.index.files.use.limit")) {
        Registry.intValue("intellij.platform.ml.embeddings.index.files.limit")
      }
      else null
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val indexingContext = Dispatchers.Default.limitedParallelism(TOTAL_THREAD_LIMIT)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val filesIterationContext = Dispatchers.Default.limitedParallelism(FILE_WORKER_COUNT)

  fun prepareForSearch(project: Project): Job = cs.launch {
    if (isFileListenerAdded.compareAndSet(false, true)) addFileListener()
    Disposer.register(project) {
      runBlockingMaybeCancellable {
        jobsMutex.withLock {
          indexingJobs.remove(project)
          indexedProjects.remove(project)
        }
      }
    }
    val currentJob = jobsMutex.withLock {
      // Cancel previous indexing for this project
      indexingJobs[project]?.cancel()
      // Create a new indexing job for this project
      val job = SemanticSearchCoroutineScope.getScope(project).launch { indexProject(project) }
      indexingJobs[project] = job
      indexedProjects.add(project)
      job
    }
    currentJob.join()
    jobsMutex.withLock {
      indexingJobs.remove(project)
    }
  }

  suspend fun triggerIndexing(project: Project) {
    if (isFileListenerAdded.compareAndSet(false, true)) addFileListener()
    var shouldIndex = false
    jobsMutex.withLock {
      if (project !in indexedProjects) {
        indexedProjects.add(project)
        shouldIndex = true
      }
    }
    if (shouldIndex) {
      prepareForSearch(project)
    }
  }

  private fun addFileListener() {
    VirtualFileManager.getInstance().addAsyncFileListener(SemanticSearchFileChangeListener.getInstance(), this)
  }

  private suspend fun indexProject(project: Project) {
    if (!ApplicationManager.getApplication().isUnitTestMode) project.waitForSmartMode()
    logger.debug { "Started full project embedding indexing" }
    SEMANTIC_SEARCH_TRACER.spanBuilder(INDEXING_SPAN_NAME).useWithScope {
      startIndexingSession(project)
      try {
        val projectIndexingStartTime = System.nanoTime()
        indexFiles(project, scanFiles(project).toList().sortedByDescending { it.name.length })
        EmbeddingSearchLogger.indexingFinished(project, forActions = false, TimeoutUtil.getDurationMillis(projectIndexingStartTime))
      } finally {
        finishIndexingSession(project)
      }
    }
    logger.debug { "Finished full project embedding indexing" }
  }

  private fun scanFiles(project: Project): Flow<VirtualFile> {
    val scanLimit = filesLimit?.let { it * 2 } // do not scan all files if there is a limit
    var filteredFiles = 0
    return channelFlow {
      SEMANTIC_SEARCH_TRACER.spanBuilder(SCANNING_SPAN_NAME).useWithScope {
        withBackgroundProgress(project, EmbeddingsBundle.getMessage("ml.embeddings.indices.scanning.label")) {
          ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (file.isFile && file.isValid && file.isInLocalFileSystem) {
              launch { send(file) }
              filteredFiles += 1
            }
            scanLimit == null || filteredFiles < scanLimit
          }
        }
      }
    }
  }

  suspend fun indexFiles(project: Project, files: List<VirtualFile>) {
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (!settings.shouldIndexAnythingFileBased) return

    withContext(indexingScope.coroutineContext) {
      withContext(indexingContext) {
        val filesChannel = Channel<IndexableFile>(capacity = BUFFER_SIZE)
        val classesChannel = Channel<IndexableClass>(capacity = BUFFER_SIZE)
        val symbolsChannel = Channel<IndexableSymbol>(capacity = BUFFER_SIZE)

        suspend fun sendEntities(indexId: IndexId, channel: ReceiveChannel<IndexableEntity>) {
          val entities = ArrayList<IndexableEntity>(BATCH_SIZE)
          var index = 0
          for (entity in channel) {
            if (entities.size < BATCH_SIZE) entities.add(entity) else entities[index] = entity
            ++index
            if (index == BATCH_SIZE) {
              storageManagerWrapper.addAbsent(project, indexId, entities)
              index = 0
            }
          }
          if (entities.isNotEmpty()) {
            storageManagerWrapper.addAbsent(project, indexId, entities)
          }
        }

        launch { sendEntities(IndexId.FILES, filesChannel) }
        launch { sendEntities(IndexId.CLASSES, classesChannel) }
        launch { sendEntities(IndexId.SYMBOLS, symbolsChannel) }

        val psiManager = PsiManager.getInstance(project)
        val processedFiles = AtomicInteger(0)
        val total: Int = filesLimit?.let { minOf(files.size, it) } ?: files.size
        logger.debug { "Effective embedding indexing files limit: $total" }
        withContext(filesIterationContext) {
          val limit = filesLimit
          repeat(FILE_WORKER_COUNT) { worker ->
            var index = worker
            launch {
              while (index < files.size) {
                if (limit != null && processedFiles.get() >= limit) return@launch
                val file = files[index]
                if (file.isFile && file.isValid && file.isInLocalFileSystem) {
                  processFile(file, psiManager, settings, filesChannel, classesChannel, symbolsChannel)
                  processedFiles.incrementAndGet()
                }
                else {
                  logger.debug { "File is not valid: ${file.name}" }
                }
                index += FILE_WORKER_COUNT
              }
            }
          }
        }
        filesChannel.close()
        classesChannel.close()
        symbolsChannel.close()
      }
    }
  }

  private suspend fun processFile(
    file: VirtualFile,
    psiManager: PsiManager,
    settings: EmbeddingIndexSettings,
    filesChannel: Channel<IndexableFile>,
    classesChannel: Channel<IndexableClass>,
    symbolsChannel: Channel<IndexableSymbol>,
  ) = coroutineScope {
    if (settings.shouldIndexFiles) {
      launch {
        filesChannel.send(IndexableFile(file))
      }
    }

    if (settings.shouldIndexClasses || settings.shouldIndexSymbols) {
      val psiFile = readActionUndispatched { psiManager.findFile(file) } ?: return@coroutineScope

      if (settings.shouldIndexClasses) {
        launch {
          readActionUndispatched { FileIndexableEntitiesProvider.extractClasses(psiFile) }.collect(classesChannel::send)
        }
      }
      if (settings.shouldIndexSymbols) {
        launch {
          readActionUndispatched { FileIndexableEntitiesProvider.extractSymbols(psiFile) }.collect(symbolsChannel::send)
        }
      }
    }
  }

  private suspend fun startIndexingSession(project: Project) {
    for (indexId in FILE_BASED_INDICES) {
      storageManagerWrapper.startIndexingSession(project, indexId)
    }
  }

  private suspend fun finishIndexingSession(project: Project) {
    for (indexId in FILE_BASED_INDICES) {
      storageManagerWrapper.finishIndexingSession(project, indexId)
    }
  }

  companion object {
    fun getInstance(): FileBasedEmbeddingIndexer = service()

    private const val TOTAL_THREAD_LIMIT = 8
    private const val FILE_WORKER_COUNT = 4
    private const val BATCH_SIZE = 128
    private const val BUFFER_SIZE = BATCH_SIZE * 8

    internal const val INDEXING_VERSION = "0.0.1"

    private val FILE_BASED_INDICES = arrayOf(IndexId.FILES, IndexId.CLASSES, IndexId.SYMBOLS)

    private val logger = Logger.getInstance(FileBasedEmbeddingIndexer::class.java)

    private const val SCANNING_SPAN_NAME = "embeddingFilesScanning"
    private const val INDEXING_SPAN_NAME = "embeddingIndexing"
  }

  override fun dispose() {}
}