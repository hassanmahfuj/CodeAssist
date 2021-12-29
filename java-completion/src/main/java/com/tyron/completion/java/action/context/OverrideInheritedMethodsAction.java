package com.tyron.completion.java.action.context;

import android.app.AlertDialog;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableMap;
import com.tyron.completion.java.CompileTask;
import com.tyron.completion.java.CompilerProvider;
import com.tyron.completion.java.action.api.Action;
import com.tyron.completion.java.action.api.ActionContext;
import com.tyron.completion.java.action.api.ActionProvider;
import com.tyron.completion.java.rewrite.OverrideInheritedMethod;
import com.tyron.completion.java.rewrite.Rewrite;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.model.Range;
import com.tyron.completion.model.TextEdit;

import org.openjdk.javax.lang.model.element.Element;
import org.openjdk.javax.lang.model.element.ElementKind;
import org.openjdk.javax.lang.model.element.ExecutableElement;
import org.openjdk.javax.lang.model.element.Modifier;
import org.openjdk.javax.lang.model.element.TypeElement;
import org.openjdk.javax.lang.model.util.Elements;
import org.openjdk.source.tree.ClassTree;
import org.openjdk.source.tree.Tree;
import org.openjdk.source.util.TreePath;
import org.openjdk.source.util.Trees;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class OverrideInheritedMethodsAction extends ActionProvider {

    @Override
    public boolean isApplicable(@NonNull TreePath currentPath) {
        return currentPath.getLeaf() instanceof ClassTree;
    }

    @Override
    public void addMenus(@NonNull ActionContext context) {
        Tree leaf = context.getCurrentPath().getLeaf();
        if (!(leaf instanceof ClassTree)) {
            return;
        }

        MenuItem menuItem = context.addMenu("overrideMethods", "Override Inherited Methods");
        menuItem.setOnMenuItemClickListener(item -> {
            perform(context);
            return true;
        });
    }

    private void perform(ActionContext context) {
        CompileTask task = context.getCompileTask();
        Trees trees = Trees.instance(context.getCompileTask().task);
        Element classElement = trees.getElement(context.getCurrentPath());
        Elements elements = context.getCompileTask().task.getElements();
        Map<String, Rewrite> rewriteMap = new TreeMap<>();
        for (Element member : elements.getAllMembers((TypeElement) classElement)) {
            if (member.getModifiers().contains(Modifier.FINAL)) {
                continue;
            }
            if (member.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            TypeElement methodSource = (TypeElement) member.getEnclosingElement();
            if (methodSource.getQualifiedName().contentEquals("java.lang.Object")) {
                continue;
            }
            if (methodSource.equals(classElement)) {
                continue;
            }
            DiagnosticUtil.MethodPtr ptr = new DiagnosticUtil.MethodPtr(task.task, method);
            Rewrite rewrite = new OverrideInheritedMethod(ptr.className, ptr.methodName,
                    ptr.erasedParameterTypes, context.getCurrentFile(), context.getCursor());
            String title = "Override " + method.getSimpleName() + " from " + ptr.className;
            rewriteMap.put(title, rewrite);
        }

        String[] strings = rewriteMap.keySet().toArray(new String[0]);

        new AlertDialog.Builder(context.getContext())
                .setTitle("Override inherited methods")
                .setItems(strings, (d, w) -> {
                    Rewrite rewrite = rewriteMap.get(strings[w]);
                    context.performAction(new Action(rewrite));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
