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

package org.netbeans.modules.btrace.spi;

import java.io.PrintWriter;
import static java.lang.System.err;
import static java.lang.System.out;
import org.netbeans.modules.btrace.api.BTraceTask;

/**
 *
 * @author Jaroslav Bachorik 
 */
public interface OutputProvider {
    final public static OutputProvider DEFAULT = new OutputProvider() {
        final PrintWriter stdout = new PrintWriter(out);
        final PrintWriter stderr = new PrintWriter(err);
        public PrintWriter getStdOut(BTraceTask task) {
            return stdout;
        }

        public PrintWriter getStdErr(BTraceTask task) {
            return stderr;
        }
    };

    PrintWriter getStdOut(BTraceTask task);
    PrintWriter getStdErr(BTraceTask task);
}
