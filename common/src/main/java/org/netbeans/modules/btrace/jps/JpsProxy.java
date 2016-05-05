/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
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

package org.netbeans.modules.btrace.jps;

import static java.lang.String.valueOf;
import static java.lang.System.out;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import static java.util.logging.Logger.getLogger;
import sun.jvmstat.monitor.*;
import static sun.jvmstat.monitor.MonitoredHost.getMonitoredHost;
import static sun.jvmstat.monitor.MonitoredVmUtil.commandLine;
import static sun.jvmstat.monitor.MonitoredVmUtil.jvmArgs;
import static sun.jvmstat.monitor.MonitoredVmUtil.jvmFlags;
import static sun.jvmstat.monitor.MonitoredVmUtil.mainArgs;
import static sun.jvmstat.monitor.MonitoredVmUtil.mainClass;
import sun.jvmstat.monitor.event.HostEvent;
import sun.jvmstat.monitor.event.HostListener;
import sun.jvmstat.monitor.event.VmStatusChangeEvent;


/**
 * This class is based on "jvmps" class from jvmps 2.0 written by Brian Doherty.
 * It provides functionality to identify all the JVMs currently running on the local machine.
 * Comments starting with //// are original comments from Brian.
 *
 * @author Tomas Hurka
 * @author Misha Dmitriev
 */
public class JpsProxy {
    public static interface Listener {
        void onChange(Set<RunningVM> started, Set<RunningVM> terminated);
    }
    
    private static final Logger LOGGER = getLogger(JpsProxy.class.getName());
    private final MonitoredHost host;
    private final String selfName;
    
    private final Set<RunningVM> vms = new HashSet<>();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Map<Integer, RunningVM> pidMap = new HashMap<>();
    
    private static final class Singleton {
        private final static JpsProxy INSTANCE = new JpsProxy();
    }
    
    public static JpsProxy getInstance() {
        return Singleton.INSTANCE;
    }
    
    private JpsProxy() {
        selfName = getRuntimeMXBean().getName();
        MonitoredHost monitoredHost = null;
        try {
            HostIdentifier hostId = new HostIdentifier((String)null);
            monitoredHost = getMonitoredHost(hostId);
            synchronized(vms) {
                addVms(toVms(monitoredHost.activeVms(), monitoredHost));
            }
            
            monitoredHost.addHostListener(new HostListener() {
                private boolean init = true;
                @Override
                public void vmStatusChanged(VmStatusChangeEvent vsce) {
                    if (init) {
                        init = false;
                        return;
                    }
                    Set<RunningVM> started = toVms((Set<Integer>)vsce.getStarted());
                    Set<RunningVM> terminated = toVms((Set<Integer>)vsce.getTerminated());
                    
                    for(Listener l : listeners) {
                        l.onChange(started, terminated);
                    }
                    synchronized(vms) {
                        addVms(started);
                        vms.removeAll(terminated);
                    }
                }

                @Override
                public void disconnected(HostEvent he) {
                    // ignore
                }
            });
        } catch (MonitorException e) {
            String report = "in jvmps, got MonitorException"; // NOI18N

            if (e.getMessage() != null) {
                report += (" with message + " + e.getMessage()); // NOI18N
            }

            LOGGER.warning(report);
        } catch (URISyntaxException e) {
            // ignore

        }
        host = monitoredHost;
    }
    
    private void addVms(Collection<RunningVM> rVms) {
        synchronized(vms) {
            vms.addAll(rVms);
            for(RunningVM r : rVms) {
                pidMap.put(r.getPid(), r);
            }
        }
    }
    
    private void removeVms(Collection<RunningVM> rVms) {
        synchronized(vms) {
            vms.removeAll(rVms);
            for(RunningVM r : rVms) {
                pidMap.remove(r.getPid());
            }
        }
    }
    
    private Set<RunningVM> toVms(Collection<Integer> pids) {
        return toVms(pids, host);
    }
    
    private Set<RunningVM> toVms(Collection<Integer> pids, MonitoredHost mHost) {
        Set<RunningVM> vmSet = new HashSet<>();
        for (int lvmid : pids) {
            if (selfName.startsWith(lvmid + "@")) { // myself
                continue;
            }
            
            synchronized(vms) {
                RunningVM rvm = pidMap.get(lvmid);
                if (rvm != null) {
                    vmSet.add(rvm);
                    continue;
                }
            }

            VmIdentifier id = null;
            MonitoredVm vm = null;
            String uriString = "//" + lvmid + "?mode=r"; // NOI18N

            try {
                id = new VmIdentifier(uriString);
                vm = mHost.getMonitoredVm(id, 0);
            } catch (URISyntaxException e) {
                //// this error should not occur as we are creating our own VMIdentifiers above based on a validated HostIdentifier.
                //// This would be an unexpected condition.
                LOGGER.log(WARNING, "in jvmps, detected malformed VM Identifier: {0}; ignored", uriString); // NOI18N

                continue;
            } catch (MonitorException e) {
                out.println("Ex " + e.getMessage());
                e.printStackTrace();

                //// it's possible that from the time we acquired the list of available jvms that a jvm has terminated. Therefore, it is
                //// best just to ignore this error.
                continue;
            } catch (Exception e) {
                //// certain types of errors, such as access acceptions, can be encountered when attaching to a jvm.
                //// These are reported as exceptions, not as some subclass of security exception.

                //// FIXME - we should probably have some provision for logging these types of errors, or possibly just print out the
                //// the Java Virtual Machine lvmid in a finally clause: System.out.println(String.valueOf(lvmid));
                LOGGER.log(WARNING, "in jvmps, for VM = {0} got exception: {1}", new Object[]{valueOf(lvmid), e}); // NOI18N

                continue;
            }

            if (!isAttachable(vm)) {
                continue;
            }
            
            try {
                String cmdString = commandLine(vm);
                String mainClass = mainClass(vm, true);
                String mainArgs = mainArgs(vm);
                String vmArgs = jvmArgs(vm);
                String vmFlags = jvmFlags(vm);
                RunningVM rvm = new RunningVM(lvmid, vmFlags, vmArgs, mainClass, mainArgs);
                synchronized(vms) {
                    pidMap.put(rvm.getPid(), rvm);
                }
                vmSet.add(rvm);
            } catch (MonitorException e) {
                String report = "in jvmps, got MonitorException"; // NOI18N

                if (e.getMessage() != null) {
                    report += (" with message + " + e.getMessage()); // NOI18N
                }

                LOGGER.warning(report);
            }
        }
        return vmSet;
    }
    
    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public void addListener(Listener l) {
        listeners.add(l);
    }
    
    public void removeListener(Listener l) {
        listeners.remove(l);
    }
    
    /** Returns the array of records for all running VMs capable of dynamic attach (JDK 1.6 and newer)*/
    public RunningVM[] getRunningVMs() {
        List<RunningVM> vret;
        synchronized(vms) {
            vret = new ArrayList<>(vms);
        }
        return vret.toArray(new RunningVM[vret.size()]);
    }

    // invoke MonitoredVmUtil.isAttachable(MonitoredVm vm) using reflection (JDK 6 only code)
    private static Method monitoredVmUtil_isAttachable;

    static {
        try {
            monitoredVmUtil_isAttachable = MonitoredVmUtil.class.getMethod("isAttachable",new Class<?>[]{MonitoredVm.class}); // NOI18N
        } catch (SecurityException | NoSuchMethodException ex) {
            ex.printStackTrace();
        }
    }

    private static boolean isAttachable(MonitoredVm vm) {
        Object ret;
        try {
            ret = monitoredVmUtil_isAttachable.invoke(null, new Object[] {vm});
            if (ret instanceof Boolean) {
                return ((Boolean)ret);
            }
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException ex) {
            ex.printStackTrace();
        }
        return false;
    }
}
