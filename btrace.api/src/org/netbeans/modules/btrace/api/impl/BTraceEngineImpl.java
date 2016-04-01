/*
 * Copyright (c) 2010, 2016, Jaroslav Bachorik <jb@btrace.io>.
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
package org.netbeans.modules.btrace.api.impl;

import com.sun.btrace.client.Client;
import com.sun.btrace.comm.Command;
import static com.sun.btrace.comm.Command.ERROR;
import static com.sun.btrace.comm.Command.EXIT;
import static com.sun.btrace.comm.Command.RETRANSFORMATION_START;
import static com.sun.btrace.comm.Command.SUCCESS;
import com.sun.btrace.comm.RetransformationStartNotification;
import java.io.IOException;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import com.sun.btrace.comm.ErrorCommand;
import static java.lang.String.valueOf;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import java.lang.ref.WeakReference;
import static java.util.EnumSet.of;
import java.util.Iterator;
import java.util.ServiceLoader;
import static java.util.ServiceLoader.load;
import java.util.concurrent.ExecutorService;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import org.netbeans.modules.btrace.api.BTraceCompiler;
import org.netbeans.modules.btrace.api.BTraceEngine;
import org.netbeans.modules.btrace.api.BTraceTask;
import org.netbeans.modules.btrace.api.BTraceSettings;
import static org.netbeans.modules.btrace.api.BTraceTask.State.ACCEPTED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.COMPILED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.COMPILING;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FAILED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FINISHED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.INSTRUMENTING;
import static org.netbeans.modules.btrace.api.BTraceTask.State.RUNNING;
import org.netbeans.modules.btrace.spi.BTraceCompilerFactory;
import org.netbeans.modules.btrace.spi.BTraceSettingsProvider;
import org.netbeans.modules.btrace.spi.ClasspathProvider;
import static org.netbeans.modules.btrace.spi.ClasspathProvider.EMPTY;
import org.netbeans.modules.btrace.spi.OutputProvider;
import static org.netbeans.modules.btrace.spi.OutputProvider.DEFAULT;
import org.netbeans.modules.btrace.spi.PortLocator;
import org.netbeans.modules.btrace.spi.impl.BTraceCompilerFactoryImpl;
import org.netbeans.modules.btrace.spi.impl.BTraceSettingsProviderImpl;
import org.netbeans.modules.btrace.spi.impl.PortLocatorImpl;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceEngineImpl extends BTraceEngine {
    final private static Logger LOGGER = getLogger(BTraceEngineImpl.class.getName());

    /**
     * Basic state listener<br>
     * Makes it possible to intercept start/stop of a certain {@linkplain BTraceTask}
     */
    interface StateListener extends EventListener {
        /**
         * Called on task startup
         * @param task The task that has started up
         */
        void onTaskStart(BTraceTask task);
        /**
         * Called on task shutdown
         * @param task The task that has stopped
         */
        void onTaskStop(BTraceTask task);
    }

    private BTraceSettingsProvider settingsProvider;
    private BTraceCompilerFactory compilerFactory;
    private ClasspathProvider cpProvider;
    private PortLocator portLocator;
    private OutputProvider outputProvider;

    private Map<BTraceTask, Client> clientMap = new HashMap<>();

    final private Set<WeakReference<StateListener>> listeners = new HashSet<>();
    final private ExecutorService commQueue = newCachedThreadPool();

    public BTraceEngineImpl() {

        this.settingsProvider = initSettingsProvider();
        this.compilerFactory = initCompilerFactory();
        this.cpProvider = initClasspathProvider();
        this.portLocator = initPortLocator();
        this.outputProvider = initOutputProvider();
    }

    private static BTraceCompilerFactory initCompilerFactory() {
        ServiceLoader<BTraceCompilerFactory> loader = load(BTraceCompilerFactory.class);
        Iterator<BTraceCompilerFactory> iter = loader.iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        return new BTraceCompilerFactoryImpl();
    }

    private static ClasspathProvider initClasspathProvider() {
        ServiceLoader<ClasspathProvider> loader = load(ClasspathProvider.class);
        Iterator<ClasspathProvider> iter = loader.iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        return EMPTY;
    }

    private static BTraceSettingsProvider initSettingsProvider() {
        ServiceLoader<BTraceSettingsProvider> loader = load(BTraceSettingsProvider.class);
        Iterator<BTraceSettingsProvider> iter = loader.iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        return new BTraceSettingsProviderImpl();
    }

    private static PortLocator initPortLocator() {
        ServiceLoader<PortLocator> loader = load(PortLocator.class);
        Iterator<PortLocator> iter = loader.iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        return new PortLocatorImpl();
    }

    private static OutputProvider initOutputProvider() {
        ServiceLoader<OutputProvider> loader = load(OutputProvider.class);
        Iterator<OutputProvider> iter = loader.iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        return DEFAULT;
    }

    @Override
    public BTraceTask createTask(int pid) {
        return new BTraceTaskImpl(pid, this);
    }

    void addListener(StateListener listener) {
        synchronized(listeners) {
            listeners.add(new WeakReference<>(listener));
        }
    }

    void removeListener(StateListener listener) {
        synchronized(listeners) {
            for(Iterator<WeakReference<StateListener>> iter=listeners.iterator();iter.hasNext();) {
                WeakReference<StateListener> ref = iter.next();
                StateListener l = ref.get();
                if (l == null || l.equals(listener)) {
                    iter.remove();
                }
            }
        }
    }

    boolean start(final BTraceTask task) {
        LOGGER.finest("Starting BTrace task");

        boolean result = doStart(task);
        LOGGER.log(FINEST, "BTrace task {0}", result ? "started successfuly" : "failed");
        if (result) {
            fireOnTaskStart(task);
        }
        return result;
    }

    final private AtomicBoolean stopping = new AtomicBoolean(false);

    boolean stop(final BTraceTask task) {
        LOGGER.finest("Attempting to stop BTrace task");
        try {
            if (stopping.compareAndSet(false, true)) {
                LOGGER.finest("Stopping BTrace task");
                boolean result = doStop(task);
                LOGGER.log(FINEST, "BTrace task {0}", result ? "stopped successfuly" : "not stopped");
                if (result) {
                    fireOnTaskStop(task);
                }
                return result;
            }
            return true;
        } finally {
            stopping.set(false);
        }
    }

    private boolean doStart(BTraceTask task) {
        final AtomicBoolean result = new AtomicBoolean(false);
        final BTraceTaskImpl btrace = (BTraceTaskImpl) task;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final BTraceCompiler compiler = compilerFactory.newCompiler(btrace);
            btrace.setState(COMPILING);
            final byte[] bytecode = compiler.compile(btrace.getScript(), task.getClassPath(), outputProvider.getStdErr(task));
            if (bytecode.length == 0) {
                btrace.setState(FAILED);
                return false;
            }
            btrace.setState(COMPILED);

            LOGGER.log(FINEST, "Compiled the trace: {0} bytes", bytecode.length);
            commQueue.submit(new Runnable() {

                public void run() {
                    int  port = portLocator.getTaskPort(btrace);
                    LOGGER.log(FINEST, "BTrace agent listening on port {0}", port);
                    BTraceSettings settings = settingsProvider.getSettings();
                    final Client client = new Client(
                        port, ".", settings.isDebugMode(), true,
                        btrace.isUnsafe(), settings.isDumpClasses(),
                        settings.getDumpClassPath(),
                        settings.getStatsd()
                    );

                    try {
                        client.attach(valueOf(btrace.getPid()), compiler.getAgentJarPath(), compiler.getToolsJarPath(), null);
                        sleep(200); // give the server side time to initialize and open the port
                        client.submit(bytecode, new String[]{}, (Command cmd) -> {
                            LOGGER.log(FINEST, "Received command: {0}", cmd.toString());
                            switch (cmd.getType()) {
                                case SUCCESS: {
                                    if (btrace.getState() == COMPILED) {
                                        btrace.setState(ACCEPTED);
                                    } else if (of(INSTRUMENTING, ACCEPTED).contains(btrace.getState())) {
                                        btrace.setState(RUNNING);
                                        result.set(true);
                                        clientMap.put(btrace, client);
                                        latch.countDown();
                                    }
                                    break;
                                }
                                case EXIT: {
                                    btrace.setState(FINISHED);
                                    latch.countDown();
                                    stop(btrace);
                                    break;
                                }
                                case RETRANSFORMATION_START: {
                                    int numClasses = ((RetransformationStartNotification)cmd).getNumClasses();
                                    btrace.setInstrClasses(numClasses);
                                    btrace.setState(INSTRUMENTING);
                                    break;
                                }
                                case ERROR: {
                                    ((ErrorCommand)cmd).getCause().printStackTrace(outputProvider.getStdErr(btrace));
                                    btrace.setState(FAILED);
                                    latch.countDown();
                                    stop(btrace);
                                    break;
                                }
                                default:
                                    LOGGER.log(WARNING, "Unknown command: {0}", cmd);
                            }
                            btrace.dispatchCommand(cmd);
                        });
                    } catch (Exception e) {
                        LOGGER.log(FINE, e.getLocalizedMessage(), e);
                        result.set(false);
                        latch.countDown();
                    }
                }
            });
            latch.await();

        } catch (InterruptedException ex) {
            LOGGER.log(WARNING, null, ex);
        }
        return result.get();
    }

    private boolean doStop(BTraceTask task) {
        Client client = clientMap.remove(task);
        if (client != null) {
            try {
                client.sendExit(0);
                sleep(300);
                client.close();
            } catch (InterruptedException e) {
                currentThread().interrupt();
            } catch (IOException ex) {
                // ignore all IO related exception during the stop sequence
            }
        }
        return true;
    }

    void sendEvent(BTraceTaskImpl task) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.sendEvent();
            } catch (IOException ex) {
                LOGGER.log(SEVERE, null, ex);
            }
        }
    }

    void sendEvent(BTraceTaskImpl task, String eventName) {
        Client client = clientMap.get(task);
        if (client != null) {
            try {
                client.sendEvent(eventName);
            } catch (IOException ex) {
                LOGGER.log(SEVERE, null, ex);
            }
        }
    }

    ClasspathProvider getClasspathProvider() {
        return cpProvider;
    }

    private void fireOnTaskStart(BTraceTask task) {
        synchronized(listeners) {
            for(WeakReference<StateListener> ref : listeners) {
                StateListener l = ref.get();
                if (l != null) l.onTaskStart(task);
            }
        }
    }

    private void fireOnTaskStop(BTraceTask task) {
        synchronized(listeners) {
            for(WeakReference<StateListener> ref : listeners) {
                StateListener l = ref.get();
                if (l != null) l.onTaskStop(task);
            }
        }
    }
}
