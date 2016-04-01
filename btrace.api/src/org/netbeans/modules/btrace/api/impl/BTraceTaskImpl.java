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

import com.sun.btrace.comm.Command;
import static com.sun.btrace.comm.Command.ERROR;
import static com.sun.btrace.comm.Command.GRID_DATA;
import static com.sun.btrace.comm.Command.MESSAGE;
import static com.sun.btrace.comm.Command.NUMBER;
import static com.sun.btrace.comm.Command.NUMBER_MAP;
import static com.sun.btrace.comm.Command.RETRANSFORM_CLASS;
import static com.sun.btrace.comm.Command.STRING_MAP;
import java.io.File;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.btrace.comm.ErrorCommand;
import com.sun.btrace.comm.GridDataCommand;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.comm.NumberDataCommand;
import com.sun.btrace.comm.NumberMapDataCommand;
import com.sun.btrace.comm.RetransformClassNotification;
import com.sun.btrace.comm.StringMapDataCommand;
import static java.io.File.pathSeparator;
import static java.lang.Boolean.parseBoolean;
import static java.lang.System.out;
import static java.util.EnumSet.of;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.compile;
import org.netbeans.modules.btrace.api.BTraceEngine;
import org.netbeans.modules.btrace.api.BTraceTask;
import org.netbeans.modules.btrace.api.BTraceTask.MessageDispatcher;
import org.netbeans.modules.btrace.api.BTraceTask.State;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FAILED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.FINISHED;
import static org.netbeans.modules.btrace.api.BTraceTask.State.INSTRUMENTING;
import static org.netbeans.modules.btrace.api.BTraceTask.State.NEW;
import static org.netbeans.modules.btrace.api.BTraceTask.State.RUNNING;
import static org.netbeans.modules.btrace.api.BTraceTask.State.STARTING;
import org.openide.util.WeakListeners;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceTaskImpl extends BTraceTask implements BTraceEngineImpl.StateListener {
    final private static Pattern NAMED_EVENT_PATTERN = compile("@OnEvent\\s*\\(\\s*\\\"(\\w.*)\\\"\\s*\\)", MULTILINE);
    final private static Pattern ANONYMOUS_EVENT_PATTERN = compile("@OnEvent(?!\\s*\\()");
    final private static Pattern UNSAFE_PATTERN = compile("@BTrace\\s*\\(.*unsafe\\s*=\\s*(true|false).*\\)", MULTILINE);
    final private static Pattern NAME_ANNOTATION_PATTERN = compile("@BTrace\\s*\\(.*name\\s*=\\s*\"(.*)\".*\\)", MULTILINE);
    final private static Pattern NAME_PATTERN = compile("@BTrace.*?class\\s+(.*?)\\s+", MULTILINE);

    final private AtomicReference<State> currentState = new AtomicReference<>(NEW);
    final private Set<StateListener> stateListeners = new HashSet<>();
    final private Set<MessageDispatcher> messageDispatchers = new HashSet<>();

    final private static ExecutorService dispatcher = newSingleThreadExecutor();

    private PrintWriter consoleWriter = new PrintWriter(out, true);

    private String script;
    private int numInstrClasses;
    private boolean unsafe;

    final private BTraceEngineImpl engine;

    final private int pid;

    public BTraceTaskImpl(int pid, BTraceEngine engine) {
        this.pid = pid;
        this.engine = (BTraceEngineImpl)engine;
        this.engine.addListener(this);
    }

    @Override
    public String getScript() {
        return script != null ? script : "";
    }

    @Override
    public String getName() {
        Matcher m = NAME_ANNOTATION_PATTERN.matcher(getScript());
        if (m.find()) {
            return m.group(1);
        } else {
            m = NAME_PATTERN.matcher(getScript());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    @Override
    public void setScript(String newValue) {
        script = newValue;
        Matcher m = UNSAFE_PATTERN.matcher(getScript());
        if (m.find()) {
            unsafe = parseBoolean(m.group(1));
        }
    }

    @Override
    public void sendEvent(String event) {
        engine.sendEvent(this, event);
    }

    @Override
    public void sendEvent() {
        engine.sendEvent(this);
    }

    @Override
    public Set<String> getNamedEvents() {
        Set<String> events = new HashSet<>();
        Matcher matcher = NAMED_EVENT_PATTERN.matcher(getScript());
        while (matcher.find()) {
            events.add(matcher.group(1));
        }
        return events;
    }

    @Override
    public boolean hasAnonymousEvents() {
        Matcher matcher = ANONYMOUS_EVENT_PATTERN.matcher(getScript());
        return matcher.find();
    }

    @Override
    public boolean hasEvents() {
        return getScript() != null && getScript().contains("@OnEvent");
    }

    public int getPid() {
        return pid;
    }

    /**
     * Property getter
     * @return Returns the current state
     */
    protected State getState() {
        return currentState.get();
    }

    @Override
    public int getInstrClasses() {
        return of(INSTRUMENTING, RUNNING).contains(getState()) ? numInstrClasses : -1;
    }

    /**
     * Listener management (can use {@linkplain WeakListeners} to create a new listener)
     * @param listener {@linkplain StateListener} instance to add
     */
    @Override
    public void addStateListener(StateListener listener) {
        synchronized (stateListeners) {
            stateListeners.add(listener);
        }
    }

    /**
     * Listener management
     * @param listener {@linkplain StateListener} instance to remove
     */
    @Override
    public void removeStateListener(StateListener listener) {
        synchronized (stateListeners) {
            stateListeners.remove(listener);
        }
    }

    /**
     * Dispatcher management (can use {@linkplain WeakListeners} to create a new listener)
     * @param dispatcher {@linkplain StateListener} instance to add
     */
    @Override
    public void addMessageDispatcher(MessageDispatcher dispatcher) {
        synchronized (messageDispatchers) {
            messageDispatchers.add(dispatcher);
        }
    }

    /**
     * Dispatcher management
     * @param dispatcher {@linkplain StateListener} instance to remove
     */
    @Override
    public void removeMessageDispatcher(MessageDispatcher dispatcher) {
        synchronized (messageDispatchers) {
            messageDispatchers.remove(dispatcher);
        }
    }

    /**
     * Starts the injected code using the attached {@linkplain BTraceEngine}
     */
    @Override
    public void start() {
        setState(STARTING);
        if (!engine.start(this)) {
            setState(FAILED);
        }
    }

    /**
     * Stops the injected code running on the attached {@linkplain BTraceEngine}
     */
    @Override
    public void stop() {
        if (getState() == RUNNING) {
            if (!engine.stop(this)) {
                setState(RUNNING);
            }
        }
    }

    /**
     * @see BTraceEngine.StateListener#onTaskStart(net.java.visualvm.btrace.api.BTraceTask)
     */
    @Override
    public void onTaskStart(BTraceTask task) {
        if (task.equals(this)) {
            setState(RUNNING);
        }
    }

    /**
     * @see BTraceEngine.StateListener#onTaskStop(net.java.visualvm.btrace.api.BTraceTask)
     */
    @Override
    public void onTaskStop(BTraceTask task) {
        if (task.equals(this)) {
            setState(FINISHED);
        }
    }

    @Override
    public String getClassPath() {
        StringBuilder sb = new StringBuilder();
        for(String cpEntry : engine.getClasspathProvider().getClasspath(this)) {
            if (sb.length() > 0) {
                sb.append(pathSeparator);
            }
            sb.append(cpEntry);
        }
        return sb.toString();
    }

    @Override
    public boolean isUnsafe() {
        return unsafe;
    }

    void setState(State newValue) {
        currentState.set(newValue);
        fireStateChange();
    }

    void setInstrClasses(int value) {
        numInstrClasses = value;
    }

    private void fireStateChange() {
        Set<StateListener> toProcess = null;

        synchronized (stateListeners) {
             toProcess = new HashSet<>(stateListeners);
        }
        for (StateListener listener : toProcess) {
            listener.stateChanged(getState());
        }
    }

    void dispatchCommand(final Command cmd) {
        final Set<MessageDispatcher> dispatchingSet = new HashSet<>();
        synchronized(messageDispatchers) {
            dispatchingSet.addAll(messageDispatchers);
        }
        dispatcher.submit(() -> {
            for(MessageDispatcher listener : dispatchingSet) {
                switch (cmd.getType()) {
                    case MESSAGE: {
                        listener.onPrintMessage(((MessageCommand)cmd).getMessage());
                        break;
                    }
                    case RETRANSFORM_CLASS: {
                        listener.onClassInstrumented(((RetransformClassNotification)cmd).getClassName());
                        break;
                    }
                    case NUMBER: {
                        NumberDataCommand ndc = (NumberDataCommand)cmd;
                        listener.onNumberMessage(ndc.getName(), ndc.getValue());
                        break;
                    }
                    case NUMBER_MAP: {
                        NumberMapDataCommand nmdc = (NumberMapDataCommand)cmd;
                        listener.onNumberMap(nmdc.getName(), nmdc.getData());
                        break;
                    }
                    case STRING_MAP: {
                        StringMapDataCommand smdc = (StringMapDataCommand)cmd;
                        listener.onStringMap(smdc.getName(), smdc.getData());
                        break;
                    }
                    case GRID_DATA: {
                        GridDataCommand gdc = (GridDataCommand)cmd;
                        listener.onGrid(gdc.getName(), gdc.getData());
                        break;
                    }
                    case ERROR: {
                        ErrorCommand ec = (ErrorCommand)cmd;
                        listener.onError(ec.getCause());
                        break;
                    }
                }
            }
        });
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BTraceTaskImpl other = (BTraceTaskImpl) obj;
        if (this.pid != other.pid) {
            return false;
        }
        if (this.getName() != other.getName() && (this.getName() == null || !this.getName().equals(other.getName()))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + pid;
        hash = 19 * hash + (this.getName() != null ? this.getName().hashCode() : 0);
        return hash;
    }
}
