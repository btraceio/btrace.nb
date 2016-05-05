/*
 * Copyright (c) 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

package org.netbeans.modules.btrace.editor.errors;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaParserResultTask;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser.Result;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.modules.parsing.spi.SchedulerTask;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.HintsController;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ErrorHintsAnnotator extends JavaParserResultTask<Result>{
    public static class Factory extends TaskFactory {

        @Override
        public Collection<? extends SchedulerTask> create(Snapshot snpsht) {
            return Collections.singleton(new ErrorHintsAnnotator());
        }
    }

    public ErrorHintsAnnotator() {
        super(Phase.ELEMENTS_RESOLVED);
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public void run(Result t, SchedulerEvent se) {
        CompilationInfo ci = CompilationInfo.get(t);

        Map<ErrorDescription, Integer> errorMap = new HashMap<>();

        ErrorScanner scanner = new ErrorScanner(ci);
        scanner.scan(ci.getCompilationUnit(), errorMap);
        if (scanner.getDocument() != null) {
            HintsController.setErrors(scanner.getDocument(), "btrace-errors", errorMap.keySet());
        }
    }

    @Override
    public void cancel() {
        //;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

}
