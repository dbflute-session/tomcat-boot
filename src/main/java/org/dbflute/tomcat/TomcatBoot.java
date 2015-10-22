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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;
import org.dbflute.tomcat.core.RhythmicalTomcat;
import org.dbflute.tomcat.logging.ServerLoggingLoader;
import org.dbflute.tomcat.logging.TomcatLoggingOption;

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
    // -----------------------------------------------------
    //                                                 Basic
    //                                                 -----
    protected final int port;
    protected final String contextPath;

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    protected boolean development;
    protected boolean browseOnDesktop;
    protected boolean suppressShutdownHook;
    protected boolean useAnnotationDetect;
    protected boolean useMetaInfoResourceDetect;
    protected boolean useTldDetect;
    protected boolean useWebFragmentsDetect;
    protected String configFile;
    protected String loggingFile;
    protected Consumer<TomcatLoggingOption> loggingOptionCall;

    // -----------------------------------------------------
    //                                              Stateful
    //                                              --------
    protected String resolvedConfigFile;
    protected Properties configProps;
    protected Logger logger;
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

    @Deprecated
    public TomcatBoot useAnnotationHandling() {
        useAnnotationDetect = true;
        return this;
    }

    public TomcatBoot useAnnotationDetect() {
        useAnnotationDetect = true;
        return this;
    }

    public TomcatBoot useMetaInfoResourceDetect() {
        useMetaInfoResourceDetect = true;
        return this;
    }

    public TomcatBoot useTldDetect() {
        useTldDetect = true;
        return this;
    }

    public TomcatBoot useWebFragmentsDetect() {
        useWebFragmentsDetect = true;
        return this;
    }

    public TomcatBoot configure(String configFile) {
        if (configFile == null || configFile.trim().length() == 0) {
            throw new IllegalArgumentException("The argument 'configFile' should not be null or empty: " + configFile);
        }
        this.configFile = configFile;
        return this;
    }

    public TomcatBoot logging(String loggingFile, Consumer<TomcatLoggingOption> opLambda) {
        if (loggingFile == null || loggingFile.trim().length() == 0) {
            throw new IllegalArgumentException("The argument 'loggingFile' should not be null or empty: " + loggingFile);
        }
        if (opLambda == null) {
            throw new IllegalArgumentException("The argument 'opLambda' should not be null.");
        }
        this.loggingFile = loggingFile;
        this.loggingOptionCall = opLambda;
        return this;
    }

    // ===================================================================================
    //                                                                               Boot
    //                                                                              ======
    public TomcatBoot bootAwait() {
        ready();
        go();
        await();
        return this;
    }

    // -----------------------------------------------------
    //                                                 Ready
    //                                                 -----
    public void ready() { // public as parts
        loadServerConfigIfNeeds();
        loadServerLoggingIfNeeds();
    }

    protected void loadServerConfigIfNeeds() {
        if (configFile == null) {
            return;
        }
        configProps = new Properties();
        resolvedConfigFile = resolveConfigEnvPath(configFile);
        final InputStream ins = getClass().getClassLoader().getResourceAsStream(resolvedConfigFile);
        if (ins == null) {
            throw new IllegalStateException("Not found the config file in classpath: " + resolvedConfigFile);
        }
        try {
            configProps.load(ins);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load the config resource as stream: " + resolvedConfigFile, e);
        }
    }

    protected void loadServerLoggingIfNeeds() { // should be called after configuration
        if (loggingFile != null) {
            createServerLoggingLoader().loadServerLogging();
            logger = Logger.getLogger(getClass().getPackage().getName());
        }
    }

    protected ServerLoggingLoader createServerLoggingLoader() {
        return new ServerLoggingLoader(loggingFile, loggingOptionCall, configProps, msg -> info(msg));
    }

    // -----------------------------------------------------
    //                                                  Go
    //                                                ------
    public void go() { // public as parts, no wait
        info("...Booting the Tomcat: port=" + port + " contextPath=" + contextPath);
        if (development) {
            registerShutdownHook();
        }
        prepareServer();
        final URI uri = startServer();
        info("Boot successful" + (development ? " as development" : "") + ": url -> " + uri);
        if (development) {
            browseOnDesktop(uri);
        }
    }

    protected void prepareServer() {
        server = createTomcat();
        server.setPort(port);
        adjustServer();
        setupWebappContext();
        setupServerConfigIfNeeds();
    }

    protected void adjustServer() {
        disableUnpackWARs();
    }

    protected void disableUnpackWARs() {
        final Host host = server.getHost();
        if (host instanceof StandardHost) {
            // suppress ExpandWar's IOException, originally embedded so unneeded
            ((StandardHost) host).setUnpackWARs(false);
        }
    }

    protected void setupWebappContext() {
        try {
            final String warPath = prepareWarPath();
            if (warPath.endsWith(".war")) {
                server.addWebapp(contextPath, warPath);
            } else {
                final String docBase = new File(prepareWebappPath()).getAbsolutePath();
                final Context context = server.addWebapp(contextPath, docBase);
                context.getServletContext().setAttribute(Globals.ALT_DD_ATTR, prepareWebXmlPath());
            }
        } catch (ServletException e) {
            throw new IllegalStateException("Failed to set up web context.", e);
        }
    }

    protected Tomcat createTomcat() {
        final AnnotationHandling annotationHandling = prepareAnnotationHandling();
        final MetaInfoResourceHandling metaInfoResourceHandling = prepareMetaInfoResourceHandling();
        final TldHandling tldHandling = prepareTldHandling();
        final WebFragmentsHandling webFragmentsHandling = prepareuseWebFragmentsHandling();
        return newRhythmicalTomcat(annotationHandling, metaInfoResourceHandling, tldHandling, webFragmentsHandling);
    }

    protected RhythmicalTomcat newRhythmicalTomcat(AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling,
            TldHandling tldHandling, WebFragmentsHandling webFragmentsHandling) {
        return new RhythmicalTomcat(annotationHandling, metaInfoResourceHandling, tldHandling, webFragmentsHandling);
    }

    protected AnnotationHandling prepareAnnotationHandling() {
        return useAnnotationDetect ? AnnotationHandling.DETECT : AnnotationHandling.NONE;
    }

    protected MetaInfoResourceHandling prepareMetaInfoResourceHandling() {
        return useMetaInfoResourceDetect ? MetaInfoResourceHandling.DETECT : MetaInfoResourceHandling.NONE;
    }

    protected TldHandling prepareTldHandling() {
        return useTldDetect ? TldHandling.DETECT : TldHandling.NONE;
    }

    protected WebFragmentsHandling prepareuseWebFragmentsHandling() {
        return useWebFragmentsDetect ? WebFragmentsHandling.DETECT : WebFragmentsHandling.NONE;
    }

    // -----------------------------------------------------
    //                                                 Await
    //                                                 -----
    public void await() { // public as parts
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
    //                                                                Set up Configuration
    //                                                                ====================
    protected void setupServerConfigIfNeeds() {
        if (configProps == null) {
            return;
        }
        info("...Reflecting configuration to server: " + resolvedConfigFile);
        reflectConfigToServer(server, server.getConnector(), configProps);
    }

    protected void reflectConfigToServer(Tomcat server, Connector connector, Properties props) { // you can override
        final String uriEncoding = props.getProperty("tomcat.URIEncoding");
        if (uriEncoding != null) {
            info(" tomcat.URIEncoding = " + uriEncoding);
            connector.setURIEncoding(uriEncoding);
        }
        final String useBodyEncodingForURI = props.getProperty("tomcat.useBodyEncodingForURI");
        if (useBodyEncodingForURI != null) {
            info(" tomcat.useBodyEncodingForURI = " + useBodyEncodingForURI);
            connector.setUseBodyEncodingForURI(useBodyEncodingForURI.equalsIgnoreCase("true"));
        }
    }

    // -----------------------------------------------------
    //                                   Resolve Environment
    //                                   -------------------
    protected String resolveConfigEnvPath(String envPath) { // almost same as Lasta Di's logic
        if (envPath == null) {
            throw new IllegalArgumentException("The argument 'envPath' should not be null.");
        }
        final String configEnv = getConfigEnv();
        final String envMark = "_env.";
        if (configEnv != null && envPath.contains(envMark)) {
            // e.g. maihama_env.properties to maihama_env_production.properties
            final int markIndex = envPath.indexOf(envMark);
            final String front = envPath.substring(0, markIndex);
            final String rear = envPath.substring(markIndex + envMark.length());
            return front + "_env_" + configEnv + "." + rear;
        } else {
            return envPath;
        }
    }

    protected String getConfigEnv() { // null allowed
        return System.getProperty("lasta.env"); // uses Lasta Di's as default
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
    //                                                                         Information
    //                                                                         ===========
    protected void info(String msg) {
        if (logger != null) {
            logger.info(msg);
        } else {
            println(msg);
        }
    }

    protected void println(String msg) {
        System.out.println(msg); // console as default not to depends specific logger
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Tomcat getServer() {
        return server;
    }
}