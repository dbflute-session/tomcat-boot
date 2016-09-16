/*
 * Copyright 2015-2016 the original author or authors.
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
import org.apache.catalina.startup.RhythmicalContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;

/**
 * @author jflute
 */
public class RhythmicalTomcat extends Tomcat { // e.g. to remove org.eclipse.jetty

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final AnnotationHandling annotationHandling;
    protected final MetaInfoResourceHandling metaInfoResourceHandling;
    protected final TldHandling tldHandling;
    protected final WebFragmentsHandling webFragmentsHandling;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public RhythmicalTomcat(AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling,
            TldHandling tldHandling, WebFragmentsHandling webFragmentsHandling) {
        this.annotationHandling = annotationHandling;
        this.metaInfoResourceHandling = metaInfoResourceHandling;
        this.tldHandling = tldHandling;
        this.webFragmentsHandling = webFragmentsHandling;
    }

    // ===================================================================================
    //                                                                          Add Webapp
    //                                                                          ==========
    // copied from super Tomcat because of private methods
    @Override
    public Context addWebapp(Host host, String contextPath, String docBase) {
        final ContextConfig contextConfig = createContextConfig(); // *extension point
        return addWebapp(host, contextPath, docBase, contextConfig);
    }

    protected ContextConfig createContextConfig() {
        return newRhythmicalContextConfig(annotationHandling, metaInfoResourceHandling, tldHandling, webFragmentsHandling);
    }

    protected RhythmicalContextConfig newRhythmicalContextConfig(AnnotationHandling annotationHandling,
            MetaInfoResourceHandling metaInfoResourceHandling, TldHandling tldHandling, WebFragmentsHandling webFragmentsHandling) {
        return new RhythmicalContextConfig(annotationHandling, metaInfoResourceHandling, tldHandling, webFragmentsHandling);
    }

    @Override
    public Context addWebapp(Host host, String contextPath, String docBase, ContextConfig config) {
        // quit because of private and unneeded
        //silence(host, contextPath);

        final Context ctx = createContext(host, contextPath);
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);
        ctx.addLifecycleListener(newDefaultWebXmlListener()); // *extension point
        ctx.setConfigFile(getWebappConfigFile(docBase, contextPath));

        ctx.addLifecycleListener(config);

        // prevent it from looking ( if it finds one - it'll have dup error )
        config.setDefaultWebXml(noDefaultWebXmlPath());

        if (host == null) {
            getHost().addChild(ctx);
        } else {
            host.addChild(ctx);
        }

        return ctx;
    }

    protected Context createContext(Host host, String url) { // same as super class's private method
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

    protected DefaultWebXmlListener newDefaultWebXmlListener() {
        return new DefaultWebXmlListener();
    }
}
