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
package org.dbflute.tomcat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.WebXml;

/**
 * @author jflute
 */
public class TomcatBoot {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    protected static final String DEFAULT_MARK_DIR = "/tmp/dbflute/tomcatboot"; // for shutdown hook

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final int port;
    protected final String contextPath;
    protected boolean development;
    protected boolean browseOnDesktop;
    protected boolean suppressShutdownHook;
    protected boolean useAnnotationDetect;
    protected boolean useMetaInfoResourceDetect;

    protected Tomcat server;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public TomcatBoot(int port, String contextPath) {
        this.port = port;
        this.contextPath = contextPath;
    }

    public TomcatBoot asDevelopment() {
        development = true;
        return this;
    }

    public TomcatBoot asDevelopment(boolean development) {
        this.development = development;
        return this;
    }

    public TomcatBoot browseOnDesktop() {
        assertDevelopmentState();
        browseOnDesktop = true;
        return this;
    }

    public TomcatBoot suppressShutdownHook() {
        assertDevelopmentState();
        suppressShutdownHook = true;
        return this;
    }

    protected void assertDevelopmentState() {
        if (!development) {
            throw new IllegalStateException("The option is valid only when development: port=" + port);
        }
    }

    public TomcatBoot useAnnotationHandling() {
        useAnnotationDetect = true;
        return this;
    }

    public TomcatBoot useMetaInfoResourceDetect() {
        useMetaInfoResourceDetect = true;
        return this;
    }

    // ===================================================================================
    //                                                                               Boot
    //                                                                              ======
    public TomcatBoot bootAwait() {
        startBoot();
        await();
        return this;
    }

    public void startBoot() { // no wait
        info("...Booting the Tomcat: port=" + port + " contextPath=" + contextPath);
        if (development) {
            registerShutdownHook();
        }
        prepareServer();
        final URI uri = startServer();
        info("Boot successful" + (development ? " as development" : "") + ": uri=" + uri);
        if (development) {
            browseOnDesktop(uri);
        }
    }

    protected void prepareServer() {
        server = createTomcat();
        server.setPort(port);
        final Context webContext;
        try {
            final String warPath = prepareWarPath();
            if (warPath.endsWith(".war")) {
                webContext = server.addWebapp(contextPath, warPath);
            } else {
                webContext = server.addWebapp(contextPath, new File(prepareWebappPath()).getAbsolutePath());
                webContext.getServletContext().setAttribute(Globals.ALT_DD_ATTR, prepareWebXmlPath());
            }
        } catch (ServletException e) {
            throw new IllegalStateException("Failed to set up web context.", e);
        }
    }

    protected Tomcat createTomcat() {
        final AnnotationHandling annotationHandling = prepareAnnotationHandling();
        final MetaInfoResourceHandling metaInfoResourceHandling = prepareMetaInfoResourceHandling();
        return newRhythmicalTomcat(annotationHandling, metaInfoResourceHandling);
    }

    protected RhythmicalTomcat newRhythmicalTomcat(AnnotationHandling annotationHandling,
            MetaInfoResourceHandling metaInfoResourceHandling) {
        return new RhythmicalTomcat(annotationHandling, metaInfoResourceHandling);
    }

    protected AnnotationHandling prepareAnnotationHandling() {
        return useAnnotationDetect ? AnnotationHandling.USE : AnnotationHandling.NONE;
    }

    protected MetaInfoResourceHandling prepareMetaInfoResourceHandling() {
        return useMetaInfoResourceDetect ? MetaInfoResourceHandling.USE : MetaInfoResourceHandling.NONE;
    }

    // ===================================================================================
    //                                                                   Rhythmical Tomcat
    //                                                                   =================
    public static class RhythmicalTomcat extends Tomcat { // to remove org.eclipse.jetty

        protected final AnnotationHandling annotationHandling;
        protected final MetaInfoResourceHandling metaInfoResourceHandling;

        public RhythmicalTomcat(AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling) {
            this.annotationHandling = annotationHandling;
            this.metaInfoResourceHandling = metaInfoResourceHandling;
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
            return newRhythmicalContextConfig(annotationHandling, metaInfoResourceHandling);
        }

        protected RhythmicalContextConfig newRhythmicalContextConfig(AnnotationHandling annotationHandling,
                MetaInfoResourceHandling metaInfoResourceHandling) {
            return new RhythmicalContextConfig(annotationHandling, metaInfoResourceHandling);
        }
    }

    public static class RhythmicalContextConfig extends ContextConfig {

        protected final AnnotationHandling annotationHandling;
        protected final MetaInfoResourceHandling metaInfoResourceHandling;

        public RhythmicalContextConfig(AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling) {
            this.annotationHandling = annotationHandling;
            this.metaInfoResourceHandling = metaInfoResourceHandling;
        }

        @Override
        protected void processServletContainerInitializers() {
            // initializers are needed for tld search
            //if (AnnotationHandling.USE.equals(annotationHandling)) {
            super.processServletContainerInitializers();
            removeJettyInitializer();
        }

        protected void removeJettyInitializer() {
            initializerClassMap.keySet().stream().filter(initializer -> {
                return initializer.getClass().getName().startsWith("org.eclipse.jetty");
            }).collect(Collectors.toList()).forEach(initializer -> {
                initializerClassMap.remove(initializer);
            });
        }

        @Override
        protected void processAnnotations(Set<WebXml> fragments, boolean handlesTypesOnly) {
            if (AnnotationHandling.USE.equals(annotationHandling)) {
                super.processAnnotations(fragments, handlesTypesOnly);
            }
        }

        @Override
        protected void processResourceJARs(Set<WebXml> fragments) {
            if (MetaInfoResourceHandling.USE.equals(metaInfoResourceHandling)) {
                super.processResourceJARs(fragments);
            }
        }
    }

    public static enum AnnotationHandling {
        USE, NONE
    }

    public static enum MetaInfoResourceHandling {
        USE, NONE
    }

    // ===================================================================================
    //                                                                        Prepare Path
    //                                                                        ============
    protected String prepareWarPath() {
        final URL location = TomcatBoot.class.getProtectionDomain().getCodeSource().getLocation();
        String path;
        try {
            path = location.toURI().getPath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to get path from the location: " + location, e);
        }
        return path;
    }

    protected String prepareWebappPath() {
        return "./src/main/webapp";
    }

    protected String prepareWebXmlPath() {
        return "./src/main/webapp/WEB-INF/web.xml";
    }

    // ===================================================================================
    //                                                                        Start Server
    //                                                                        ============
    protected URI startServer() {
        try {
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException("server start failed.", e);
        }
        final String uri = "http://" + server.getHost().getName() + ":" + port + contextPath;
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to create URI object: " + uri, e);
        }
    }

    // ===================================================================================
    //                                                                         Development
    //                                                                         ===========
    // -----------------------------------------------------
    //                                         Shutdown Hook
    //                                         -------------
    protected void registerShutdownHook() {
        if (suppressShutdownHook) {
            return;
        }
        final File markFile = prepareMarkFile();
        final long lastModified = markFile.lastModified();
        final String exp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date(lastModified));
        info("...Registering the shutdown hook for the Tomcat: lastModified=" + exp);
        new Thread(() -> {
            while (true) {
                if (needsShutdown(markFile, lastModified)) {
                    shutdownForcedly();
                    break;
                }
                waitForNextShuwdownHook();
            }
        }).start();
    }

    protected File prepareMarkFile() {
        final File markFile = new File(buildMarkFilePath());
        if (markFile.exists()) {
            markFile.setLastModified(System.currentTimeMillis());
            waitForExistingServerShuwdown();
        } else {
            markFile.mkdirs();
            try {
                markFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create new file: " + markFile, e);
            }
        }
        return markFile;
    }

    protected void waitForExistingServerShuwdown() {
        try {
            Thread.sleep(300L); // for Tomcat early catching port
        } catch (InterruptedException e) {
            throw new IllegalStateException("Failed to sleep the thread.", e);
        }
    }

    protected String buildMarkFilePath() {
        return getMarkDir() + "/boot" + port + ".dfmark";
    }

    protected String getMarkDir() {
        return DEFAULT_MARK_DIR;
    }

    protected boolean needsShutdown(File markFile, long lastModified) {
        return !markFile.exists() || lastModified != markFile.lastModified();
    }

    protected void shutdownForcedly() {
        info("...Shuting down the Tomcat forcedly: port=" + port);
        close();
    }

    protected void waitForNextShuwdownHook() {
        try {
            Thread.sleep(getShuwdownHookWaitMillis());
        } catch (InterruptedException e) {
            throw new IllegalStateException("Failed to sleep the thread.", e);
        }
    }

    protected long getShuwdownHookWaitMillis() {
        return 300L; // for Tomcat early catching port
    }

    // -----------------------------------------------------
    //                                                Browse
    //                                                ------
    protected void browseOnDesktop(final URI uri) {
        if (!browseOnDesktop) {
            return;
        }
        final java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
        try {
            desktop.browse(uri);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to browse the URI: " + uri, e);
        }
    }

    // ===================================================================================
    //                                                                               Await
    //                                                                               =====
    public void await() {
        if (server == null) {
            throw new IllegalStateException("server has not been started.");
        }
        try {
            server.getServer().await();
        } catch (Exception e) {
            throw new IllegalStateException("server join failed.", e);
        }
    }

    // ===================================================================================
    //                                                                               Close
    //                                                                               =====
    public void close() {
        if (server == null) {
            throw new IllegalStateException("server has not been started.");
        }
        try {
            server.stop();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop the Tomcat.", e);
        }
    }

    // ===================================================================================
    //                                                                             Logging
    //                                                                             =======
    protected void info(String msg) {
        System.out.println(msg); // console as default not to depends specific logger
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Tomcat getServer() {
        return server;
    }
}