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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.cookies.EditorCookie;
import org.openide.util.ContextAwareAction;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.Lookup.Item;
import org.openide.util.Lookup.Result;
import org.openide.util.Lookup.Template;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.TopComponent;

/**
 *
 * @author Jaroslav Bachorik
 */
abstract public class AbstractToolbarAction extends AbstractAction implements ContextAwareAction, HelpCtx.Provider, Presenter.Toolbar {
    protected static class Context {
        final private Lookup lookup;
        final private InstanceContent content;

        public Context(Lookup lookup) {
            this.content = new InstanceContent();
            this.lookup = new ProxyLookup(lookup, new AbstractLookup(content));
        }

        public <T> Result<T> lookupResult(Class<T> clazz) {
            return lookup.lookupResult(clazz);
        }

        public <T> Item<T> lookupItem(Template<T> template) {
            return lookup.lookupItem(template);
        }

        public <T> Collection<? extends T> lookupAll(Class<T> clazz) {
            return lookup.lookupAll(clazz);
        }

        public <T> Result<T> lookup(Template<T> template) {
            return lookup.lookup(template);
        }

        public <T> T lookup(Class<T> clazz) {
            return lookup.lookup(clazz);
        }

        public void addInstance(Object instance) {
            content.add(instance);
        }

        public void removeInstance(Object instance) {
            content.remove(instance);
        }

        final public static Context EMPTY = new Context(Lookup.EMPTY);
    }

    final private static Map<EditorCookie, Context> contextMap = new WeakHashMap<EditorCookie, Context>();
    private Context context = Context.EMPTY;

    public AbstractToolbarAction() {
    }

    protected AbstractToolbarAction(Context context) {
        this.context = context;
    }

    @Override
    final public Action createContextAwareInstance(Lookup actionContext) {
        EditorCookie ec = actionContext.lookup(EditorCookie.class);
        AbstractToolbarAction a = ToolbarActionManager.getInstance().getAction(this.getClass(), ec);
        if (a == null) {
            Context c = contextMap.get(ec);
            if (c == null) {
                c = new Context(actionContext);
                contextMap.put(ec, c);
            }
            a = newInstance(c);
            ToolbarActionManager.getInstance().trackAction(a, ec);
        }
        return a;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    final public Component getToolbarPresenter() {
        return toolbarPresenterInstance();
    }

    final protected Context getContext() {
        return context;
    }

    protected void setVisibility(boolean visible) {
        toolbarPresenterInstance().setVisible(visible);
    }

    @Override
    final public void actionPerformed(ActionEvent e) {
        AbstractToolbarAction performer = this;
        if (getContext() == Context.EMPTY) {
            Lookup lkp = TopComponent.getRegistry().getActivated().getLookup();
            EditorCookie ec = lkp.lookup(EditorCookie.class);
            performer = ToolbarActionManager.getInstance().getAction(DeployAction.class, ec);
            if (performer == null) {
                performer = (AbstractToolbarAction)createContextAwareInstance(lkp);
            }
        }
        performer.doPerform();
    }

    abstract protected void doPerform();
    abstract protected AbstractToolbarAction newInstance(Context context);
    abstract protected Component toolbarPresenterInstance();
}
