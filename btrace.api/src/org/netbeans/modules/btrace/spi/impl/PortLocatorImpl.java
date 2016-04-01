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

package org.netbeans.modules.btrace.spi.impl;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import static com.sun.tools.attach.VirtualMachine.attach;
import java.io.IOException;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import java.net.ServerSocket;
import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;
import static java.util.logging.Logger.getLogger;
import org.netbeans.modules.btrace.api.BTraceTask;
import org.netbeans.modules.btrace.spi.PortLocator;
import static org.netbeans.modules.btrace.spi.PortLocator.DEFAULT_PORT;
import static org.netbeans.modules.btrace.spi.PortLocator.PORT_PROPERTY;

/**
 *
 * @author Jaroslav Bachorik <yardus@netbeans.org>
 */
final public class PortLocatorImpl implements PortLocator {
    final private static Logger LOGGER = getLogger(PortLocator.class.getName());

    @Override
    public int getTaskPort(BTraceTask task) {
        VirtualMachine vm = null;
        try {
            vm = attach(valueOf(task.getPid()));
            String portStr = vm.getSystemProperties().getProperty(PORT_PROPERTY);
            return portStr != null ? parseInt(portStr) : findFreePort();
        } catch (AttachNotSupportedException | IOException ex) {
            getLogger(PortLocatorImpl.class.getName()).log(SEVERE, null, ex);
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException e) {
                    LOGGER.log(SEVERE, null, e);
                }
            }
        }
        return findFreePort();
    }

    private static int findFreePort() {
        ServerSocket server = null;
        int port = 0;
        try {
            server = new ServerSocket(0);
            port = server.getLocalPort();
        } catch (IOException e) {
            port = DEFAULT_PORT;
        } finally {
            try {
                server.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return port;
    }
}
