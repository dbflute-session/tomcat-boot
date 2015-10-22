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
package org.dbflute.tomcat.logging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.LogManager;
import java.util.regex.Pattern;

/**
 * @author jflute
 */
public class ServerLoggingLoader {

    protected final String loggingFile; // not null
    protected final Consumer<TomcatLoggingOption> loggingOptionCall; // not null
    protected final Properties configProps; // null allowed
    protected final Consumer<String> coreLogger; // not null

    public ServerLoggingLoader(String loggingFile, Consumer<TomcatLoggingOption> loggingOptionCall, Properties configProps,
            Consumer<String> coreLogger) {
        this.loggingFile = loggingFile;
        this.loggingOptionCall = loggingOptionCall;
        this.configProps = configProps;
        this.coreLogger = coreLogger;
    }

    public void loadServerLogging() { // should be called after configuration
        try (InputStream ins = getClass().getClassLoader().getResourceAsStream(loggingFile)) { // thanks, fess
            if (ins == null) {
                throw new IllegalStateException("Not found the logging file in classpath: " + loggingFile);
            }
            final String encoding = "UTF-8";
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                final ByteBuffer buffer = ByteBuffer.allocate(4096);
                final byte[] buf = buffer.array();
                int len;
                while ((len = ins.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                String text = out.toString(encoding);
                final TomcatLoggingOption option = new TomcatLoggingOption();
                loggingOptionCall.accept(option);
                final Map<String, String> replaceMap = option.getReplaceMap();
                if (replaceMap != null) {
                    for (Entry<String, String> entry : replaceMap.entrySet()) {
                        final String key = entry.getKey();
                        text = text.replaceAll(Pattern.quote("${" + key + "}"), entry.getValue());
                    }
                }
                if (configProps != null) {
                    for (Entry<Object, Object> entry : configProps.entrySet()) {
                        final String key = (String) entry.getKey();
                        text = text.replaceAll(Pattern.quote("${" + key + "}"), (String) entry.getValue());
                    }
                }
                coreLogger.accept("...Setting tomcat logging configuration: " + loggingFile);
                LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(text.getBytes(encoding)));
            }
        } catch (Exception e) {
            handleLoggingSetupFailureException(e);
        }
    }

    protected void handleLoggingSetupFailureException(Exception e) {
        throw new IllegalStateException("Failed to load tomcat logging configuration: " + loggingFile, e);
    }
}
