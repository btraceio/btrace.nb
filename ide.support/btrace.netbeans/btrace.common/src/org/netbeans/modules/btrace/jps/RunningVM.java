/*
 * Copyright (c) 2010, 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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


/**
 * A container for various information available for a running JVM.
 * Note that "VM flags" that we have for the VM in principle, is various -XX:+... options, which are supposed to
 * be used only by real expert users, or for debugging. We have them here just for completeness, but since they
 * are used very rarely, there is probably no reason to display them in the attach dialog or whatever.
 *
 * @author Misha Dmitriev
 */
public class RunningVM {
    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    private final String mainArgs;
    private final String mainClass;
    private final String vmArgs;
    private final String vmFlags;
    private final int pid;

    //~ Constructors -------------------------------------------------------------------------------------------------------------

    /** Creates a new instance of RunningVM */
    public RunningVM(int pid, String vmFlags, String vmArgs, String mainClass, String mainArgs) {
        this.pid = pid;
        this.vmFlags = vmFlags;
        this.vmArgs = vmArgs;
        this.mainClass = mainClass;
        this.mainArgs = mainArgs;
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public String getMainArgs() {
        return mainArgs;
    }

    public String getMainClass() {
        return mainClass;
    }

    public int getPid() {
        return pid;
    }

    public String getVMArgs() {
        return vmArgs;
    }

    public String getVMFlags() {
        return vmFlags;
    }

    @Override
    public String toString() {
        return getPid() + "  " + getVMFlags() + "  " + getVMArgs() + "  " + getMainClass() + "  " + getMainArgs(); // NOI18N
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RunningVM other = (RunningVM) obj;
        if ((this.mainArgs == null) ? (other.mainArgs != null) : !this.mainArgs.equals(other.mainArgs)) {
            return false;
        }
        if ((this.mainClass == null) ? (other.mainClass != null) : !this.mainClass.equals(other.mainClass)) {
            return false;
        }
        if ((this.vmArgs == null) ? (other.vmArgs != null) : !this.vmArgs.equals(other.vmArgs)) {
            return false;
        }
        if ((this.vmFlags == null) ? (other.vmFlags != null) : !this.vmFlags.equals(other.vmFlags)) {
            return false;
        }
        return this.pid == other.pid;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.mainArgs != null ? this.mainArgs.hashCode() : 0);
        hash = 79 * hash + (this.mainClass != null ? this.mainClass.hashCode() : 0);
        hash = 79 * hash + (this.vmArgs != null ? this.vmArgs.hashCode() : 0);
        hash = 79 * hash + (this.vmFlags != null ? this.vmFlags.hashCode() : 0);
        hash = 79 * hash + this.pid;
        return hash;
    }
}
