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
package org.dbflute.tomcat;

import java.io.File;
import java.io.IOException;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;
import org.dbflute.tomcat.core.RhythmicalTomcat;
import org.dbflute.tomcat.core.accesslog.AccessLogOption;
import org.dbflute.tomcat.core.likeit.LikeItCatalinaSetupper;
import org.dbflute.tomcat.core.valve.YourValveOption;
import org.dbflute.tomcat.logging.BootLogger;
import org.dbflute.tomcat.logging.TomcatLoggingOption;
import org.dbflute.tomcat.props.BootPropsTranslator;
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
    protected final String contextPath; // not null

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    protected boolean development;
    protected boolean browseOnDesktop;
    protected boolean suppressShutdownHook;
    protected boolean useAnnotationDetect;
    protected boolean useMetaInfoResourceDetect;
    protected boolean useTldDetect;
    protected Predicate<String> tldFilesSelector; // null allowed
    protected boolean useWebFragmentsDetect;
    protected Predicate<String> webFragmentsSelector; // null allowed
    protected String configFile; // null allowed
    protected String[] extendsConfigFiles; // null allowed
    protected String loggingFile; // null allowed
    protected Consumer<TomcatLoggingOption> loggingOptionCall; // null allowed (but not null if loggingFile exists)
    protected String baseDir; // null allowed
    protected YourValveOption yourValveOption; // null allowed
    protected LikeItCatalinaSetupper likeitCatalinaSetupper; // null allowed

    // -----------------------------------------------------
    //                                              Stateful
    //                                              --------
    protected Properties configProps; // null allowed (but not null if configFile exists after ready)
    protected List<String> readConfigList; // null allowed (but not null if configFile exists after ready), basically for logging
    protected BootLogger bootLogger; // not null after ready
    protected Tomcat server; // not null after preparing server

    // -----------------------------------------------------
    //                                              Follower
    //                                              --------
    protected final BootPropsTranslator propsTranslator = createBootPropsTranslator();

    protected BootPropsTranslator createBootPropsTranslator() {
        return new BootPropsTranslator();
    }

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    /**
     * Create with port number and context path.
     * <pre>
     * e.g. has context path
     *  TomcatBoot boot = new TomcatBoot(8152, "/fortress");
     * 
     * e.g. no context path
     *  TomcatBoot boot = new TomcatBoot(8152, "");
     * </pre>
     * @param port The port number for the tomcat server.
     * @param contextPath The context path for the tomcat server, basically has slash prefix. (NotNull, EmptyAllowed)
     */
    public TomcatBoot(int port, String contextPath) {
        if (contextPath == null) {
            throw new IllegalArgumentException("The argument 'contextPath' should not be null.");
        }
        this.port = port;
        this.contextPath = contextPath;
    }

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    /**
     * Does it boot the tomcat server as development mode?
     * @return this. (NotNull)
     */
    public TomcatBoot asDevelopment() {
        development = true;
        return this;
    }

    /**
     * Does it boot the tomcat server as development mode?
     * @param development Is it development mode?
     * @return this. (NotNull)
     */
    public TomcatBoot asDevelopment(boolean development) {
        this.development = development;
        return this;
    }

    /**
     * Browse on desktop automatically after boot finished.
     * @return this. (NotNull)
     */
    public TomcatBoot browseOnDesktop() {
        // wants to use this in production (e.g. DBFlute Intro) 
        //assertDevelopmentState();
        browseOnDesktop = true;
        return this;
    }

    /**
     * Suppress shutdown hook. (for development only)
     * @return this. (NotNull)
     */
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

    /**
     * You can detect annotations in all jar files.
     * @return this. (NotNull)
     */
    public TomcatBoot useAnnotationDetect() {
        useAnnotationDetect = true;
        return this;
    }

    /**
     * You can detect 'META-INF' resources in jar files detected as web fragments. <br>
     * <span style="color: #CC4747; font-size: 120%">So you also needs to enable web fragments detect.</span>
     * @return this. (NotNull)
     */
    public TomcatBoot useMetaInfoResourceDetect() { // also needs web fragments detect
        useMetaInfoResourceDetect = true;
        return this;
    }

    /**
     * You can detect '.tdl' files in all jar files.
     * @return this. (NotNull)
     */
    public TomcatBoot useTldDetect() {
        useTldDetect = true;
        return this;
    }

    /**
     * You can detect '.tdl' files in selected jar files.
     * <pre>
     * boot.useTldDetect(jarName -&gt; {
     *     return jarName.contains("lasta-taglib");
     * });
     * </pre>
     * @param oneArgLambda The callback for selector of tld files, argument is jar name. (NotNull)
     * @return this. (NotNull)
     */
    public TomcatBoot useTldDetect(Predicate<String> oneArgLambda) { // you can select
        useTldDetect = true;
        tldFilesSelector = oneArgLambda;
        return this;
    }

    /**
     * You can detect web fragments in all jar files.
     * @return this. (NotNull)
     */
    public TomcatBoot useWebFragmentsDetect() { // without filter (so all jar files are scanned)
        useWebFragmentsDetect = true;
        return this;
    }

    /**
     * You can detect web fragments in selected jar files.
     * <pre>
     * boot.useMetaInfoResourceDetect().useWebFragmentsDetect(jarName -&gt; { // for swagger
     *     return jarName.contains("swagger-ui");
     * });
     * </pre>
     * @param oneArgLambda The callback for selector of web fragments, argument is jar name. (NotNull)
     * @return this. (NotNull)
     */
    public TomcatBoot useWebFragmentsDetect(Predicate<String> oneArgLambda) { // you can select
        useWebFragmentsDetect = true;
        webFragmentsSelector = oneArgLambda;
        return this;
    }

    /**
     * You can configure tomcat options by application properties.
     * <pre>
     * boot.configure("fortress_config.properties", "fortress_env.properties");
     * </pre>
     * @param configFile The path of configuration file in classpath. (NotNull)
     * @param extendsConfigFiles The paths of super configuration files. (NotNull, EmptyAllowed)
     * @return this. (NotNull)
     */
    public TomcatBoot configure(String configFile, String... extendsConfigFiles) {
        if (configFile == null || configFile.trim().length() == 0) {
            throw new IllegalArgumentException("The argument 'configFile' should not be null or empty: " + configFile);
        }
        if (extendsConfigFiles == null) {
            throw new IllegalArgumentException("The argument 'extendsConfigFiles' should not be null or empty: configFile=" + configFile);
        }
        this.configFile = configFile;
        this.extendsConfigFiles = extendsConfigFiles;
        return this;
    }

    /**
     * You can set tomcat logging by application properties.
     * <pre>
     * boot.logging("tomcat_logging.properties", op -&gt; {
     *     op.replace("tomcat.log.name", "catalina_out");
     * }); // uses jdk14logger
     * </pre>
     * @param loggingFile The path of logging file in classpath. (NotNull)
     * @param opLambda The callback for logging option. (NotNull)
     * @return this. (NotNull)
     */
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

    /**
     * Set base directory of tomcat server.
     * @param baseDir The base directory for Tomcat@setBaseDir(). (NotNull)
     * @return this. (NotNull)
     */
    public TomcatBoot atBaseDir(String baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    /**
     * Add your valve for tomcat server. (can be called several times)
     * @param yourValve The your valve. (NotNull)
     * @return this. (NotNull)
     */
    public TomcatBoot valve(Valve yourValve) {
        if (yourValve == null) {
            throw new IllegalArgumentException("The argument 'yourValve' should not be null.");
        }
        if (yourValveOption == null) {
            yourValveOption = new YourValveOption();
        }
        yourValveOption.valve(yourValve);
        return this;
    }

    /**
     * You can customize tomcat resource (e.g. host, context) as you like (it).
     * @param resourceLambda The setupper of tomcat resource (e.g. host, context). (NotNull)
     * @return this. (NotNull)
     */
    public TomcatBoot asYouLikeIt(LikeItCatalinaSetupper resourceLambda) {
        if (resourceLambda == null) {
            throw new IllegalArgumentException("The argument 'resourceLambda' should not be null.");
        }
        likeitCatalinaSetupper = resourceLambda;
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
        readConfigList = new ArrayList<String>();
        if (extendsConfigFiles != null && extendsConfigFiles.length > 0) {
            final List<String> extendsConfigFileList = new ArrayList<String>(Arrays.asList(extendsConfigFiles));
            Collections.reverse(extendsConfigFileList); // to read from parent e.g. [maihama_env.properites, dockside_env.properites]
            for (String extendsConfigFile : extendsConfigFileList) {
                final String extendsResolvedFile = resolveConfigEnvPath(extendsConfigFile);
                readConfigList.add(extendsResolvedFile);
                configProps.putAll(readConfigProps(extendsResolvedFile)); // override old value by new value
            }
        }
        final String resolvedFile = resolveConfigEnvPath(configFile); // main properties
        readConfigList.add(resolvedFile);
        configProps.putAll(readConfigProps(resolvedFile)); // override old value by new value
        Collections.reverse(readConfigList); // for e.g. [dockside_env.properites, maihama_env.properites]
    }

    protected String resolveConfigEnvPath(String envPath) { // almost same as Lasta Di's logic
        return propsTranslator.resolveConfigEnvPath(envPath);
    }

    protected Properties readConfigProps(String propFile) {
        return propsTranslator.readConfigProps(propFile);
    }

    protected void loadServerLoggingIfNeeds() { // should be called after configuration
        bootLogger = new BootLogger(loggingFile, loggingOptionCall, configProps);
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
        final String warPath = prepareWarPath();
        try {
            if (warPath.endsWith(".war")) {
                doSetupWebappContextWar(warPath);
            } else {
                doSetupWebappContextWebappDir();
            }
        } catch (ServletException e) {
            throw new IllegalStateException("Failed to set up web context: warPath=" + warPath, e);
        }
    }

    protected void doSetupWebappContextWar(String warPath) throws ServletException {
        server.addWebapp(contextPath, warPath);
        if (!isUnpackWARsDisabled()) {
            prepareUnpackWARsEnv();
        }
    }

    protected void doSetupWebappContextWebappDir() throws ServletException {
        final String webappPath = prepareWebappPath();
        final String docBase = new File(webappPath).getAbsolutePath();
        final Context context = server.addWebapp(contextPath, docBase);
        final String webXmlPath = prepareWebXmlPath(webappPath);
        context.getServletContext().setAttribute(Globals.ALT_DD_ATTR, webXmlPath);
    }

    protected Tomcat createTomcat() {
        final AnnotationHandling annotationHandling = prepareAnnotationHandling();
        final MetaInfoResourceHandling metaInfoResourceHandling = prepareMetaInfoResourceHandling();
        final TldHandling tldHandling = prepareTldHandling();
        final Predicate<String> tldFilesSelector = prepareTldFilesSelector();
        final WebFragmentsHandling webFragmentsHandling = prepareuseWebFragmentsHandling();
        final Predicate<String> webFragmentsSelector = prepareWebFragmentsSelector(); // null allowed
        final AccessLogOption accessLogOption = prepareAccessLogOption(); // null allowed
        final YourValveOption yourValveOption = prepareYourValveOption(); // null allowed
        final LikeItCatalinaSetupper likeitCatalinaSetupper = prepareLikeItCatalinaSetupper(); // null allowed
        return newRhythmicalTomcat(bootLogger // has many arguments
                , annotationHandling, metaInfoResourceHandling // meta
                , tldHandling, tldFilesSelector // taglib files
                , webFragmentsHandling, webFragmentsSelector // web fragments
                , accessLogOption, yourValveOption, likeitCatalinaSetupper // options
        );
    }

    protected RhythmicalTomcat newRhythmicalTomcat(BootLogger bootLogger // logging
            , AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling // meta
            , TldHandling tldHandling, Predicate<String> tldFilesSelector // tld files
            , WebFragmentsHandling webFragmentsHandling, Predicate<String> webFragmentsSelector // web fragments
            , AccessLogOption accessLogOption, YourValveOption yourValveOption, LikeItCatalinaSetupper likeitCatalinaSetupper // options
    ) {
        return new RhythmicalTomcat(bootLogger // has many arguments
                , annotationHandling, metaInfoResourceHandling // meta
                , tldHandling, tldFilesSelector // taglib files
                , webFragmentsHandling, webFragmentsSelector // web fragments
                , accessLogOption, yourValveOption, likeitCatalinaSetupper // options
        );
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

    protected Predicate<String> prepareTldFilesSelector() {
        return tldFilesSelector; // null allowed
    }

    protected WebFragmentsHandling prepareuseWebFragmentsHandling() {
        return useWebFragmentsDetect ? WebFragmentsHandling.DETECT : WebFragmentsHandling.NONE;
    }

    protected Predicate<String> prepareWebFragmentsSelector() {
        return webFragmentsSelector; // null allowed
    }

    protected AccessLogOption prepareAccessLogOption() {
        return propsTranslator.prepareAccessLogOption(bootLogger, configProps, readConfigList); // null allowed
    }

    protected YourValveOption prepareYourValveOption() {
        return yourValveOption; // null allowed
    }

    protected LikeItCatalinaSetupper prepareLikeItCatalinaSetupper() {
        return likeitCatalinaSetupper; // null allowed
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
        propsTranslator.setupServerConfigIfNeeds(bootLogger, server, server.getConnector(), configProps, readConfigList);
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
        final String scheme = server.getConnector().getScheme();
        final String uri = scheme + "://" + server.getHost().getName() + ":" + port + contextPath;
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
        try {
            server.destroy(); // since 0.7.2, it needs since Tomcat-9.0.[16/17]!? (could not boot new instance)
        } catch (Exception e) {
            throw new IllegalStateException("Failed to destroy the Tomcat.", e);
        }
    }

    // ===================================================================================
    //                                                                         Information
    //                                                                         ===========
    protected void info(String msg) {
        bootLogger.info(msg);
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Tomcat getServer() {
        return server;
    }
}