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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.netbeans.modules.btrace.api.BTraceTask;
import org.netbeans.modules.btrace.jps.JpsProxy;
import org.netbeans.modules.btrace.jps.RunningVM;
import org.netbeans.modules.btrace.jps.ui.PIDSelectPanel;
import org.openide.awt.Mnemonics;
import org.openide.cookies.EditorCookie;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;

/**
 *
 * @author Jaroslav Bachorik
 */
//@EditorActionRegistration(
//    name = "btrace-select-pid",
//    mimeType = "text/x-java",
//    popupText = "#btrace_select_pid_not_formatted",
//    toolBarPosition=100
//)
public class PIDSelector extends AbstractToolbarAction {
    private static final String VM_COMBO_ITEM_TEXT = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_VmComboItemText"); //NOI18N
    final static public RunningVM NONE_VM = new RunningVM(-1, "", "", "", "") {
        @Override
        public String toString() {
            return "<none>";
        }
    };

    private JComponent toolbarPresenter = null;
    private Lookup.Result<BTraceTask> taskResult = null;

    private LookupListener ll = (LookupEvent ev) -> {
        updateEnablement();
    };

    public PIDSelector() {
        putValue(NAME, "Connect To:");
    }

    public PIDSelector(Context context) {
        super(context);
        putValue(NAME, "Connect To:");
        taskResult = context.lookupResult(BTraceTask.class);
        taskResult.addLookupListener(ll);
        updateEnablement();
    }

    @Override
    protected void doPerform() {
        // ignore
    }

    @Override
    protected AbstractToolbarAction newInstance(Context context) {
        return new PIDSelector(context);
    }

    @Override
    synchronized protected Component toolbarPresenterInstance() {
        if (toolbarPresenter == null) {
            toolbarPresenter = new ToolbarPresenter(getContext());
        }
        return toolbarPresenter;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            if (toolbarPresenter != null) {
                toolbarPresenter.setEnabled(enabled);
            }
            PIDSelector.super.setEnabled(enabled);
        });
    }

    private void updateEnablement() {
        setEnabled(taskResult.allInstances().isEmpty());
    }

    private static final class ToolbarPresenter extends JPanel {

        private final Context actionContext;
        private JComboBox combo;
        private JLabel comboLabel;
        private RunningVmsModel model;

        public ToolbarPresenter(final Context actionContext) {
            this.actionContext = actionContext;
            initComponents();
        }

        @Override
        public Dimension getMinimumSize() {
            Dimension dim = super.getMinimumSize();
            int minWidth = combo.getWidth() * 2;
            return new Dimension(minWidth, dim.height);
        }

        private void initComponents() {
            setLayout(new BorderLayout(4, 0));
            setBorder(new EmptyBorder(0, 4, 0, 4));
            setOpaque(false);
            setFocusTraversalPolicyProvider(true);
            setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {

                @Override
                public Component getDefaultComponent(Container aContainer) {
                    final EditorCookie ec = actionContext.lookup(
                            EditorCookie.class);
                    if (ec != null) {
                        JEditorPane[] panes = ec.getOpenedPanes();
                        if (panes != null) {
                            for (JEditorPane pane : panes) {
                                if (pane.isShowing()) {
                                    return pane;
                                }
                            }
                        }
                    }

                    return null;
                }
            });

            combo = new JComboBox();
            combo.addItemListener((ItemEvent e) -> {
                RunningVM vm = (RunningVM) combo.getSelectedItem();
                combo.setToolTipText(vm != null ? vm.toString() : null);
            });
            combo.setOpaque(false);
            model = new RunningVmsModel(actionContext);
            combo.setModel(model);
            combo.setRenderer(new VMRenderer());
            String accessibleName = NbBundle.getMessage(PIDSelector.class, "LBL_RunningVM");
            combo.getAccessibleContext().setAccessibleName(accessibleName);
            combo.getAccessibleContext().setAccessibleDescription(accessibleName);
            combo.setPreferredSize(new Dimension(Math.min(combo.getPreferredSize().width, 400), combo.getPreferredSize().height));

            comboLabel = new JLabel();
            Mnemonics.setLocalizedText(comboLabel, NbBundle.getMessage(PIDSelector.class, "LBL_SelectVM"));
            comboLabel.setOpaque(false);
            comboLabel.setLabelFor(combo);
//            comboLabel.setFont(comboLabel.getFont().deriveFont(Font.BOLD));

            add(comboLabel, BorderLayout.WEST);
            add(combo, BorderLayout.CENTER);
        }

        @Override
        public void setEnabled(boolean enabled) {
            combo.setEnabled(enabled);
            super.setEnabled(enabled);
        }
    }

    private static final class RunningVmsModel extends AbstractListModel implements ComboBoxModel {
        final private List<RunningVM> vmList; // must be ArrayList
        final private Object selectedVmLock = new Object();
        // @GuardedBy selectedVM
        private RunningVM selectedVM = null;
        private Context context;
        private final JpsProxy p = JpsProxy.getInstance();

        public RunningVmsModel(Context context) {
            this.context = context;
            vmList = new ArrayList<>();
            vmList.addAll(Arrays.asList(p.getRunningVMs()));
            vmList.add(0, NONE_VM);
            setSelectedItem(NONE_VM);
            fireContentsChanged(RunningVmsModel.this, 0, 0);
            
            p.addListener(new JpsProxy.Listener() {
                @Override
                public void onChange(final Set<RunningVM> started, final Set<RunningVM> terminated) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            vmList.removeAll(terminated);
                            vmList.addAll(started);
                            synchronized(selectedVmLock) {
                                if (!vmList.contains(selectedVM)) {
                                    setSelectedItem(NONE_VM);
                                }
                            }
                            fireContentsChanged(RunningVmsModel.this, 0, 0);
                        }
                    });
                }
            });
        }

        @Override
        public Object getElementAt(int index) {
            synchronized(vmList) {
                return vmList.get(index);
            }
        }

        @Override
        public int getSize() {
            synchronized(vmList) {
                return vmList.size();
            }
        }

        @Override
        public void setSelectedItem(final Object object) {
            synchronized(selectedVmLock) {
                if (selectedVM != null) {
                    context.removeInstance(selectedVM);
                }
                context.addInstance(object);

                selectedVM = (RunningVM) object;
                System.err.println(selectedVM);
            }
        }

        @Override
        public Object getSelectedItem() {
            synchronized(selectedVmLock) {
                return selectedVM;
            }
        }
    }

    private static final class VMRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof RunningVM) {
                String text = "";
                RunningVM vm = (RunningVM) value;
                if (vm == NONE_VM) {
                    text = vm.toString();
                } else {
                    String args = vm.getMainArgs();

                    if (args == null) {
                        args = ""; //NOI18N
                    } else {
                        args = " " + args; //NOI18N
                    }

                    text = MessageFormat.format(VM_COMBO_ITEM_TEXT, new Object[] { vm.getMainClass(), "" + vm.getPid() }); // NOI18N
                }
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            } else {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        }
    }
}
