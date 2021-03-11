/*
 * Copyright 2015-2021 the original author or authors.
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
import java.util.function.Predicate;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.RhythmicalContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.AccessLogValve;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;
import org.dbflute.tomcat.core.accesslog.AccessLogOption;
import org.dbflute.tomcat.core.likeit.LikeItCatalinaResource;
import org.dbflute.tomcat.core.likeit.LikeItCatalinaSetupper;
import org.dbflute.tomcat.core.valve.YourValveOption;
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
    protected final Predicate<String> tldFilesSelector; // null allowed, selector is not required
    protected final WebFragmentsHandling webFragmentsHandling;
    protected final Predicate<String> webFragmentsSelector; // null allowed, selector is not required
    protected final AccessLogOption accessLogOption; // null allowed, use access log if exists
    protected final YourValveOption yourValveOption; // null allowed, for user options
    protected final LikeItCatalinaSetupper likeitCatalinaSetupper; // null allowed, for user options

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public RhythmicalTomcat(BootLogger bootLogger // has many arguments
            , AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling // meta
            , TldHandling tldHandling, Predicate<String> tldFilesSelector // taglib files
            , WebFragmentsHandling webFragmentsHandling, Predicate<String> webFragmentsSelector // web fragments
            , AccessLogOption accessLogOption, YourValveOption yourValveOption, LikeItCatalinaSetupper likeitCatalinaSetupper // options
    ) {
        this.bootLogger = bootLogger;
        this.annotationHandling = annotationHandling;
        this.metaInfoResourceHandling = metaInfoResourceHandling;
        this.tldHandling = tldHandling;
        this.tldFilesSelector = tldFilesSelector;
        this.webFragmentsHandling = webFragmentsHandling;
        this.webFragmentsSelector = webFragmentsSelector;
        this.accessLogOption = accessLogOption;
        this.yourValveOption = yourValveOption;
        this.likeitCatalinaSetupper = likeitCatalinaSetupper;
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
        return newRhythmicalContextConfig(annotationHandling, metaInfoResourceHandling // meta
                , tldHandling, tldFilesSelector // taglib files
                , webFragmentsHandling, webFragmentsSelector // web fragments
        );
    }

    protected RhythmicalContextConfig newRhythmicalContextConfig( // has many arguments
            AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling // meta
            , TldHandling tldHandling, Predicate<String> tldFilesSelector // taglib files
            , WebFragmentsHandling webFragmentsHandling, Predicate<String> webFragmentsSelector // web fragments
    ) {
        return new RhythmicalContextConfig(annotationHandling, metaInfoResourceHandling // meta
                , tldHandling, tldFilesSelector // taglib files
                , webFragmentsHandling, webFragmentsSelector // web fragments
        );
    }

    @Override
    public Context addWebapp(Host host, String contextPath, String docBase, LifecycleListener config) {
        // quit because of private and unneeded
        //silence(host, contextPath);

        final Context ctx = createContext(host, contextPath);
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);
        ctx.addLifecycleListener(newDefaultWebXmlListener()); // *extension point
        ctx.setConfigFile(getWebappConfigFile(docBase, contextPath));

        ctx.addLifecycleListener(config);

        if (config instanceof ContextConfig) {
            // prevent it from looking ( if it finds one - it'll have dup error )
            ((ContextConfig) config).setDefaultWebXml(noDefaultWebXmlPath());
        }

        if (host == null) {
            getHost().addChild(ctx);
        } else {
            host.addChild(ctx);
        }

        return ctx;
    }

    // -----------------------------------------------------
    //                                               Context
    //                                               -------
    protected Context createContext(Host host, String url) { // similar to super class's private method
        String contextClass = StandardContext.class.getName();
        if (host == null) {
            host = this.getHost();
        }
        if (host instanceof StandardHost) {
            contextClass = ((StandardHost) host).getContextClass();
        }
        final Context ctx;
        try {
            ctx = (Context) Class.forName(contextClass).getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            String msg = "Can't instantiate context-class " + contextClass + " for host " + host + " and url " + url;
            throw new IllegalArgumentException(msg, e);
        }
        setupAccessLogIfNeeds(ctx);
        setupYourValveIfNeeds(ctx);
        if (likeitCatalinaSetupper != null) {
            likeitCatalinaSetupper.setup(new LikeItCatalinaResource(host, ctx));
        }
        return ctx;
    }

    protected void setupAccessLogIfNeeds(Context ctx) {
        if (accessLogOption != null && ctx instanceof StandardContext) { // also check context type just in case
            final StandardContext stdctx = (StandardContext) ctx;
            final AccessLogValve valve = new AccessLogValve();
            accessLogOption.getLogDir().ifPresent(dir -> valve.setDirectory(dir));
            accessLogOption.getFilePrefix().ifPresent(prefix -> valve.setPrefix(prefix));
            accessLogOption.getFileSuffix().ifPresent(suffix -> valve.setSuffix(suffix));
            accessLogOption.getFileDateFormat().ifPresent(format -> valve.setFileDateFormat(format));
            valve.setEncoding(accessLogOption.getFileEncoding().orElse("UTF-8"));
            valve.setPattern(accessLogOption.getFormatPattern().orElse("common"));
            accessLogOption.getConditionIf().ifPresent(cond -> valve.setConditionIf(cond));
            accessLogOption.getConditionUnless().ifPresent(cond -> valve.setConditionUnless(cond));
            stdctx.addValve(valve);
        }
    }

    protected void setupYourValveIfNeeds(Context ctx) {
        if (yourValveOption != null && ctx instanceof StandardContext) { // also check context type just in case
            final StandardContext stdctx = (StandardContext) ctx;
            for (Valve valve : yourValveOption.getValveList()) {
                stdctx.addValve(valve);
            }
        }
    }

    // -----------------------------------------------------
    //                                       WebXml Listener
    //                                       ---------------
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
        ctx.addServletMappingDecoded("/", "default");
        // without JSP
        //ctx.addServletMappingDecoded("*.jsp", "jsp");
        //ctx.addServletMappingDecoded("*.jspx", "jsp");

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
