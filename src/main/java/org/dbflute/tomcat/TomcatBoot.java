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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 */
public class TomcatBoot {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(TomcatBoot.class);
    protected static final String DEFAULT_MARK_DIR = "/tmp/dbflute/tomcatboot";

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final int port;
    protected final String contextPath;
    protected boolean development;
    protected boolean browseOnDesktop;
    protected boolean suppressShutdownHook;

    protected Tomcat server;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    // TODO jflute tomcat_boot: want to suppress annotation search
    public TomcatBoot(int port, String contextPath) {
        this.port = port;
        this.contextPath = contextPath;
    }

    public TomcatBoot asDevelopment() {
        development = true;
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

    // ===================================================================================
    //                                                                               Boot
    //                                                                              ======
    public TomcatBoot bootAwait() {
        startBoot();
        await();
        return this;
    }

    public void startBoot() { // no wait
        logger.info("...Booting the Tomcat: port={} contextPath={}", port, contextPath);
        if (development) {
            registerShutdownHook();
        }
        prepareServer();
        final URI uri = startServer();
        logger.info("Boot successful{}: uri={}", development ? " as development" : "", uri);
        if (development) {
            browseOnDesktop(uri);
        }
    }

    protected void prepareServer() {
        server = new Tomcat();
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
        webContext.setJarScanner(null);
    }

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
        logger.info("...Registering the shutdown hook for the Tomcat: lastModified=" + exp);
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
        logger.info("...Shuting down the Tomcat forcedly: port=" + port);
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
        return 2000L;
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
}