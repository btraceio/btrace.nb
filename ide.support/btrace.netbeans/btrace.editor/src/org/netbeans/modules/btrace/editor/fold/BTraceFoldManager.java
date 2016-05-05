/*
 * Copyright (c) 2016, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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

package org.netbeans.modules.btrace.editor.fold;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.spi.editor.fold.FoldHierarchyTransaction;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldManagerFactory;
import org.netbeans.spi.editor.fold.FoldOperation;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceFoldManager implements FoldManager {
    final private static Pattern FRAGMENT_FOLD_START = Pattern.compile("\\s*//\\s*<fragment\\s+name\\s*=\\s*\"(.*?)\">\\s*", Pattern.DOTALL | Pattern.MULTILINE);
    final private static Pattern FRAGMENT_FOLD_END = Pattern.compile("\\s*//\\s*</fragment>\\s*", Pattern.DOTALL | Pattern.MULTILINE);

    public static class Factory implements FoldManagerFactory {

        @Override
        public FoldManager createFoldManager() {
            return new BTraceFoldManager();
        }
    }

    private class FoldFly {
        int start, end;

        public FoldFly(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FoldFly other = (FoldFly) obj;
            if (this.start != other.start) {
                return false;
            }
            return this.end == other.end;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + this.start;
            hash = 19 * hash + this.end;
            return hash;
        }
    }

    private FoldOperation currentOp;

    final private Set<Fold> folds = new HashSet<>();

    @Override
    public void changedUpdate(DocumentEvent de, FoldHierarchyTransaction fht) {
        if (de.getOffset() == 0 && de.getLength() == 0) {
            processFoldsFwd(de.getDocument(), de.getOffset(), fht);
        }
    }

    @Override
    public void expandNotify(Fold fold) {
    }

    @Override
    public void init(FoldOperation fo) {
        currentOp = fo;
    }

    @Override
    public void initFolds(FoldHierarchyTransaction fht) {
    }

    @Override
    public void insertUpdate(DocumentEvent de, FoldHierarchyTransaction fht) {
        processFoldsFwd(de.getDocument(), de.getOffset(), fht);
    }

    @Override
    public void release() {
    }

    @Override
    public void removeDamagedNotify(Fold fold) {
        folds.remove(fold);
    }

    @Override
    public void removeEmptyNotify(Fold fold) {
        folds.remove(fold);
    }

    @Override
    public void removeUpdate(DocumentEvent de, FoldHierarchyTransaction fht) {
        processFoldsFwd(de.getDocument(), de.getOffset(), fht);
    }

    private void processFoldsFwd(Document d, int pos, FoldHierarchyTransaction fht) {
        TokenHierarchy th = TokenHierarchy.get(d);
        TokenSequence<JavaTokenId> ts = th.tokenSequence();
        ts.move(0);
        int startPos = -1, guardLeading = 0;
        String fragName = "";
        boolean foldOpened = false;

        Set<FoldFly> existingFolds = new HashSet<>();
        Set<Fold> newFolds = new HashSet<>();
        while (ts.moveNext()) {
            Token<JavaTokenId> t = ts.token();
            if (t.id() == JavaTokenId.LINE_COMMENT) {
                Matcher startMatcher = FRAGMENT_FOLD_START.matcher(t.text());
                Matcher endMatcher =  FRAGMENT_FOLD_END.matcher(t.text());
                if (startMatcher.matches()) {
                    if (!foldOpened) {
                        startPos = t.offset(th);
                        fragName = startMatcher.group(1);
                        guardLeading = t.length();
                        foldOpened = true;
                    }
                } else if (endMatcher.matches()) {
                    if (foldOpened) {
                        foldOpened = false;
                        int endPos = t.offset(th) + t.length() - 1;
                        if (startPos > -1 && endPos > -1) {
                            boolean newFold = true;
                            for(Fold f : folds) {
                                if (f.getStartOffset() == startPos && f.getEndOffset() == endPos) {
                                    newFold = false;
                                    break;
                                }
                            }
                            if (newFold) {
                                try {
                                    Fold f = currentOp.addToHierarchy(new FoldType("btrace.fragment"), "Fragment: " + fragName, true, startPos, endPos, guardLeading, t.length(), null, fht);
                                    newFolds.add(f);
                                } catch (BadLocationException ex) {
                                    ex.printStackTrace();
                                }
                            } else {
                                existingFolds.add(new FoldFly(startPos, endPos));
                            }
                        }
                    }
                }
            }
        }
        for(Iterator<Fold> iter = folds.iterator();iter.hasNext();) {
            Fold f = iter.next();
            FoldFly ff = new FoldFly(f.getStartOffset(), f.getEndOffset());
            if (!existingFolds.contains(ff)) {
                currentOp.removeFromHierarchy(f, fht);
                iter.remove();
            }
        }
        folds.addAll(newFolds);
    }
}
