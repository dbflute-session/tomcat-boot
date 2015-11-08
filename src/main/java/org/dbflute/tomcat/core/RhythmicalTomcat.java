/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.dbflute.tomcat.core;

import java.lang.reflect.InvocationTargetException;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;

/**
 * @author jflute
 */
public class RhythmicalTomcat extends Tomcat { // e.g. to remove org.eclipse.jetty

    protected final AnnotationHandling annotationHandling;
    protected final MetaInfoResourceHandling metaInfoResourceHandling;
    protected final TldHandling tldHandling;
    protected final WebFragmentsHandling webFragmentsHandling;

    public RhythmicalTomcat(AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling,
            TldHandling tldHandling, WebFragmentsHandling webFragmentsHandling) {
        this.annotationHandling = annotationHandling;
        this.metaInfoResourceHandling = metaInfoResourceHandling;
        this.tldHandling = tldHandling;
        this.webFragmentsHandling = webFragmentsHandling;
    }

    // copied from super Tomcat because of private methods
    @Override
    public Context addWebapp(Host host, String contextPath, String name, String docBase) {
        // quit
        //silence(host, contextPath);

        final Context ctx = createContext(host, contextPath);
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);
        ctx.addLifecycleListener(newDefaultWebXmlListener());
        ctx.setConfigFile(getWebappConfigFile(docBase, contextPath));

        final ContextConfig ctxCfg = createContextConfig(); // *extension point
        ctx.addLifecycleListener(ctxCfg);

        // prevent it from looking ( if it finds one - it'll have dup error )
        ctxCfg.setDefaultWebXml(noDefaultWebXmlPath());

        if (host == null) {
            getHost().addChild(ctx);
        } else {
            host.addChild(ctx);
        }

        return ctx;
    }

    protected DefaultWebXmlListener newDefaultWebXmlListener() {
        return new DefaultWebXmlListener();
    }

    protected Context createContext(Host host, String url) {
        String contextClass = StandardContext.class.getName();
        if (host == null) {
            host = this.getHost();
        }
        if (host instanceof StandardHost) {
            contextClass = ((StandardHost) host).getContextClass();
        }
        try {
            return (Context) Class.forName(contextClass).getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            String msg = "Can't instantiate context-class " + contextClass + " for host " + host + " and url " + url;
            throw new IllegalArgumentException(msg, e);
        }
    }

    protected ContextConfig createContextConfig() {
        return newRhythmicalContextConfig(annotationHandling, metaInfoResourceHandling, tldHandling, webFragmentsHandling);
    }

    protected RhythmicalContextConfig newRhythmicalContextConfig(AnnotationHandling annotationHandling,
            MetaInfoResourceHandling metaInfoResourceHandling, TldHandling tldHandling, WebFragmentsHandling webFragmentsHandling2) {
        return new RhythmicalContextConfig(annotationHandling, metaInfoResourceHandling, tldHandling, webFragmentsHandling);
    }
}
