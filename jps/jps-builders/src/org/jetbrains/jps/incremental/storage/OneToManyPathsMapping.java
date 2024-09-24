// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentMapBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class OneToManyPathsMapping extends AbstractStateStorage<String, Collection<String>> implements OneToManyPathMapping {
  private final PathRelativizerService relativizer;

  public OneToManyPathsMapping(@NotNull Path storePath, PathRelativizerService relativizer) throws IOException {
    super(PersistentMapBuilder.newBuilder(storePath, PathStringDescriptors.createPathStringDescriptor(), new PathCollectionExternalizer()));
    this.relativizer = relativizer;
  }

  @Override
  public void setOutputs(@NotNull String keyPath, @NotNull List<String> boundPaths) throws IOException {
    super.update(normalizePath(keyPath), normalizePaths(boundPaths));
  }

  void setOutput(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.update(normalizePath(keyPath), List.of(normalizePath(boundPath)));
  }

  public void appendData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.appendData(normalizePath(keyPath), List.of(normalizePath(boundPath)));
  }

  @Override
  public void appendData(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.appendData(normalizePath(keyPath), normalizePaths((List<String>)boundPaths));
  }

  /**
   * @deprecated Use {@link #getOutputs(String)}
   */
  @Deprecated(forRemoval = true)
  @Override
  public @Nullable Collection<String> getState(@NotNull String keyPath) throws IOException {
    return getOutputs(keyPath);
  }

  @Override
  public @Nullable List<String> getOutputs(@NotNull String keyPath) throws IOException {
    List<String> collection = (List<String>)super.getState(relativizer.toRelative(keyPath));
    if (collection == null) {
      return null;
    }
    else if (collection.isEmpty()) {
      return Collections.emptyList();
    }
    else {
      String[] result = new String[collection.size()];
      for (int i = 0, size = collection.size(); i < size; i++) {
        result[i] = relativizer.toFull(collection.get(i));
      }
      return Arrays.asList(result);
    }
  }

  public String @Nullable [] getOutputArray(@NotNull String keyPath) throws IOException {
    List<String> collection = (List<String>)super.getState(relativizer.toRelative(keyPath));
    if (collection == null) {
      return null;
    }
    else if (collection.isEmpty()) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
    else {
      String[] result = new String[collection.size()];
      for (int i = 0, size = collection.size(); i < size; i++) {
        result[i] = relativizer.toFull(collection.get(i));
      }
      return result;
    }
  }

  @Override
  public void remove(@NotNull String keyPath) throws IOException {
    super.remove(relativizer.toRelative(keyPath));
  }

  @Override
  public @NotNull Iterator<String> getKeysIterator() throws IOException {
    return super.getKeyIterator(relativizer::toFull);
  }

  @ApiStatus.Internal
  public @NotNull SourceToOutputMappingCursor cursor() throws IOException {
    return new SourceToOutputMappingCursorImpl(getKeysIterator());
  }

  public void removeData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    String relativeKeyPath = relativizer.toRelative(keyPath);
    List<String> oldList = (List<String>)super.getState(relativeKeyPath);
    if (oldList == null || oldList.isEmpty()) {
      return;
    }

    String relativePath = relativizer.toRelative(boundPath);
    int index = oldList.indexOf(relativePath);
    if (index < 0) {
      return;
    }

    if (oldList.size() == 1) {
      super.remove(relativeKeyPath);
      return;
    }

    List<String> newState = new ArrayList<>(oldList.size() - 1);
    for (int i = 0; i < oldList.size(); i++) {
      if (index != i) {
        newState.add(oldList.get(i));
      }
    }
    super.update(relativeKeyPath, newState);
  }

  private static final class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
    @Override
    public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeUTF(out, str);
      }
    }

    @Override
    public List<String> read(@NotNull DataInput in) throws IOException {
      List<String> result = new ArrayList<>();
      DataInputStream stream = (DataInputStream)in;
      HashSet<String> guard = new HashSet<>();
      while (stream.available() > 0) {
        String s = IOUtil.readUTF(stream);
        if (guard.add(s)) {
          result.add(s);
        }
      }
      return result;
    }
  }

  private String normalizePath(@NotNull String path) {
    return relativizer.toRelative(path);
  }

  private @NotNull @Unmodifiable List<String> normalizePaths(@NotNull List<String> outputs) {
    String[] normalized = new String[outputs.size()];
    for (int i = 0; i < normalized.length; i++) {
      normalized[i] = relativizer.toRelative(outputs.get(i));
    }
    return Arrays.asList(normalized);
  }

  private final class SourceToOutputMappingCursorImpl implements SourceToOutputMappingCursor {
    private final Iterator<String> mySourceIterator;
    private String sourcePath;

    private SourceToOutputMappingCursorImpl(Iterator<String> sourceIterator) { mySourceIterator = sourceIterator; }

    @Override
    public boolean hasNext() {
      return mySourceIterator.hasNext();
    }

    @Override
    public @NotNull String next() {
      sourcePath = mySourceIterator.next();
      return sourcePath;
    }

    @Override
    public String @NotNull [] getOutputPaths() {
      try {
        return Objects.requireNonNullElse(getOutputArray(sourcePath), ArrayUtilRt.EMPTY_STRING_ARRAY);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
