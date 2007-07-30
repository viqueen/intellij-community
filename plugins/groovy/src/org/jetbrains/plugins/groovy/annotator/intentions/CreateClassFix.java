/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public abstract class CreateClassFix {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.annotator.intentions.CreateClassFix");

  public static IntentionAction createClassFixAction(final GrReferenceElement refElement) {
    return new IntentionAction() {

      @NotNull
      public String getText() { 
        return "Create Class \'" + refElement.getReferenceName() + "\'";
      }

      @NotNull
      public String getFamilyName() {
        return GroovyBundle.message("create.class");
      }

      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof GroovyFile)) return;
        final PsiManager manager = refElement.getManager();
        final String name = refElement.getReferenceName();
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        GroovyFile groovyFile = (GroovyFile) file;
        final String qualifier = groovyFile.getPackageName();
        String title = GroovyBundle.message("create.class", StringUtil.capitalize(CreateClassKind.CLASS.getDescription()));
        GroovyCreateClassDialog dialog = new GroovyCreateClassDialog(project, title, name, qualifier, module);
        dialog.show();

        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;

        PsiDirectory targetDirectory = dialog.getTargetDirectory();
        if (targetDirectory == null) return;

        PsiClass targetClass = createClassByType(targetDirectory, name, manager, refElement);
        if (targetClass != null) {
          String qualifiedName = targetClass.getQualifiedName();
          if (qualifiedName != null && qualifiedName.contains(".")) {
            String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
            if (!packageName.equals(groovyFile.getPackageName())){
              groovyFile.addImportForClass(targetClass);
            }
          }
          putCursor(project, targetClass.getContainingFile(), targetClass);
        }
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  private static PsiClass createClassByType(final PsiDirectory directory,
                                            final String name,
                                            final PsiManager manager,
                                            final PsiElement contextElement) {
    return ApplicationManager.getApplication().runWriteAction(
        new Computable<PsiClass>() {
          public PsiClass compute() {
            try {
              PsiClass targetClass = null;
              try {
                PsiFile file = GroovyTemplatesFactory.createFromTemplate(directory, name, name + ".groovy", "GroovyClass.groovy");
                for (PsiElement element : file.getChildren()) {
                  if (element instanceof PsiClass) {
                    targetClass = ((PsiClass) element);
                    break;
                  }
                }
                if (targetClass == null) {
                  throw new IncorrectOperationException();
                }
              }
              catch (final IncorrectOperationException e) {
                CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox(e, name, directory, false);
                return null;
              }
              PsiModifierList modifiers = targetClass.getModifierList();
              if (!manager.getResolveHelper().isAccessible(targetClass, contextElement, null) &&
                  modifiers != null) {
                modifiers.setModifierProperty(PsiKeyword.PUBLIC, true);
              }
              return targetClass;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
              return null;
            }
          }
        });
  }

  protected static Editor putCursor(Project project, @NotNull PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    VirtualFile virtualFile = targetFile.getVirtualFile();
    if (virtualFile != null) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, textOffset);
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    } else {
      return null;
    }
  }
}
