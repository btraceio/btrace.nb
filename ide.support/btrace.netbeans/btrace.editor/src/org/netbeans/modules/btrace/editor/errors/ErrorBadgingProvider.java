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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser.Result;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.EmbeddingIndexer;
import org.netbeans.modules.parsing.spi.indexing.EmbeddingIndexerFactory;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache;
import org.netbeans.modules.parsing.spi.indexing.ErrorsCache.ErrorKind;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.netbeans.modules.parsing.spi.indexing.support.IndexingSupport;
import org.netbeans.spi.editor.hints.ErrorDescription;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ErrorBadgingProvider extends EmbeddingIndexer {
    final private static Logger LOG = Logger.getLogger(ErrorBadgingProvider.class.getName());

    public static class Factory extends EmbeddingIndexerFactory {
        private static final String MIME = "text/x-java";
        private static final int VERSION = 1;
        private static final String NAME = "btrace.errors";

        @Override
        public EmbeddingIndexer createIndexer(Indexable indxbl, Snapshot snpsht) {
            if (indxbl.getMimeType().equals(MIME)) {
                return new ErrorBadgingProvider();
            }
            return null;
        }

        @Override
        public void filesDeleted(Iterable<? extends Indexable> itrbl, Context cntxt) {
            for(Indexable ixbl : itrbl) {
                try {
                    IndexingSupport.getInstance(cntxt).removeDocuments(ixbl);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, null, e);
                }
            }
        }

        @Override
        public void filesDirty(Iterable<? extends Indexable> itrbl, Context cntxt) {
            for(Indexable ixbl : itrbl) {
                try {
                    IndexingSupport.getInstance(cntxt).markDirtyDocuments(ixbl);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, null, e);
                }
            }
        }

        @Override
        public int getIndexVersion() {
            return VERSION;
        }

        @Override
        public String getIndexerName() {
            return NAME;
        }
    }

    final private class ErrorConvertor implements ErrorsCache.Convertor<Map.Entry<ErrorDescription, Integer>>  {
        public ErrorKind getKind(Map.Entry<ErrorDescription, Integer> t) {
            switch (t.getKey().getSeverity()) {
                case ERROR:
                    return ErrorKind.ERROR;
                case WARNING:
                    return ErrorKind.WARNING;
                default:
                    return null;
            }
        }

        public int getLineNumber(Map.Entry<ErrorDescription, Integer> t) {
            return t.getValue() + 1;
        }

        public String getMessage(Map.Entry<ErrorDescription, Integer> t) {
            return t.getKey().getDescription();
        }

    }

    private final ErrorsCache.Convertor convertor = new ErrorConvertor();

    final Map<ErrorDescription, Integer> errorMap = new HashMap<>();

    private ErrorBadgingProvider() {
    }

    @Override
    protected void index(final Indexable indxbl, Result result, final Context cntxt) {
        final CompilationController cc = (CompilationController)CompilationInfo.get(result);

        try {
            errorMap.clear();
            if (cc.toPhase(Phase.ELEMENTS_RESOLVED).compareTo(Phase.ELEMENTS_RESOLVED) == 1) {
                return;
            }

            ErrorScanner scanner = new ErrorScanner(cc);
            scanner.scan(cc.getCompilationUnit(), errorMap);
            ErrorsCache.setErrors(cntxt.getRootURI(), indxbl, errorMap.entrySet(), convertor);
        } catch (IOException e) {
            LOG.log(Level.WARNING, null, e);
        }
    }
}
