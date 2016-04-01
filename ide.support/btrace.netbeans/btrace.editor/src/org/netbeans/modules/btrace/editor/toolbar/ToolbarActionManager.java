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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ToolbarActionManager {
    final private static class Singleton {
        final private static ToolbarActionManager INSTANCE = new ToolbarActionManager();
    }

    final private Map<EditorCookie, Collection<AbstractToolbarAction>> actionMap = new WeakHashMap<>();

    private ToolbarActionManager() {}

    public static ToolbarActionManager getInstance() {
        return Singleton.INSTANCE;
    }

    public void trackAction(AbstractToolbarAction a, EditorCookie ec) {
        synchronized(actionMap) {
            Collection<AbstractToolbarAction> actions = actionMap.get(ec);
            if (actions == null) {
                actions = new ArrayList<>();
                actionMap.put(ec, actions);
                a.setVisibility(false);
            }
            actions.add(a);
        }
    }

    public void forgetAction(AbstractToolbarAction c, EditorCookie ec) {
        synchronized(actionMap) {
            Collection<AbstractToolbarAction> actions = actionMap.get(ec);
            if (actions != null) {
                actions.remove(c);
                if (actions.isEmpty()) actionMap.remove(ec);
            }
        }
    }

    public AbstractToolbarAction getAction(Class<? extends AbstractToolbarAction> clazz, EditorCookie ec) {
        synchronized(actionMap) {
            Collection<AbstractToolbarAction> actions = actionMap.get(ec);
            if (actions != null) {
                for(AbstractToolbarAction a : actions) {
                    if (a.getClass().equals(clazz)) return a;
                }
            }
        }
        return null;
    }

    public void updateState(final FileObject fo, final boolean visible) {
        try {
            DataObject dobj = DataObject.find(fo);
            if (dobj != null) {
                final EditorCookie ec = dobj.getCookie(EditorCookie.class);
                SwingUtilities.invokeLater(() -> {
                    synchronized (actionMap) {
                        if (actionMap.containsKey(ec)) {
                            actionMap.get(ec).stream().forEach((a) -> {
                                a.setVisibility(visible);
                            });
                        }
                    }
                });
            }
        } catch (DataObjectNotFoundException e) {
            Logger.getLogger(ToolbarActionManager.class.getName()).log(Level.FINE, null, e);
        }
    }
}
