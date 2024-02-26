// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE;
import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.RecordLayout.RECORD_SIZE_IN_BYTES;
import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage.NULL_ID;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class PersistentFSRecordsStorageLockFreeOverMMappedFileTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSRecordsLockFreeOverMMappedFile> {

  private static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  private static final int PAGE_SIZE = DEFAULT_MAPPED_CHUNK_SIZE;

  @Parameterized.Parameters(name = "{index}: {0}")
  public static UpdateAPIMethod[] METHODS_TO_TEST() {
    return new UpdateAPIMethod[]{
      DEFAULT_API_UPDATE_METHOD,
      MODERN_API_UPDATE_METHOD
    };
  }



  public PersistentFSRecordsStorageLockFreeOverMMappedFileTest(UpdateAPIMethod updateMethodToTest) {
    super(MAX_RECORDS_TO_INSERT, updateMethodToTest);
  }


  @NotNull
  @Override
  protected PersistentFSRecordsLockFreeOverMMappedFile openStorage(@NotNull Path storagePath) throws IOException {
    return MMappedFileStorageFactory.withDefaults()
      .pageSize(DEFAULT_MAPPED_CHUNK_SIZE)
      .wrapStorageSafely(storagePath, PersistentFSRecordsLockFreeOverMMappedFile::new);
  }

  @Test
  public void recordAreAlwaysAlignedFullyOnSinglePage() {
    int enoughRecords = (PAGE_SIZE / RECORD_SIZE_IN_BYTES) * 16;

    for (int recordId = 0; recordId < enoughRecords; recordId++) {
      long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      long recordEndOffsetInFile = recordOffsetInFile + RECORD_SIZE_IN_BYTES - 1;
      assertEquals(
        "Record(#" + recordId + ", offset: " + recordOffsetInFile + ") must start and end on a same page",
        recordOffsetInFile / PAGE_SIZE,
        recordEndOffsetInFile / PAGE_SIZE
      );
    }
  }

  @Test
  public void recordOffsetCalculatedByStorageIsConsistentWithPlainCalculation() {
    int enoughRecords = (PAGE_SIZE / RECORD_SIZE_IN_BYTES) * 16;

    long expectedRecordOffsetInFile = PersistentFSRecordsLockFreeOverMMappedFile.HEADER_SIZE;
    for (int recordId = NULL_ID + 1; recordId < enoughRecords; recordId++) {

      long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      assertEquals("recordOffset(recordId:" + recordId + ") must be " + expectedRecordOffsetInFile,
                   expectedRecordOffsetInFile,
                   recordOffsetInFile
      );

      expectedRecordOffsetInFile += RECORD_SIZE_IN_BYTES;
      if (PAGE_SIZE - (expectedRecordOffsetInFile % PAGE_SIZE) < RECORD_SIZE_IN_BYTES) {
        expectedRecordOffsetInFile = (expectedRecordOffsetInFile / PAGE_SIZE + 1) * PAGE_SIZE;
      }
    }
  }

}