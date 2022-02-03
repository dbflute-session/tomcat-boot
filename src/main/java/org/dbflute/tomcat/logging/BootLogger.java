/*
 * Copyright 2015-2022 the original author or authors.
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
package org.dbflute.tomcat.logging;

import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * @author jflute at showbase
 * @since 0.5.1 (2016/11/06 Sunday)
 */
public class BootLogger {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String loggingFile; // null allowed
    protected final Consumer<TomcatLoggingOption> loggingOptionCall; // null allowed (but not null if loggingFile exists)
    protected final Properties configProps; // not null
    protected final Logger logger; // null allowed

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public BootLogger(String loggingFile, Consumer<TomcatLoggingOption> loggingOptionCall, Properties configProps) {
        this.loggingFile = loggingFile;
        this.loggingOptionCall = loggingOptionCall;
        this.configProps = configProps;
        if (loggingFile != null) {
            // #hope console settings will be overridden as tomcat default after creating tomcat instance... by jflute (2017/07/29)
            // so your formatter setting is invalid in console, wants to fix it future
            createServerLoggingLoader().loadServerLogging();
            this.logger = Logger.getLogger(getClass().getPackage().getName());
        } else {
            this.logger = null;
        }
    }

    protected ServerLoggingLoader createServerLoggingLoader() {
        return new ServerLoggingLoader(loggingFile, loggingOptionCall, configProps, msg -> println(msg));
    }

    // ===================================================================================
    //                                                                             Logging
    //                                                                             =======
    public void info(String msg) {
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
}
