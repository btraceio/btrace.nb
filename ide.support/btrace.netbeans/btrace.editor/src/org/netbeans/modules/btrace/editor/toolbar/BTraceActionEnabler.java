/*
 * Copyright (c) 2010, 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.netbeans.modules.btrace.editor.toolbar;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import java.util.Collection;
import java.util.Collections;
import javax.lang.model.element.Element;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaParserResultTask;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser.Result;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceActionEnabler extends JavaParserResultTask<Result>{
    final private static String BTRACE_CLASS = "com.sun.btrace.annotations.BTrace";
    public static class Factory extends TaskFactory {

        @Override
        public Collection<? extends SchedulerTask> create(Snapshot snapshot) {
            return Collections.singleton(new BTraceActionEnabler());
        }

    }
    private class AnnotationScanner extends TreePathScanner<Void, CompilationInfo> {
        private boolean isBTrace = false;

        @Override
        public Void visitAnnotation(AnnotationTree node, CompilationInfo ci) {
            Element e = ci.getTrees().getElement(getCurrentPath());
            if (e != null && e.asType().toString().equals(BTRACE_CLASS)) {
                isBTrace = true;
            }
            return super.visitAnnotation(node, ci);
        }

        @Override
        public Void scan(Tree tree, CompilationInfo p) {
            return isBTrace ? null : super.scan(tree, p);
        }
    };

    public BTraceActionEnabler() {
        super(Phase.PARSED);
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public void run(Result result, SchedulerEvent event) {
        CompilationInfo ci = CompilationInfo.get(result);
        AnnotationScanner scanner = new AnnotationScanner();
        scanner.scan(ci.getCompilationUnit(), ci);
        ToolbarActionManager.getInstance().updateState(ci.getFileObject(), scanner.isBTrace);
    }

    @Override
    public void cancel() {
        // TODO
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

}
