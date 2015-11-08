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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author jflute
 */
public class LinerLogFormatter extends Formatter {

    protected static Date cachedDate = new Date();
    protected static SimpleDateFormat cachedFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

    // e.g.
    //  2015-10-23 01:59:12,746 [main] INFO (AbstractProtocol@start()) - ...
    @Override
    public synchronized String format(LogRecord record) {
        cachedDate.setTime(record.getMillis());
        final StringBuilder sb = new StringBuilder();
        sb.append(cachedFormat.format(cachedDate));
        sb.append(" [").append(Thread.currentThread().getName()).append("]");
        sb.append(" ").append(record.getLevel().getName());
        sb.append(" (");
        final String className = record.getSourceClassName();
        if (className != null) {
            if (className.contains(".")) {
                sb.append(className.substring(className.lastIndexOf(".") + ".".length()));
            } else {
                sb.append(className);
            }
            final String methodName = record.getSourceMethodName();
            if (methodName != null) {
                sb.append("@").append(methodName).append("()");
            }
        } else {
            sb.append(record.getLoggerName());
        }
        sb.append(") - ").append(formatMessage(record));
        final Throwable thrown = record.getThrown();
        if (thrown != null) {
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(stringWriter);
            try {
                printWriter.println();
                thrown.printStackTrace(printWriter);
            } finally {
                printWriter.close();
            }
            sb.append(stringWriter.toString());
        }
        sb.append("\n");
        return sb.toString();
    }
}
