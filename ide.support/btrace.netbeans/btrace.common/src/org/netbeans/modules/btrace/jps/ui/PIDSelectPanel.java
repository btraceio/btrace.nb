/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c)  2016, Jaroslav Bachorik <j.bachorik@btrace.io>. All rights reserved.
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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

package org.netbeans.modules.btrace.jps.ui;

import java.awt.*;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.EAST;
import static java.awt.BorderLayout.NORTH;
import static java.awt.Font.BOLD;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.REMAINDER;
import static java.awt.GridBagConstraints.WEST;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import static java.lang.Math.max;
import static java.lang.System.arraycopy;
import static java.text.MessageFormat.format;
import javax.swing.*;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.SwingUtilities.invokeLater;
import org.netbeans.modules.btrace.jps.JpsProxy;
import org.netbeans.modules.btrace.jps.RunningVM;
import org.openide.DialogDescriptor;
import static org.openide.DialogDescriptor.BOTTOM_ALIGN;
import org.openide.DialogDisplayer;
import static org.openide.NotifyDescriptor.CANCEL_OPTION;
import static org.openide.util.NbBundle.getMessage;
import static org.openide.util.RequestProcessor.getDefault;


/**
 * A panel that allows to select a process PID from a combo box of all running processes
 *
 * @author Tomas Hurka
 * @author Ian Formanek
 */
public final class PIDSelectPanel extends JPanel implements ActionListener {
    private final JpsProxy p = JpsProxy.getInstance();
    
    //~ Inner Classes ------------------------------------------------------------------------------------------------------------

    private static class PIDComboRenderer extends DefaultListCellRenderer {
        //~ Methods --------------------------------------------------------------------------------------------------------------

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            if (value instanceof RunningVM) {
                RunningVM vm = (RunningVM) value;
                String args = vm.getMainArgs();

                if (args == null) {
                    args = ""; //NOI18N
                } else {
                    args = " " + args; //NOI18N
                }

                String text = format(VM_COMBO_ITEM_TEXT, new Object[] { vm.getMainClass(), "" + vm.getPid() }); // NOI18N

                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            } else {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        }
    }

    //~ Static fields/initializers -----------------------------------------------------------------------------------------------

    // -----
    // I18N String constants
    private static final String REFRESH_BUTTON_NAME = getMessage(PIDSelectPanel.class, "PIDSelectPanel_RefreshButtonName"); //NOI18N
    private static final String PID_LABEL_TEXT = getMessage(PIDSelectPanel.class, "PIDSelectPanel_PidLabelText"); //NOI18N
    private static final String MAIN_CLASS_LABEL_TEXT = getMessage(PIDSelectPanel.class,
                                                                            "PIDSelectPanel_MainClassLabelText"); //NOI18N
    private static final String ARGUMENTS_LABEL_TEXT = getMessage(PIDSelectPanel.class,
                                                                           "PIDSelectPanel_ArgumentsLabelText"); //NOI18N
    private static final String VM_ARGUMENTS_LABEL_TEXT = getMessage(PIDSelectPanel.class,
                                                                              "PIDSelectPanel_VmArgumentsLabelText"); //NOI18N
    private static final String VM_FLAGS_LABEL_TEXT = getMessage(PIDSelectPanel.class, "PIDSelectPanel_VmFlagsLabelText"); //NOI18N
    private static final String VM_COMBO_ITEM_TEXT = getMessage(PIDSelectPanel.class, "PIDSelectPanel_VmComboItemText"); //NOI18N
    private static final String PROCESSES_LIST_ITEM_TEXT = getMessage(PIDSelectPanel.class,
                                                                               "PIDSelectPanel_ProcessesListItemText"); //NOI18N
    private static final String ERROR_GETTING_PROCESSES_ITEM_TEXT = getMessage(PIDSelectPanel.class,
                                                                                        "PIDSelectPanel_ErrorGettingProcessesItemText"); //NOI18N
    private static final String NO_PROCESSES_ITEM_TEXT = getMessage(PIDSelectPanel.class,
                                                                             "PIDSelectPanel_NoProcessesItemText"); //NOI18N
    private static final String SELECT_PROCESS_ITEM_TEXT = getMessage(PIDSelectPanel.class,
                                                                               "PIDSelectPanel_SelectProcessItemText"); //NOI18N
    private static final String OK_BUTTON_NAME = getMessage(PIDSelectPanel.class, "PIDSelectPanel_OkButtonName"); //NOI18N
    private static final String SELECT_PROCESS_DIALOG_CAPTION = getMessage(PIDSelectPanel.class,
                                                                                    "PIDSelectPanel_SelectProcessDialogCaption"); //NOI18N
    private static final String COMBO_ACCESS_NAME = getMessage(PIDSelectPanel.class, "PIDSelectPanel_ComboAccessName"); //NOI18N
    private static final String COMBO_ACCESS_DESCR = getMessage(PIDSelectPanel.class, "PIDSelectPanel_ComboAccessDescr"); //NOI18N
    private static final String BUTTON_ACCESS_DESCR = getMessage(PIDSelectPanel.class, "PIDSelectPanel_ButtonAccessDescr"); //NOI18N
                                                                                                                                     // -----
    private static final int MAX_WIDTH = 500;

    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    private final JButton button;
    private final JButton okButton;
    private JComboBox<Object> combo;
    private final JLabel argumentsLabel;
    private final JLabel mainClassLabel;
    private final JLabel pidLabel;
    private final JLabel vmArgumentsLabel;
    private final JLabel vmFlagsLabel;

    //~ Constructors -------------------------------------------------------------------------------------------------------------

    public PIDSelectPanel(JButton okButton) {
        this.okButton = okButton;

        combo = new JComboBox<>();
        button = new JButton(REFRESH_BUTTON_NAME);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new GridBagLayout());

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.insets = new Insets(3, 5, 0, 0);
        labelGbc.anchor = WEST;

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.weightx = 1.0;
        valueGbc.fill = HORIZONTAL;
        valueGbc.insets = new Insets(3, 5, 0, 5);
        valueGbc.gridwidth = REMAINDER;
        valueGbc.anchor = WEST;

        JLabel l;

        l = new JLabel(PID_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(pidLabel = new JLabel(), valueGbc);
        l = new JLabel(MAIN_CLASS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(mainClassLabel = new JLabel(), valueGbc);
        l = new JLabel(ARGUMENTS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(argumentsLabel = new JLabel(), valueGbc);
        l = new JLabel(VM_ARGUMENTS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(vmArgumentsLabel = new JLabel(), valueGbc);
        l = new JLabel(VM_FLAGS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(vmFlagsLabel = new JLabel(), valueGbc);

        combo.setRenderer(new PIDComboRenderer());
        combo.getAccessibleContext().setAccessibleName(COMBO_ACCESS_NAME);
        combo.getAccessibleContext().setAccessibleDescription(COMBO_ACCESS_DESCR);

        button.getAccessibleContext().setAccessibleDescription(BUTTON_ACCESS_DESCR);

        setBorder(createEmptyBorder(12, 12, 12, 12));
        setLayout(new BorderLayout(0, 10));

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BorderLayout(5, 0));

        northPanel.add(combo, CENTER);
        northPanel.add(button, EAST);

        add(northPanel, NORTH);
        add(infoPanel, CENTER);

        okButton.setEnabled(false);

        refreshCombo();

        button.addActionListener(this);
        combo.addActionListener(this);
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public int getPID() {
        Object sel = combo.getSelectedItem();

        if ((sel != null) && sel instanceof RunningVM) {
            return ((RunningVM) sel).getPid();
        }

        return -1;
    }

    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();

        return new Dimension(max(d.width, MAX_WIDTH), d.height);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == button) {
            refreshCombo();
        } else if (e.getSource() == combo) {
            okButton.setEnabled(combo.getSelectedItem() instanceof RunningVM);
            updateInfo();
        }
    }

    public static int selectPID() {
        JButton okButton = new JButton(OK_BUTTON_NAME);
        PIDSelectPanel pidSelect = new PIDSelectPanel(okButton);

        DialogDescriptor dd = new DialogDescriptor(pidSelect, SELECT_PROCESS_DIALOG_CAPTION, true,
                                                   new Object[] { okButton, CANCEL_OPTION}, okButton, BOTTOM_ALIGN, null, null);
        Dialog d = DialogDisplayer.getDefault().createDialog(dd);
        d.setVisible(true);

        if (dd.getValue() == okButton) {
            return pidSelect.getPID();
        } else {
            return -1;
        }
    }

    private final Runnable comboRefresher = new Runnable() {
        @Override
        public void run() {
            final RunningVM[] vms = p.getRunningVMs();
            final Object[] ar = new Object[((vms == null) ? 0 : vms.length) + 1];

            if (vms == null) {
                ar[0] = ERROR_GETTING_PROCESSES_ITEM_TEXT;
            } else if (vms.length == 0) {
                ar[0] = NO_PROCESSES_ITEM_TEXT;
            } else {
                ar[0] = SELECT_PROCESS_ITEM_TEXT;
                arraycopy(vms, 0, ar, 1, vms.length);
            }
            invokeLater(() -> {
                combo.setEnabled(true);
                combo.setModel(new DefaultComboBoxModel<>(ar));
                updateInfo();
            });
        }
    };

    private void refreshCombo() {
        okButton.setEnabled(false);
        combo.setEnabled(false);
        combo.setModel(new DefaultComboBoxModel<>(new Object[] { PROCESSES_LIST_ITEM_TEXT }));
        getDefault().post(comboRefresher);
    }

    private void updateInfo() {
        Object sel = combo.getSelectedItem();

        if ((sel != null) && sel instanceof RunningVM) {
            RunningVM vm = (RunningVM) sel;
            pidLabel.setText("" + vm.getPid()); //NOI18N
            mainClassLabel.setText(vm.getMainClass());
            argumentsLabel.setText(vm.getMainArgs());
            vmArgumentsLabel.setText(vm.getVMArgs());
            vmFlagsLabel.setText(vm.getVMFlags());
        } else {
            pidLabel.setText(""); //NOI18N
            mainClassLabel.setText(""); //NOI18N
            argumentsLabel.setText(""); //NOI18N
            vmArgumentsLabel.setText(""); //NOI18N
            vmFlagsLabel.setText(""); //NOI18N
        }
    }
}
