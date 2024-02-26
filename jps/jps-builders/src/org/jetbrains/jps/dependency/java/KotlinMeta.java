// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import kotlinx.metadata.*;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * A set of data needed to create a kotlin.Metadata annotation instance parsed from bytecode.
 * The created annotation instance can be further introspected with <a href="https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm">kotlinx-metadata-jvm</a> library
 */
public final class KotlinMeta implements JvmMetadata<KotlinMeta, KotlinMeta.Diff> {
  private static final String[] EMPTY_STRING_ARRAY = new String[0];
  private static final int[] EMPTY_INT_ARRAY = new int[0];

  private final int myKind;
  private final int @NotNull [] myVersion;
  private final String @NotNull [] myData1;
  private final String @NotNull [] myData2;
  @NotNull private final String myExtraString;
  @NotNull private final String myPackageName;
  private final int myExtraInt;

  public KotlinMeta(int kind, int @Nullable [] version, String @Nullable [] data1,  String @Nullable [] data2, @Nullable String extraString, @Nullable String packageName, int extraInt) {
    myKind = kind;
    myVersion = version != null? version : EMPTY_INT_ARRAY;
    myData1 = data1 != null? data1 : EMPTY_STRING_ARRAY;
    myData2 = data2 != null? data2 : EMPTY_STRING_ARRAY;
    myExtraString = extraString != null? extraString : "";
    myPackageName = packageName != null? packageName : "";
    myExtraInt = extraInt;
  }

  public KotlinMeta(GraphDataInput in) throws IOException {
    myKind = in.readInt();

    int versionsCount = in.readInt();
    myVersion = versionsCount > 0? new int[versionsCount] : EMPTY_INT_ARRAY;
    for (int idx = 0; idx < versionsCount; idx++) {
      myVersion[idx] = in.readInt();
    }

    myData1 = RW.readCollection(in, in::readUTF).toArray(EMPTY_STRING_ARRAY);
    myData2 = RW.readCollection(in, in::readUTF).toArray(EMPTY_STRING_ARRAY);
    myExtraString = in.readUTF();
    myPackageName = in.readUTF();
    myExtraInt = in.readInt();
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    out.writeInt(myKind);

    out.writeInt(myVersion.length);
    for (int elem : myVersion) {
      out.writeInt(elem);
    }

    RW.writeCollection(out, Arrays.asList(myData1), out::writeUTF);
    RW.writeCollection(out, Arrays.asList(myData2), out::writeUTF);
    out.writeUTF(myExtraString);
    out.writeUTF(myPackageName);
    out.writeInt(myExtraInt);
  }

  public int getKind() {
    return myKind;
  }

  public int @NotNull [] getVersion() {
    return myVersion;
  }

  public String @NotNull [] getData1() {
    return myData1;
  }

  public String @NotNull [] getData2() {
    return myData2;
  }

  @NotNull
  public String getExtraString() {
    return myExtraString;
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  public Integer getExtraInt() {
    return myExtraInt;
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof KotlinMeta;
  }

  @Override
  public int diffHashCode() {
    return KotlinMeta.class.hashCode();
  }

  @Override
  public Diff difference(KotlinMeta past) {
    return new Diff(past);
  }

  private KotlinClassMetadata[] myCachedMeta;

  public KotlinClassMetadata getClassMetadata() {
    if (myCachedMeta == null) {
      try {
        myCachedMeta = new KotlinClassMetadata[] {KotlinClassMetadata.readLenient(new KotlinClassHeader(
          getKind(), getVersion(), getData1(), getData2(), getExtraString(), getPackageName(), getExtraInt()
        ))};
      }
      catch (Throwable e) {
        myCachedMeta = new KotlinClassMetadata[] {null};
      }
    }
    return myCachedMeta[0];
  }

  public Iterable<KmProperty> getKmProperties() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container != null? container.getProperties() : Collections.emptyList();
  }

  public Iterable<KmFunction> getKmFunctions() {
    KmDeclarationContainer container = getDeclarationContainer();
    return container != null? container.getFunctions() : Collections.emptyList();
  }

  @Nullable
  public KmDeclarationContainer getDeclarationContainer() {
    KotlinClassMetadata clsMeta = getClassMetadata();
    if (clsMeta instanceof KotlinClassMetadata.Class) {
      return ((KotlinClassMetadata.Class)clsMeta).getKmClass();
    }
    if (clsMeta instanceof KotlinClassMetadata.FileFacade) {
      return ((KotlinClassMetadata.FileFacade)clsMeta).getKmPackage();
    }
    if (clsMeta instanceof KotlinClassMetadata.MultiFileClassPart) {
      return ((KotlinClassMetadata.MultiFileClassPart)clsMeta).getKmPackage();
    }
    return null;
  }

  public final class Diff implements Difference {

    private final KotlinMeta myPast;

    Diff(KotlinMeta past) {
      myPast = past;
    }

    @Override
    public boolean unchanged() {
      return !kindChanged() && !versionChanged() && !packageChanged() && !extraChanged() && !functions().unchanged() && !properties().unchanged()/*&& !dataChanged()*/;
    }

    public boolean kindChanged() {
      return myPast.myKind != myKind;
    }

    public boolean versionChanged() {
      return !Arrays.equals(myPast.myVersion, myVersion);
    }

    //public boolean dataChanged() {
    //  return !Arrays.equals(myPast.myData1, myData1) || !Arrays.equals(myPast.myData2, myData2);
    //}

    public boolean packageChanged() {
      return !Objects.equals(myPast.myPackageName, myPackageName);
    }

    public boolean extraChanged() {
      return myPast.myExtraInt != myExtraInt || !Objects.equals(myPast.myExtraString, myExtraString);
    }

    public Specifier<KmFunction, KmFunctionsDiff> functions() {
      return Difference.deepDiff(myPast.getKmFunctions(), getKmFunctions(),
        (f1, f2) -> Objects.equals(JvmExtensionsKt.getSignature(f1), JvmExtensionsKt.getSignature(f2)),
        f -> Objects.hashCode(JvmExtensionsKt.getSignature(f)),
        KmFunctionsDiff::new
      );
    }

    public Specifier<KmProperty, KmPropertiesDiff> properties() {
      return Difference.deepDiff(myPast.getKmProperties(), getKmProperties(),
        (p1, p2) -> Objects.equals(p1.getName(), p2.getName()),
        p -> Objects.hashCode(p.getName()),
        KmPropertiesDiff::new
      );
    }
  }

  public static final class KmFunctionsDiff implements Difference {
    private final KmFunction past;
    private final KmFunction now;

    public KmFunctionsDiff(KmFunction past, KmFunction now) {
      this.past = past;
      this.now = now;
    }

    @Override
    public boolean unchanged() {
      return !becameNullable() && !argsBecameNotNull();
    }

    public boolean becameNullable() {
      return !Attributes.isNullable(past.getReturnType()) && Attributes.isNullable(now.getReturnType());
    }

    public boolean argsBecameNotNull() {
      var nowIt = now.getValueParameters().iterator();
      for (KmValueParameter pastParam : past.getValueParameters()) {
        KmValueParameter nowParam = nowIt.next();
        if (Attributes.isNullable(pastParam.getType()) && !Attributes.isNullable(nowParam.getType())) {
          return true;
        }
      }
      return false;
    }
  }

  public static final class KmPropertiesDiff implements Difference {
    private final KmProperty past;
    private final KmProperty now;

    public KmPropertiesDiff(KmProperty past, KmProperty now) {
      this.past = past;
      this.now = now;
    }

    @Override
    public boolean unchanged() {
      return !nullabilityChanged();
    }

    public boolean nullabilityChanged() {
      return !Objects.equals(past.getReturnType(), now.getReturnType());
    }

    public boolean becameNullable() {
      return !Attributes.isNullable(past.getReturnType()) && Attributes.isNullable(now.getReturnType());
    }

    public boolean becameNotNull() {
      return Attributes.isNullable(past.getReturnType()) && !Attributes.isNullable(now.getReturnType());
    }

  }
}
