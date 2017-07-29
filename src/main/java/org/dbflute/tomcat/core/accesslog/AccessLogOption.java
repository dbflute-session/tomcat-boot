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
package org.dbflute.tomcat.core.accesslog;

/**
 * @author jflute
 * @since 0.5.6 (2017/07/29 Saturday at higashi ginza)
 */
public class AccessLogOption {

    //  basically default value exists in tomcat
    protected String logDir; // null allowed
    protected String formatPattern; // null allowed
    protected String fileEncoding; // null allowed

    public AccessLogOption logDir(String logDir) {
        this.logDir = logDir;
        return this;
    }

    public AccessLogOption formatPattern(String formatPattern) {
        this.formatPattern = formatPattern;
        return this;
    }

    public AccessLogOption fileEncoding(String fileEncoding) {
        this.fileEncoding = fileEncoding;
        return this;
    }

    public String getLogDir() {
        return logDir;
    }

    public String getFormatPattern() {
        return formatPattern;
    }

    public String getFileEncoding() {
        return fileEncoding;
    }
}
