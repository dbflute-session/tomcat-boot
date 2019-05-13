/*
 * Copyright 2015-2019 the original author or authors.
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

import java.util.Optional;

/**
 * @author jflute
 * @since 0.5.6 (2017/07/29 Saturday at higashi ginza)
 */
public class AccessLogOption {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    //  basically default value exists in tomcat
    protected String logDir; // null allowed
    protected String filePrefix; // null allowed
    protected String fileSuffix; // null allowed
    protected String fileDateFormat; // null allowed
    protected String fileEncoding; // null allowed
    protected String formatPattern; // null allowed
    protected String conditionIf; // null allowed
    protected String conditionUnless; // null allowed

    // ===================================================================================
    //                                                                         Easy-to-Use
    //                                                                         ===========
    public AccessLogOption logDir(String logDir) {
        this.logDir = logDir;
        return this;
    }

    public AccessLogOption filePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
        return this;
    }

    public AccessLogOption fileSuffix(String fileSuffix) {
        this.fileSuffix = fileSuffix;
        return this;
    }

    public AccessLogOption fileDateFormat(String fileDateFormat) {
        this.fileDateFormat = fileDateFormat;
        return this;
    }

    public AccessLogOption fileEncoding(String fileEncoding) {
        this.fileEncoding = fileEncoding;
        return this;
    }

    public AccessLogOption formatPattern(String formatPattern) {
        this.formatPattern = formatPattern;
        return this;
    }

    public AccessLogOption conditionIf(String conditionIf) {
        this.conditionIf = conditionIf;
        return this;
    }

    public AccessLogOption conditionUnless(String conditionUnless) {
        this.conditionUnless = conditionUnless;
        return this;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Optional<String> getLogDir() {
        return Optional.ofNullable(logDir);
    }

    public Optional<String> getFilePrefix() {
        return Optional.ofNullable(filePrefix);
    }

    public Optional<String> getFileSuffix() {
        return Optional.ofNullable(fileSuffix);
    }

    public Optional<String> getFileDateFormat() {
        return Optional.ofNullable(fileDateFormat);
    }

    public Optional<String> getFileEncoding() {
        return Optional.ofNullable(fileEncoding);
    }

    public Optional<String> getFormatPattern() {
        return Optional.ofNullable(formatPattern);
    }

    public Optional<String> getConditionIf() {
        return Optional.ofNullable(conditionIf);
    }

    public Optional<String> getConditionUnless() {
        return Optional.ofNullable(conditionUnless);
    }
}
