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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.RhythmicalContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;
import org.dbflute.tomcat.logging.BootLogger;

/**
 * @author jflute
 */
public class RhythmicalTomcat extends Tomcat { // e.g. to remove org.eclipse.jetty

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final BootLogger bootLogger;
    protected final AnnotationHandling annotationHandling;
    protected final MetaInfoResourceHandling metaInfoResourceHandling;
    protected final TldHandling tldHandling;
    protected final WebFragmentsHandling webFragmentsHandling;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public RhythmicalTomcat(BootLogger bootLogger, AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling,
            TldHandling tldHandling, WebFragmentsHandling webFragmentsHandling) {
        this.bootLogger = bootLogger;
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
        return new DefaultWebXmlListener() {
            @Override
            public void lifecycleEvent(LifecycleEvent event) {
                doDefaultWebXmlLifecycleEvent(event);
            }
        };
    }

    protected void doDefaultWebXmlLifecycleEvent(LifecycleEvent event) { // to suppress JSP's exception noise
        if (Lifecycle.BEFORE_START_EVENT.equals(event.getType())) {
            final Context ctx = (Context) event.getLifecycle();
            final String msgBase = "...Initializing webapp of default web.xml ";
            if (existsJspServlet()) { // e.g. jasper
                bootLogger.info(msgBase + "with JSP (the servlet found)");
                initWebappDefaults(ctx); // as normal
            } else {
                final Optional<String[]> defaultMimeMappings = extractDefaultMimeMappings();
                if (!defaultMimeMappings.isPresent()) { // e.g. version difference
                    bootLogger.info(msgBase + "with JSP (cannot remove it)");
                    initWebappDefaults(ctx); // as normal
                } else { // e.g. JSON API style
                    bootLogger.info(msgBase + "without JSP");
                    initWebappDefaultsWithoutJsp(ctx, defaultMimeMappings.get());
                }
            }
        }
    }

    // ===================================================================================
    //                                                                      Webapp Default
    //                                                                      ==============
    protected boolean existsJspServlet() {
        try {
            Class.forName("org.apache.jasper.servlet.JspServlet");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    protected Optional<String[]> extractDefaultMimeMappings() {
        final String fieldName = "DEFAULT_MIME_MAPPINGS";
        final String[] mappings;
        try {
            final Field mappingsField = Tomcat.class.getDeclaredField(fieldName);
            mappingsField.setAccessible(true);
            mappings = (String[]) mappingsField.get(this);
        } catch (NoSuchFieldException continued) {
            bootLogger.info("*Not found the field " + fieldName + " in this Tomcat: " + this);
            return Optional.empty();
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to get default mime mappings: " + fieldName, e);
        }
        return Optional.ofNullable(mappings);
    }

    protected void initWebappDefaultsWithoutJsp(Context ctx, String[] defaultMimeMappings) {
        // Default servlet
        final Wrapper servlet = addServlet(ctx, "default", "org.apache.catalina.servlets.DefaultServlet");
        servlet.setLoadOnStartup(1);
        servlet.setOverridable(true);

        // without JSP
        //// JSP servlet (by class name - to avoid loading all deps)
        //servlet = addServlet(ctx, "jsp", "org.apache.jasper.servlet.JspServlet");
        //servlet.addInitParameter("fork", "false");
        //servlet.setLoadOnStartup(3);
        //servlet.setOverridable(true);

        // Servlet mappings
        ctx.addServletMapping("/", "default");
        // without JSP
        //ctx.addServletMapping("*.jsp", "jsp");
        //ctx.addServletMapping("*.jspx", "jsp");

        // Sessions
        ctx.setSessionTimeout(30);

        // MIME mappings
        for (int i = 0; i < defaultMimeMappings.length;) {
            ctx.addMimeMapping(defaultMimeMappings[i++], defaultMimeMappings[i++]);
        }

        // Welcome files
        ctx.addWelcomeFile("index.html");
        ctx.addWelcomeFile("index.htm");
        // without JSP
        //ctx.addWelcomeFile("index.jsp");
    }
}
