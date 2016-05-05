/*
 * Copyright (c) 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

package org.netbeans.modules.btrace;

import java.io.File;
import static java.io.File.separator;
import static java.io.File.separatorChar;
import static org.netbeans.modules.btrace.ui.ScriptChooser.selectScript;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ScriptValidator {
    private final static ScriptValidator INSTANCE = new ScriptValidator();

    private ScriptValidator() {}

    public static ScriptValidator sharedInstance() {
        return INSTANCE;
    }

    public String validateScript(String scriptName, String scriptBaseDir) {
        String scriptPath = scriptName.replace(".class", "|class").replace('.', separatorChar).replace("|class", ".class");
        if (!new File(scriptBaseDir + separator + scriptPath.replace(".class", ".java")).exists()) {
            scriptName = selectScript(scriptBaseDir);
            scriptName = scriptName.replace('.', separatorChar);
            scriptPath = scriptName + ".class";
        }
        return scriptPath;
    }
}
