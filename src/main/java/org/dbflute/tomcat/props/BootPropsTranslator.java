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
package org.dbflute.tomcat.props;

import java.util.Properties;
import java.util.function.Consumer;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.dbflute.tomcat.core.accesslog.AccessLogOption;
import org.dbflute.tomcat.logging.BootLogger;

/**
 * @author jflute
 * @since 0.5.6 (2017/07/29 Saturday at higashi ginza)
 */
public class BootPropsTranslator {

    // ===================================================================================
    //                                                      Configuration Environment Path
    //                                                      ==============================
    public String resolveConfigEnvPath(String envPath) { // almost same as Lasta Di's logic
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
    //                                                                   Access Log Option
    //                                                                   =================
    public AccessLogOption prepareAccessLogOption(BootLogger logger, Properties props, String resolvedConfigFile) { // null allowed
        if (props == null) {
            return null;
        }
        final String enabled = props.getProperty("tomcat.accesslog.enabled");
        if (enabled == null || !enabled.equalsIgnoreCase("true")) {
            return null;
        }
        logger.info("...Preparing tomcat access log: enabled=" + enabled + ", config=" + resolvedConfigFile);
        final AccessLogOption option = new AccessLogOption();
        doPrepareAccessLogOption(logger, props, "logDir", value -> option.logDir(value));
        doPrepareAccessLogOption(logger, props, "filePrefix", value -> option.filePrefix(value));
        doPrepareAccessLogOption(logger, props, "fileSuffix", value -> option.fileSuffix(value));
        doPrepareAccessLogOption(logger, props, "fileDateFormat", value -> option.fileDateFormat(value));
        doPrepareAccessLogOption(logger, props, "fileEncoding", value -> option.fileEncoding(value));
        doPrepareAccessLogOption(logger, props, "formatPattern", value -> option.formatPattern(value));
        return option;
    }

    protected void doPrepareAccessLogOption(BootLogger logger, Properties props, String keyword, Consumer<String> reflector) {
        final String value = props.getProperty("tomcat.accesslog." + keyword);
        if (value != null) {
            logger.info(" tomcat.accesslog." + keyword + " = " + value);
            reflector.accept(value);
        }
    }

    // ===================================================================================
    //                                                                Server Configuration
    //                                                                ====================
    public void setupServerConfigIfNeeds(BootLogger logger, Tomcat server, Connector connector, Properties props,
            String resolvedConfigFile) {
        if (props == null) {
            return;
        }
        logger.info("...Reflecting configuration to server: config=" + resolvedConfigFile);
        final String uriEncoding = props.getProperty("tomcat.URIEncoding");
        if (uriEncoding != null) {
            logger.info(" tomcat.URIEncoding = " + uriEncoding);
            connector.setURIEncoding(uriEncoding);
        }
        final String useBodyEncodingForURI = props.getProperty("tomcat.useBodyEncodingForURI");
        if (useBodyEncodingForURI != null) {
            logger.info(" tomcat.useBodyEncodingForURI = " + useBodyEncodingForURI);
            connector.setUseBodyEncodingForURI(useBodyEncodingForURI.equalsIgnoreCase("true"));
        }
        final String bindAddress = props.getProperty("tomcat.bindAddress");
        if (bindAddress != null) {
            logger.info(" tomcat.bindAddress = " + bindAddress);
            connector.setProperty("address", bindAddress);
        }
    }
}
