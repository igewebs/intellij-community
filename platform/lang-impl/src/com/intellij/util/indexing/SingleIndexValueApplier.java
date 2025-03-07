// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SingleIndexValueApplier<FileIndexMetaData> {
  public final @NotNull ID<?, ?> indexId;
  public final int shardNo;

  private final @NotNull FileBasedIndexImpl indexImpl;

  private final int inputId;

  private final @Nullable FileIndexMetaData fileIndexMetaData;
  private final @NotNull StorageUpdate storageUpdate;
  private final @NotNull String fileInfo;
  private final boolean isMock;

  /** Time of {@code index.mapInputAndPrepareUpdate(inputId, null)}, in nanoseconds */
  public final long evaluatingIndexValueApplierTime;

  SingleIndexValueApplier(@NotNull FileBasedIndexImpl index,
                          @NotNull ID<?, ?> indexId,
                          int shardNo,
                          int inputId,
                          @Nullable FileIndexMetaData fileIndexMetaData,
                          @NotNull StorageUpdate update,
                          @NotNull VirtualFile file,
                          @NotNull FileContent currentFC,
                          long evaluatingIndexValueApplierTime) {
    indexImpl = index;

    this.indexId = indexId;
    this.shardNo = shardNo;
    this.inputId = inputId;

    this.fileIndexMetaData = fileIndexMetaData;
    this.evaluatingIndexValueApplierTime = evaluatingIndexValueApplierTime;
    storageUpdate = update;
    fileInfo = FileBasedIndexImpl.getFileInfoLogString(inputId, file, currentFC);
    isMock = FileBasedIndexImpl.isMock(currentFC.getFile());
  }

  public boolean wasIndexProvidedByExtension() {
    return storageUpdate instanceof IndexInfrastructureExtensionUpdate &&
           ((IndexInfrastructureExtensionUpdate)storageUpdate).isIndexProvided();
  }

  public boolean apply() {
    FileBasedIndexImpl.markFileWritingIndexes(inputId);
    try {
      return doApply();
    }
    catch (RuntimeException exception) {
      indexImpl.requestIndexRebuildOnException(exception, indexId);
      return false;
    }
    finally {
      FileBasedIndexImpl.unmarkWritingIndexes();
    }
  }

  private boolean doApply() {
    if (indexImpl.runUpdateForPersistentData(storageUpdate)) {
      if (FileBasedIndexEx.doTraceStubUpdates(indexId) || FileBasedIndexEx.doTraceIndexUpdates()) {
        FileBasedIndexImpl.LOG.info("index " + indexId + " update finished for " + fileInfo);
      }
      if (!isMock) {
        //TODO RC: this is the global lock, one-per-app-service -- why do we need it here?
        //         what exactly it protects: .getIndex() or setIndexedState...()? If 2nd, then why it is a read lock,
        //         not write lock?
        ConcurrencyUtil.withLock(indexImpl.myReadLock, () -> {
          //noinspection unchecked
          UpdatableIndex<?, ?, FileContent, FileIndexMetaData> index =
            (UpdatableIndex<?, ?, FileContent, FileIndexMetaData>)indexImpl.getIndex(indexId);
          index.setIndexedStateForFileOnFileIndexMetaData(inputId, fileIndexMetaData, wasIndexProvidedByExtension());
        });
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "SingleIndexValueApplier{" +
           "indexId=" + indexId +
           ", inputId=" + inputId +
           ", fileInfo='" + fileInfo + '\'' +
           '}';
  }
}
