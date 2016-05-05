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

package org.netbeans.modules.btrace.editor.toolbar;

import java.awt.Component;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.swing.ButtonModel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.modules.btrace.api.BTraceEngine;
import org.netbeans.modules.btrace.api.BTraceTask;
import org.netbeans.modules.btrace.api.BTraceTask.State;
import static org.netbeans.modules.btrace.api.BTraceTask.State.COMPILED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.COMPILING;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FAILED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FINISHED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.INSTRUMENTING;
import static org.netbeans.modules.btrace.api.BTraceTask.State.RUNNING;
import static org.netbeans.modules.btrace.api.BTraceTask.State.STARTING;
import org.netbeans.modules.btrace.editor.console.OutputSupport;
import org.netbeans.modules.btrace.jps.RunningVM;
import org.openide.cookies.EditorCookie;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.RequestProcessor;
import org.openide.windows.OutputWriter;

/**
 *
 * @author Jaroslav Bachorik
 */
//@EditorActionRegistration(
//            name = "btrace-deploy",
//            mimeType = "text/x-java",
//            popupText = "#btrace_deploy_not_formatted",
//            toolBarPosition=200
//    )
public class DeployAction extends AbstractToolbarAction {
    final private static Logger LOG = Logger.getLogger(DeployAction.class.getName());

    private JToggleButton presenter = null;
    private Lookup.Result<RunningVM> selectedVm = null;

    final static private BTraceEngine engine = BTraceEngine.newInstance();

    private final LookupListener enablementListener = (LookupEvent ev) -> {
        updateEnablement();
    };

    public DeployAction() {
        putValue(NAME, null);
        putValue(SMALL_ICON, ImageUtilities.loadImageIcon("org/netbeans/modules/btrace/editor/toolbar/resources/startScript.png", enabled));
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("control shift X"));
        putValue(ACTION_COMMAND_KEY, "btrace-deploy");
        putValue(SHORT_DESCRIPTION, "Deploy Script");
    }

    private DeployAction(Context context) {
        super(context);
        putValue(NAME, null);
        putValue(SMALL_ICON, ImageUtilities.loadImageIcon("org/netbeans/modules/btrace/editor/toolbar/resources/startScript.png", enabled));
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("control shift X"));
        putValue(ACTION_COMMAND_KEY, "btrace-deploy");
        putValue(SHORT_DESCRIPTION, "Deploy Script");
        selectedVm = context.lookupResult(RunningVM.class);
        selectedVm.addLookupListener(enablementListener);
        updateEnablement();
    }

    private void updateEnablement() {
        SwingUtilities.invokeLater(() -> {
            Collection<? extends RunningVM> running = selectedVm.allInstances();
            if (running.isEmpty()) {
                setEnabled(false);
            } else {
                RunningVM rvm = running.iterator().next();
                setEnabled(rvm != PIDSelector.NONE_VM);
            }
        });
    }

    @Override
    synchronized protected Component toolbarPresenterInstance() {
        if (presenter == null) {
            presenter = new MyGaGaButton();
            presenter.putClientProperty("hideActionText", Boolean.TRUE); //NOI18N
            presenter.setAction(this);
        }
        return presenter;
    }

    @Override
    protected void doPerform() {
        setEnabled(false); // temporarily disable the action
        RequestProcessor.getDefault().post(new Runnable() {

            @Override
            public void run() {
                final ProgressHandle[] ph = new ProgressHandle[1];

                BTraceTask existingTask = getContext().lookup(BTraceTask.class);
                if (existingTask != null) {
                    existingTask.stop();
                    return;
                }

                RunningVM vm = selectedVm.allInstances().iterator().next();
                final BTraceTask task = engine.createTask(vm.getPid());
                String script = getScript();
                if (script != null) {
                    task.setScript(script);
                    final PrintWriter err = OutputSupport.getInstance().getStdErr(task);
                    final PrintWriter out = OutputSupport.getInstance().getStdOut(task);

                    task.addMessageDispatcher(new BTraceTask.MessageDispatcher() {
                        int classCounter = 0;

                        @Override
                        public void onPrintMessage(String message) {
                            out.print(message);
                        }

                        @Override
                        public void onNumberMap(String name, Map<String, ? extends Number> data) {
                            out.println("##### " + name + "#####");
                            for(Map.Entry<String, ? extends Number> entry : data.entrySet()) {
                                out.println(entry.getKey() + " = " + entry.getValue());
                            }
                        }

                        @Override
                        public void onStringMap(String name, Map<String, String> data) {
                            out.println("##### " + name + " #####");
                            for(Map.Entry<String, String> entry : data.entrySet()) {
                                out.println(entry.getKey() + " = " + entry.getValue());
                            }
                        }

                        @Override
                        public void onGrid(String name, List<Object[]> data) {
                            out.println("##### " + name + "#####");
                            for(Object[] row : data) {
                                for(Object d : row) {
                                    out.print(d + "\t");
                                }
                                out.println();
                            }
                        }

                        @Override
                        public void onClassInstrumented(String name) {
                            if (ph[0] != null) {
                                ph[0].progress(++classCounter);
                            }
                        }

                        @Override
                        public void onError(Throwable cause) {
                            cause.printStackTrace(out);
                        }
                    });
                    getContext().addInstance(task);
                    task.addStateListener(new BTraceTask.StateListener() {
                        private final AtomicBoolean startupNotified = new AtomicBoolean();
                        private BTraceTask.State previousState = BTraceTask.State.NEW;

                        @Override
                        public void stateChanged(State state) {
                            Calendar calendar = Calendar.getInstance();
                            switch (state) {
                                case STARTING: {
                                    startupNotified.set(false);
                                    if (out instanceof OutputWriter) {
                                        try {
                                            ((OutputWriter)out).reset();
                                        } catch (IOException e) {
                                            // ignore
                                        }
                                    }
                                    ph[0] = ProgressHandleFactory.createHandle("Starting task");
                                    ph[0].start();
                                    break;
                                }
                                case COMPILING: {
                                    if (ph[0] != null) {
                                        ph[0].setDisplayName("Compiling script");
                                    }
                                    break;
                                }
                                case COMPILED: {
                                    break;
                                }
                                case INSTRUMENTING: {
                                    if (ph[0] != null) {
                                        ph[0].setDisplayName("Instrumenting");
                                    } else {
                                        ph[0] = ProgressHandleFactory.createHandle("Instrumenting");
                                        ph[0].start();
                                    }
                                    ph[0].switchToDeterminate(task.getInstrClasses());
                                    setEnabled(false);
                                    break;
                                }
                                case FAILED: {
                                    if (ph[0] != null) {
                                        ph[0].finish();
                                        ph[0] = null;
                                    }
                                    err.println("Task failed to start");
                                    setEnabled(true);
                                    ((JToggleButton)toolbarPresenterInstance()).setEnabled(true);
                                    ((JToggleButton)toolbarPresenterInstance()).setSelected(false);
                                    getContext().removeInstance(task);
                                    task.removeStateListener(this);
                                    break;
                                }
                                case FINISHED: {
                                    if (ph[0] != null) {
                                        ph[0].finish();
                                        ph[0] = null;
                                    }
                                    ((JToggleButton)toolbarPresenterInstance()).setSelected(false);
                                    getContext().removeInstance(task);
                                    err.println("(" + DateFormat.getTimeInstance().format(calendar.getTime()) + ") Task finished");
                                    setEnabled(true);
                                    task.removeStateListener(this);
                                    break;
                                }
                                case RUNNING: {
                                    if (ph[0] != null) {
                                        ph[0].finish();
                                        ph[0] = null;
                                    }
                                    setEnabled(true);
                                    ((JToggleButton)toolbarPresenterInstance()).setSelected(true);
                                    if (startupNotified.compareAndSet(false, true)) {
                                        err.println("(" + DateFormat.getTimeInstance().format(calendar.getTime()) + ") Task started");
                                    }
                                    break;
                                }
                            }
                            previousState = state;
                        }
                    });
                    task.start();
                }
            }
        });
    }

    @Override
    protected AbstractToolbarAction newInstance(Context context) {
        return new DeployAction(context);
    }

    private String getScript() {
        try {
            EditorCookie ec = getContext().lookup(EditorCookie.class);
            if (ec != null) {
                Document d = ec.getDocument();
                if (d != null) {
                    return d.getText(0, d.getLength());
                }
            }
        } catch (BadLocationException e) {
        }
        return null;
    }

    private static final class MyGaGaButton extends JToggleButton implements ChangeListener {

        public MyGaGaButton() {

        }

        @Override
        public void setModel(ButtonModel model) {
            ButtonModel oldModel = getModel();
            if (oldModel != null) {
                oldModel.removeChangeListener(this);
            }

            super.setModel(model);

            ButtonModel newModel = getModel();
            if (newModel != null) {
                newModel.addChangeListener(this);
            }

            stateChanged(null);
        }

        public void stateChanged(ChangeEvent evt) {
            boolean selected = isSelected();
            super.setContentAreaFilled(selected);
            super.setBorderPainted(selected);
        }

        @Override
        public void setBorderPainted(boolean arg0) {
            if (!isSelected()) {
                super.setBorderPainted(arg0);
            }
        }

        @Override
        public void setContentAreaFilled(boolean arg0) {
            if (!isSelected()) {
                super.setContentAreaFilled(arg0);
            }
        }
    }
}
