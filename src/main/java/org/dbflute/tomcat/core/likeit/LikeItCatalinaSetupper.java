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
package org.dbflute.tomcat.core.likeit;

/**
 * @author jflute
 * @since 0.5.8 (2017/09/06 Wednesday)
 */
public interface LikeItCatalinaSetupper {

    /**
     * Set up the host/context as you like it.
     * @param resource The resource of tomcat server for as-you-like-it. (NotNull)
     */
    void setup(LikeItCatalinaResource resource);
}
