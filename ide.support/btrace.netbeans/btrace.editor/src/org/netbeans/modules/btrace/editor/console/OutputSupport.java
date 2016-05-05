/*
 * Copyright (c) 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.

 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.netbeans.modules.btrace.editor.console;

import java.awt.event.ActionEvent;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.netbeans.modules.btrace.api.BTraceTask;
import org.netbeans.modules.btrace.api.BTraceTask.State;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FAILED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FINISHED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.RUNNING;
import org.netbeans.modules.btrace.spi.OutputProvider;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**
 *
 * @author Jaroslav Bachorik
 */
@ServiceProvider(service=OutputProvider.class)
public class OutputSupport implements OutputProvider {
    final private Map<BTraceTask, InputOutput> ioMap = new WeakHashMap<>();
    final private Map<String, InputOutput> toClose = new HashMap<>();

    public PrintWriter getStdOut(BTraceTask task) {
        return ((OutputSupport)getInstance()).getStdOutEx(task);
    }

    public PrintWriter getStdErr(BTraceTask task) {
        return ((OutputSupport)getInstance()).getStdErrEx(task);
    }

    public static OutputProvider getInstance() {
        return Lookup.getDefault().lookup(OutputProvider.class);
    }

    private PrintWriter getStdOutEx(BTraceTask task) {
        return getTaskIO(task).getOut();
    }

    public PrintWriter getStdErrEx(BTraceTask task) {
        return getTaskIO(task).getErr();
    }

    private InputOutput getTaskIO(BTraceTask task) {
        synchronized(ioMap) {
            String ioTitle = (task.getName() != null ? task.getName() : "<?>") + "@" + task.getPid();
            InputOutput io = ioMap.get(task);
            if (io == null) {
                io = toClose.remove(ioTitle);
                if (io != null) {
                    io.closeInputOutput();
                }
                io = IOProvider.getDefault().getIO(ioTitle, getTaskActions(task));
                ioMap.put(task, io);
                toClose.put(ioTitle, io);
                io.select();
            }
            return io;
        }
    }

    private static Action[] getTaskActions(final BTraceTask task) {
        final List<Action> actions = new ArrayList<>();
        if (task.hasEvents()) {
            if (!task.getNamedEvents().isEmpty()) {
                final WeakReference<BTraceTask> taskRef = new WeakReference<>(task);
                actions.add(new AbstractAction("Send Named Event", ImageUtilities.loadImageIcon("org/netbeans/modules/btrace/editor/toolbar/resources/event.png", false)) {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        BTraceTask task = taskRef.get();
                        if (task != null) {
                            EventChooser chooser = EventChooser.getChooser(task.getNamedEvents());
                            DialogDescriptor dd = new DialogDescriptor(chooser, "Send Event");
                            Object result = DialogDisplayer.getDefault().notify(dd);
                            if (result == DialogDescriptor.OK_OPTION) {
                                task.sendEvent(chooser.getSelectedEvent());
                            }
                        }
                    }
                });
            }
            if (task.hasAnonymousEvents()) {
                final WeakReference<BTraceTask> taskRef = new WeakReference<>(task);
                actions.add(new AbstractAction("Send Anonymous Event", ImageUtilities.loadImageIcon("org/netbeans/modules/btrace/editor/toolbar/resources/event.png", false)) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        BTraceTask task = taskRef.get();
                        if (task != null) {
                            task.sendEvent();
                        }
                    }
                });
            }
        }
        task.addStateListener(new BTraceTask.StateListener() {
            @Override
            public void stateChanged(State state) {
                switch(state) {
                    case RUNNING: {
                        for(Action a : actions) {
                            a.setEnabled(true);
                        }
                        break;
                    }
                    case FAILED:
                    case FINISHED: {
                        task.removeStateListener(this);
                    }
                    default: {
                        for(Action a : actions) {
                            a.setEnabled(false);
                        }
                    }
                }
            }
        });
        return actions.toArray(new Action[actions.size()]);
    }
}
