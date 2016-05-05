/*
 * Copyright (c) 2010, 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates this
 * particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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

package org.netbeans.modules.btrace.spi.impl;

import org.netbeans.modules.btrace.api.BTraceSettings;
import org.netbeans.modules.btrace.spi.BTraceSettingsProvider;


/**
 *
 * @author Jaroslav Bachorik 
 */
final public class BTraceSettingsProviderImpl implements BTraceSettingsProvider {
    private final BTraceSettings bs = new BTraceSettings() {

        @Override
        public boolean isDebugMode() {
            return false;
        }

        @Override
        public String getDumpClassPath() {
            return "";
        }

        @Override
        public boolean isDumpClasses() {
            return false;
        }
    };

    @Override
    public BTraceSettings getSettings() {
        return bs;
    }

}
