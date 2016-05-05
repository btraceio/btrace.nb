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

package org.netbeans.modules.btrace.editor.errors;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.support.CancellableTreePathScanner;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.text.NbDocument;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ErrorScanner extends CancellableTreePathScanner<Void, Map<ErrorDescription, Integer>> {
    final private static Logger LOG = Logger.getLogger(ErrorScanner.class.getName());

    final private static String ERR_VAR_STATIC = "All BTrace variables must be declared as 'static'";
    final private static String ERR_VAR_NO_STATIC = "BTrace variables must not have access modifiers (short syntax)";
    final private static String ERR_CALL_NOT_ALLOWED = "Only calls to BTraceUtils methods are allowed";
    final private static String ERR_NO_STATIC_INIT = "Static initializer is not allowed. Use the class constructor instead";
    final private static String ERR_NO_CONSTRUCTOR = "Constructor is not allowed. Use static initializer instead";
    final private static String ERR_NO_LOOPS = "Loops are not allowed";
    final private static String ERR_ONLY_TOP_LEVEL_CLASS = "No inner/outer/local/nested classes";
    final private static String ERR_CLASS_PUBLIC_OR_DEFAULT = "BTrace class must have public or default access";
    final private static String ERR_NO_THROW = "Throwing exceptions not allowed";
    final private static String ERR_NO_TRYCATCH = "No try/catch blocks allowed";
    final private static String ERR_NO_NEW_INSTANCES = "Creating new instances not allowed";
    final private static String ERR_NO_SYNCHRONIZATION = "No synchronized blocks allowed";
    final private static String ERR_NO_RETURN = "No return values allowed";
    final private static String ERR_NO_RETURN_TYPE = "No return types allowed";
    final private static String ERR_METHOD_INVALID = "BTrace method must be PUBLIC and STATIC";
    final private static String ERR_SHORT_METHOD_INVALID = "BTrace method must not have access modifiers (short syntax)";
    final private static String ERR_NO_ASSIGNMENT = "No field assignments allowed";

    private static final String STATIC_INITIALIZER_NAME = "<clinit>";
    private static final String CONSTRUCTOR_NAME = "<init>";

    private StyledDocument doc;
    final private CompilationInfo ci;

    private boolean isBTraceClass = false;
    private boolean isHandler = false;
    private boolean isUnsafe = false;
    private boolean isShortSyntax = false;
    private String btraceClassName = null;

    public ErrorScanner(AtomicBoolean canceled, CompilationInfo ci) {
        super(canceled);

        this.ci = ci;
        setDocument(ci);
    }

    public ErrorScanner(CompilationInfo cc) {
        this.ci = cc;
        setDocument(cc);
    }

    private void setDocument(CompilationInfo ci) {
        try {
            this.doc = (StyledDocument)ci.getDocument();
        } catch (IOException e) {
            this.doc = null;
            LOG.log(Level.SEVERE, null, e);
        }
    }

    StyledDocument getDocument() {
        return doc;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree node, Map<ErrorDescription, Integer> p) {
        isBTraceClass = false;
        isHandler = false;
        isUnsafe = false;
        btraceClassName = null;
        return doc != null ? super.visitCompilationUnit(node, p) : null;
    }

    @Override
    public Void visitAnnotation(AnnotationTree node, Map<ErrorDescription, Integer> p) {
        Element e = ci.getTrees().getElement(getCurrentPath());
        if (e != null) {
            String annType = ((TypeElement)e).getQualifiedName().toString();
            if (annType.startsWith("com.sun.btrace.annotations.")) { // NOI18N
                isHandler = true;
                if (annType.equals("com.sun.btrace.annotations.BTrace")) { // NOI18N
                    isBTraceClass = true;
                    for(ExpressionTree et : node.getArguments()) {
                        if (et.getKind() == Tree.Kind.ASSIGNMENT) {
                            String varName = ((AssignmentTree)et).getVariable().toString();
                            String varValue = ((AssignmentTree)et).getExpression().toString();
                            if (varName.equals("unsafe") && varValue.toLowerCase().equals("true")) { // NOI18N
                                isUnsafe = true;
                            }
                        }
                    }
                }
            }
        }
        return super.visitAnnotation(node, p);
    }

    @Override
    public Void visitClass(ClassTree node, Map<ErrorDescription, Integer> p) {
        Element e = ci.getTrees().getElement(getCurrentPath());
        if (e != null) {
            if (((TypeElement)e).getNestingKind() != NestingKind.TOP_LEVEL) {
                addError(node, ERR_ONLY_TOP_LEVEL_CLASS, p);
            } else {
                btraceClassName = ((TypeElement)e).getQualifiedName().toString();
                if (e.getModifiers().contains(Modifier.PRIVATE) ||
                    e.getModifiers().contains(Modifier.PROTECTED)) {
                    addError(node, ERR_CLASS_PUBLIC_OR_DEFAULT, p);
                } else if (!e.getModifiers().contains(Modifier.PUBLIC)) {
                    isShortSyntax = true;
                }
            }
        }
        return super.visitClass(node, p);
    }

    @Override
    public Void visitThrow(ThrowTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_THROW, p);
        return super.visitThrow(node, p);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Map<ErrorDescription, Integer> p) {
        if (isHandler && !isUnsafe) {
            Element e = ci.getTrees().getElement(getCurrentPath());
            if (e != null && e.getKind() == ElementKind.METHOD) {
                ExecutableElement el = (ExecutableElement)e;
                Element parent = el.getEnclosingElement();
                if (parent == null ||
                    parent.getKind() != ElementKind.CLASS ||
                    !(((TypeElement)parent).getQualifiedName().toString().startsWith("com.sun.btrace.BTraceUtils") ||
                      ((TypeElement)parent).getQualifiedName().toString().equals(btraceClassName))) {
                    addError(node, ERR_CALL_NOT_ALLOWED, p);
                }
            }
        }
        return super.visitMethodInvocation(node, p);
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Map<ErrorDescription, Integer> p) {
        if (isUnsafe) return super.visitAssignment(node, p);

        Tree varTree = node.getVariable();
        TreePath tp = ci.getTrees().getPath(ci.getCompilationUnit(), varTree);
        Element e = tp != null ? ci.getTrees().getElement(tp) : null;
        if (e != null && e.getKind().isField()) {
            Element parent = e.getEnclosingElement();
            if (parent != null && (parent.getKind().isClass() || parent.getKind().isInterface())) {
                String typeName = ((TypeElement)parent).getQualifiedName().toString();
                if (!(typeName.equals(btraceClassName) ||
                    typeName.equals("com.cun.btrace.BTraceUtils") || // NOI18N
                    typeName.startsWith("com.sun.btrace.BTraceUtils$"))) { // NOI18N
                    addError(node, ERR_NO_ASSIGNMENT, p);
                }
            }
        }
        return super.visitAssignment(node, p);
    }

    @Override
    public Void visitReturn(ReturnTree node, Map<ErrorDescription, Integer> p) {
        if (isHandler && node.getExpression() != null && ((Tree)node).getKind() != Tree.Kind.ERRONEOUS) {
            addError(node, ERR_NO_RETURN, p);
        }
        return super.visitReturn(node, p);
    }

    @Override
    public Void visitVariable(VariableTree node, Map<ErrorDescription, Integer> p) {
        if (isHandler && !isUnsafe) {
            boolean isStatic = node.getModifiers().getFlags().contains(Modifier.STATIC);
            if (!(isShortSyntax ^ isStatic)) {
                Element e = ci.getTrees().getElement(getCurrentPath());
                if (e != null && e.getKind() == ElementKind.FIELD) {
                    addError(node, isStatic ? ERR_VAR_NO_STATIC : ERR_VAR_STATIC, p);
                }
            }
        }
        return super.visitVariable(node, p);
    }

    @Override
    public Void visitMethod(MethodTree node, Map<ErrorDescription, Integer> p) {
        isHandler = false;
        try {
            if (!isShortSyntax && !isUnsafe && node.getName().contentEquals(CONSTRUCTOR_NAME)) {
                addError(node, ERR_NO_CONSTRUCTOR, p);
            }
            return super.visitMethod(node, p);
        } finally {
            if (isHandler) {
                if ((node.getReturnType() != null && !node.getReturnType().toString().equals("void"))) { // NOI18N
                    addError(node.getReturnType(), ERR_NO_RETURN_TYPE, p);
                }
                if (!isShortSyntax && !node.getModifiers().getFlags().containsAll(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC))) {
                    addError(node.getModifiers(), ERR_METHOD_INVALID, p);
                } else if (isShortSyntax && !node.getModifiers().getFlags().isEmpty()) {
                    addError(node.getModifiers(), ERR_SHORT_METHOD_INVALID, p);
                }
            }
            isHandler = false;
        }
    }

    @Override
    public Void visitNewArray(NewArrayTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_NEW_INSTANCES, p);
        return super.visitNewArray(node, p);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_NEW_INSTANCES, p);
        return super.visitNewClass(node, p);
    }

    @Override
    public Void visitSynchronized(SynchronizedTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_SYNCHRONIZATION, p);
        return super.visitSynchronized(node, p);
    }

    @Override
    public Void visitBlock(BlockTree node, Map<ErrorDescription, Integer> p) {
        if (isShortSyntax && node.isStatic()) {
            addError(node, ERR_NO_STATIC_INIT, p);
        }
        return super.visitBlock(node, p);
    }


    @Override
    public Void visitWhileLoop(WhileLoopTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_LOOPS, p);
        return super.visitWhileLoop(node, p);
    }

    @Override
    public Void visitDoWhileLoop(DoWhileLoopTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_LOOPS, p);
        return super.visitDoWhileLoop(node, p);
    }

    @Override
    public Void visitForLoop(ForLoopTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_LOOPS, p);
        return super.visitForLoop(node, p);
    }

    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_LOOPS, p);
        return super.visitEnhancedForLoop(node, p);
    }

    @Override
    public Void visitTry(TryTree node, Map<ErrorDescription, Integer> p) {
        if (!isUnsafe) addError(node, ERR_NO_TRYCATCH, p);
        return super.visitTry(node, p);
    }

    private void addError(final Tree node, final String message, final List<Fix> fixes, final Map<ErrorDescription, Integer> p) {
        if (!isBTraceClass) return; // no errors for non btrace sources
        try {
            int startPos = (int) ci.getTrees().getSourcePositions().getStartPosition(ci.getCompilationUnit(), node);
            int endPos = (int) ci.getTrees().getSourcePositions().getEndPosition(ci.getCompilationUnit(), node);
            if (endPos > -1) {
                int lineNo = NbDocument.findLineNumber(doc, startPos);
                ErrorDescription ed = null;
                if (fixes == null || fixes.isEmpty()) {
                    ed = ErrorDescriptionFactory.createErrorDescription(Severity.ERROR, message, doc, doc.createPosition(startPos), doc.createPosition(endPos));
                } else {
                    ed = ErrorDescriptionFactory.createErrorDescription(Severity.ERROR, message, fixes, doc, doc.createPosition(startPos), doc.createPosition(endPos));
                }
                p.put(ed, lineNo);
            }
        } catch (BadLocationException ex) {
            LOG.log(Level.WARNING, null, ex);
        }
    }

    private void addError(final Tree node, final String message, final Map<ErrorDescription, Integer> p) {
        addError(node, message, null, p);
    }
}
