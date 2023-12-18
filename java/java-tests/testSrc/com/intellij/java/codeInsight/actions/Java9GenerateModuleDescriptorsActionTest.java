// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.actions;

import com.intellij.JavaTestUtil;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.util.LazyInitializer;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.JobKt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.conversion.ModuleSettings.MODULE_ROOT_MANAGER_COMPONENT;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static org.jetbrains.jps.model.serialization.JpsProjectLoader.*;
import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.*;
import static org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.*;

public class Java9GenerateModuleDescriptorsActionTest extends LightMultiFileTestCase {
  private MultiModuleProjectDescriptor myDescriptor = new MultiModuleProjectDescriptor(Paths.get(getTestDataPath() + "/" + getTestName(true)));

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/actions/generateModuleDescriptors";
  }

  public void testSingleModule() throws IOException {
    performReformatAction();
  }

  public void testSingleModuleWithDependency() throws IOException {
    performReformatAction();
  }

  public void testDependentModules() throws IOException {
    performReformatAction();
  }

  protected void performReformatAction() throws IOException {
    // INIT
    final AnAction action = ActionManager.getInstance().getAction("GenerateModuleDescriptors");
    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", dataId -> {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return Collections.emptyList();
      if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
      return null;
    });

    // EXEC
    action.actionPerformed(event);

    // CHECK
    final MultiModuleProjectDescriptor descriptor = (MultiModuleProjectDescriptor)getProjectDescriptor();

    PlatformTestUtil.assertDirectoriesEqual(LocalFileSystem.getInstance().findFileByNioFile(descriptor.getAfterPath()),
                                            LocalFileSystem.getInstance().findFileByNioFile(descriptor.getProjectPath()),
                                            file -> "java".equals(file.getExtension()));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      WriteAction.run(() -> {
        ModuleManager moduleManager = ModuleManager.getInstance(getProject());
        for (Module module : moduleManager.getModules()) {
          if (!module.isDisposed()) moduleManager.disposeModule(module);
        }
        ((MultiModuleProjectDescriptor)getProjectDescriptor()).cleanup();
        LightPlatformTestCase.closeAndDeleteProject();
      });
      myDescriptor = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private static class MultiModuleProjectDescriptor extends DefaultLightProjectDescriptor {
    private final Path myPath;
    private final LazyInitializer.LazyValue<ProjectModel> myProjectModel;

    MultiModuleProjectDescriptor(@NotNull Path path) {
      myPath = path;
      Path projectPath = TemporaryDirectory.generateTemporaryPath(ProjectImpl.LIGHT_PROJECT_NAME);

      try {
        FileUtil.copyDir(getBeforePath().toFile(), projectPath.toFile());
        myProjectModel = LazyInitializer.create(() -> new ProjectModel(projectPath));
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    Path getBeforePath() {
      return myPath.resolve("before");
    }

    Path getAfterPath() {
      return myPath.resolve("after");
    }

    void cleanup() {
      for (ModuleDescriptor module : myProjectModel.get().getModules()) {
        try {
          if (module.src() != null) {
            for (VirtualFile child : module.src().getChildren()) {
              child.delete(this);
            }
          }
          if (module.testSrc() != null) {
            for (VirtualFile child : module.testSrc().getChildren()) {
              child.delete(this);
            }
          }
          for (VirtualFile child : VirtualFileManager.getInstance().refreshAndFindFileByNioPath(myProjectModel.get().getProjectPath())
            .getChildren()) {
            child.delete(this);
          }
        }
        catch (IOException ignore) {
        }
      }
    }

    @Override
    public @NotNull Path generateProjectPath() {
      return myProjectModel.get().getProjectPath();
    }

    public @NotNull Path getProjectPath() {
      return myProjectModel.get().getProjectPath();
    }

    @Override
    public Sdk getSdk() {
      return myProjectModel.get().getSdk();
    }

    @Override
    public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
      WriteAction.run(() -> {
        VfsUtil.markDirtyAndRefresh(false, true, true, myProjectModel.get().getProjectPath().toFile());
        // replace services
        CompilerProjectExtension.getInstance(project).setCompilerOutputUrl(myProjectModel.get().getOutputUrl());
        ServiceContainerUtil.replaceService(project, DumbService.class,
                                            new DumbServiceImpl(project, CoroutineScopeKt.CoroutineScope(JobKt.Job(null))) {
                                              @Override
                                              public void smartInvokeLater(@NotNull Runnable runnable) {
                                                runnable.run();
                                              }
                                            }, project);
        ServiceContainerUtil.replaceService(project, CompilerManager.class, new CompilerManagerImpl(project) {
          @Override
          public boolean isUpToDate(@NotNull CompileScope scope) {
            return true;
          }
        }, project);

        for (ModuleDescriptor descriptor : myProjectModel.get().getModules()) {
          Path iml = descriptor.basePath().resolve(descriptor.name() + ModuleFileType.DOT_DEFAULT_EXTENSION);
          final Module module = Files.exists(iml)
                                ? ModuleManager.getInstance(project).loadModule(iml)
                                : createModule(project, iml);
          handler.moduleCreated(module);

          ModuleRootModificationUtil.updateModel(module, model -> {
            model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(descriptor.languageLevel());
            model.setSdk(IdeaTestUtil.getMockJdk(descriptor.languageLevel().toJavaVersion()));
            if (descriptor.src() != null) {
              model.addContentEntry(descriptor.src()).addSourceFolder(descriptor.src(), JavaSourceRootType.SOURCE);
              handler.sourceRootCreated(descriptor.src());
            }
            if (descriptor.testSrc() != null) {
              model.addContentEntry(descriptor.testSrc()).addSourceFolder(descriptor.testSrc(), JavaSourceRootType.TEST_SOURCE);
              handler.sourceRootCreated(descriptor.testSrc());
            }

            // maven
            final Path mavenOutputPath = descriptor.basePath().resolve("target").resolve("classes");
            if (Files.exists(mavenOutputPath)) {
              final CompilerModuleExtension compiler = model.getModuleExtension(CompilerModuleExtension.class);
              compiler.setCompilerOutputPath(mavenOutputPath.toString());
              compiler.inheritCompilerOutputPath(false);
            }
          });
        }
      });
    }
  }

  private static class ProjectModel {
    private final Path myProjectPath;
    private String myOutputUrl;
    private LanguageLevel myLanguageLevel;
    private Sdk mySdk;
    private final List<ModuleDescriptor> myModules = new ArrayList<>();

    private ProjectModel(Path path) {
      try {
        myProjectPath = path;
        load();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private Path getProjectPath() {
      return myProjectPath;
    }

    private String getOutputUrl() {
      return myOutputUrl;
    }

    private LanguageLevel getLanguageLevel() {
      return myLanguageLevel == null ? LanguageLevel.JDK_11 : myLanguageLevel;
    }

    private Sdk getSdk() {
      return mySdk == null ? IdeaTestUtil.getMockJdk11() : mySdk;
    }

    private List<ModuleDescriptor> getModules() {
      return myModules;
    }

    private void load() throws IOException, JDOMException {
      final Path projectConfigurationPath = myProjectPath.resolve(DIRECTORY_STORE_FOLDER);
      final Element miscXml = JDomSerializationUtil.findComponent(JDOMUtil.load(projectConfigurationPath.resolve("misc.xml")),
                                                                  "ProjectRootManager");

      myLanguageLevel = parseLanguageLevel(miscXml.getAttributeValue(LANGUAGE_LEVEL_ATTRIBUTE), LanguageLevel.JDK_11);
      mySdk = IdeaTestUtil.getMockJdk(getLanguageLevel().toJavaVersion());

      myOutputUrl = prepare(miscXml.getChild(OUTPUT_TAG).getAttributeValue(JpsJavaModelSerializerExtension.URL_ATTRIBUTE));

      final Element modulesXml = JDomSerializationUtil.findComponent(JDOMUtil.load(projectConfigurationPath.resolve("modules.xml")),
                                                                     MODULE_MANAGER_COMPONENT);
      final Element modulesElement = modulesXml.getChild(MODULES_TAG);
      final List<Element> moduleElements = modulesElement.getChildren(MODULE_TAG);
      for (Element moduleAttr : moduleElements) {
        final Path iml = Paths.get(prepare(moduleAttr.getAttributeValue(FILE_PATH_ATTRIBUTE))); // .iml
        final String moduleName = FileUtil.getNameWithoutExtension(iml.toFile()); // module name

        if (Files.exists(iml)) {
          final Element component = JDomSerializationUtil.findComponent(JDOMUtil.load(iml), MODULE_ROOT_MANAGER_COMPONENT);
          final LanguageLevel moduleLanguageLevel =
            parseLanguageLevel(component.getAttributeValue(MODULE_LANGUAGE_LEVEL_ATTRIBUTE), getLanguageLevel());
          final Element content = component.getChild(CONTENT_TAG);
          Map<Boolean, String> sources = Collections.emptyMap();
          if (content != null) {
            // true -> testSrc, false -> src
            sources = content.getChildren(SOURCE_FOLDER_TAG).stream()
              .collect(Collectors.toMap(src -> Boolean.valueOf(src.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE)),
                                        src -> prepare(src.getAttributeValue(JpsModuleRootModelSerializer.URL_ATTRIBUTE),
                                                       iml.getParent().toString())));
          }
          myModules.add(new ModuleDescriptor(moduleName, iml.getParent(),
                                             urlToVirtualFile(sources.get(Boolean.FALSE)),
                                             urlToVirtualFile(sources.get(Boolean.TRUE)),
                                             moduleLanguageLevel));
        }
        else {
          myModules.add(new ModuleDescriptor(moduleName, iml.getParent(), null, null, getLanguageLevel()));
        }
      }
    }

    @Nullable
    private static LanguageLevel parseLanguageLevel(@Nullable String level, LanguageLevel... levels) {
      LanguageLevel result = null;
      if (level != null) result = LanguageLevel.valueOf(level);
      if (result != null) return result;
      for (LanguageLevel languageLevel : levels) {
        if (languageLevel != null) return languageLevel;
      }
      return null;
    }

    @Contract("null->null")
    @Nullable
    private static VirtualFile urlToVirtualFile(@Nullable String url) {
      if (url == null) {
        return null;
      }
      else {
        return VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
      }
    }

    @NotNull
    private String prepare(@NotNull String path) {
      return path.replace("$PROJECT_DIR$", myProjectPath.toString());
    }

    @NotNull
    private String prepare(@NotNull String path, @NotNull String moduleDir) {
      return prepare(path).replace("$MODULE_DIR$", moduleDir);
    }
  }

  private record ModuleDescriptor(@NotNull String name, @NotNull Path basePath, @Nullable VirtualFile src, @Nullable VirtualFile testSrc,
                                  @NotNull LanguageLevel languageLevel) {
  }
}
