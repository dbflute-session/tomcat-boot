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
package org.dbflute.tomcat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.dbflute.tomcat.util.BotmResourceUtil;

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
    protected String baseDir;

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

    public TomcatBoot atBaseDir(String baseDir) {
        this.baseDir = baseDir;
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
        return new ServerLoggingLoader(loggingFile, loggingOptionCall, configProps, msg -> println(msg));
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
        if (baseDir != null) {
            server.setBaseDir(baseDir);
        }
        adjustServer();
        setupWebappContext();
        setupServerConfigIfNeeds();
    }

    protected void adjustServer() {
        if (isUnpackWARsDisabled()) {
            disableUnpackWARsOption();
        }
    }

    protected void setupWebappContext() {
        try {
            final String warPath = prepareWarPath();
            if (warPath.endsWith(".war")) {
                server.addWebapp(contextPath, warPath);
                if (!isUnpackWARsDisabled()) {
                    prepareUnpackWARsEnv();
                }
            } else {
                final String webappPath = prepareWebappPath();
                final String docBase = new File(webappPath).getAbsolutePath();
                final Context context = server.addWebapp(contextPath, docBase);
                final String webXmlPath = prepareWebXmlPath(webappPath);
                context.getServletContext().setAttribute(Globals.ALT_DD_ATTR, webXmlPath);
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
        return deriveWebappDir().getPath();
    }

    protected String prepareWebXmlPath(String webappPath) {
        return webappPath + "/WEB-INF/web.xml";
    }

    protected File deriveWebappDir() {
        final String webappRelativePath = getBasicWebappRelativePath();
        final File webappDir = new File(webappRelativePath);
        if (webappDir.exists()) { // from current directory
            return webappDir;
        }
        final File projectWebappDir = findProjectWebappDir(webappRelativePath); // from build path
        if (projectWebappDir != null) {
            return projectWebappDir;
        }
        throw new IllegalStateException("Not found the webapp directory: " + webappDir);
    }

    protected String getBasicWebappRelativePath() {
        return "./src/main/webapp";
    }

    protected File findProjectWebappDir(String webappRelativePath) {
        info("...Finding project webapp from stack trace: webappRelativePath=" + webappRelativePath);
        final StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) { // just in case
            info("*Not found the stack trace: " + stackTrace);
            return null;
        }
        // IntelliJ calls from own main() so find nearest main()
        StackTraceElement rootElement = null;
        for (int i = 0; i < stackTrace.length; i++) {
            final StackTraceElement element = stackTrace[i];
            if ("main".equals(element.getMethodName())) {
                rootElement = element;
                break;
            }
        }
        if (rootElement == null) { // just in case
            info("*Not found the main method: " + Stream.of(stackTrace).map(el -> {
                return el.getMethodName();
            }).collect(Collectors.joining(",")));
            return null;
        }
        final String className = rootElement.getClassName(); // e.g. DocksideBoot
        final Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException continued) {
            info("*Not found the class: " + className + " :: " + continued.getMessage());
            return null;
        }
        final File buildDir = BotmResourceUtil.getBuildDir(clazz); // target/classes
        final File targetDir = buildDir.getParentFile(); // target
        if (targetDir == null) { // just in case
            info("*Not found the target directory: buildDir=" + buildDir);
            return null;
        }
        final File projectDir = targetDir.getParentFile(); // e.g. maihama-dockside
        if (projectDir == null) { // just in case
            info("*Not found the project directory: targetDir=" + targetDir);
            return null;
        }
        final String projectPath;
        try {
            projectPath = projectDir.getCanonicalPath().replace("\\", "/");
        } catch (IOException continued) {
            info("*Cannot get canonical path from: " + projectDir + " :: " + continued.getMessage());
            return null;
        }
        final String projectWebappPath = projectPath + "/" + webappRelativePath;
        final File projectWebappDir = new File(projectWebappPath);
        if (projectWebappDir.exists()) {
            info("OK, found the project webapp: " + projectWebappPath);
            return projectWebappDir;
        } else {
            info("*Not found the project webapp by derived path: " + projectWebappPath);
            return null;
        }
    }

    // ===================================================================================
    //                                                                          UnpackWARs
    //                                                                          ==========
    protected boolean isUnpackWARsDisabled() {
        return false;
    }

    protected void disableUnpackWARsOption() {
        final Host host = server.getHost();
        if (host instanceof StandardHost) {
            info("...Disabling unpackWARs");
            ((StandardHost) host).setUnpackWARs(false);
        }
    }

    protected void prepareUnpackWARsEnv() { // to avoid IOException failure of making directory
        final Host host = server.getHost();
        final File appBaseFile = host.getAppBaseFile(); // e.g. .../tomcat.8080/webapps
        if (appBaseFile.exists()) {
            cleanPreviousExtractedWarDir(appBaseFile);
        }
        // embedded Tomcat cannot make the directory, so make here
        // see ExpandWar.java for the detail: if(!docBase.mkdir() && !docBase.isDirectory())
        info("...Making unpackWARs directory: " + appBaseFile);
        appBaseFile.mkdirs();
    }

    protected void cleanPreviousExtractedWarDir(File appBaseFile) {
        final String appsPath = appBaseFile.getAbsolutePath().replace("\\", "/");
        if (!appsPath.contains("/webapps")) { // just in case
            return;
        }
        final String parentPath = appsPath.substring(0, appsPath.lastIndexOf("/webapps")); // e.g. .../tomcat.8080
        if (!parentPath.contains("/")) { // just in case
            return;
        }
        final String parentName = parentPath.substring(parentPath.lastIndexOf("/") + "/".length());
        if (!parentName.startsWith("tomcat.")) { // just in case
            return;
        }
        // here e.g. tomcat.8080
        try {
            // clean work and extracted resources
            info("...Cleaning previous extracted-war directory: " + parentPath);
            Files.walkFileTree(Paths.get(parentPath), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw exc;
                    }
                }
            });
        } catch (IOException continued) {
            info("*Failed to delete previous directory: " + continued.getMessage());
            return;
        }
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
            if (loggingFile == null) { // no logger settings
                println(msg); // as default
            }
            // no output before logger ready
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