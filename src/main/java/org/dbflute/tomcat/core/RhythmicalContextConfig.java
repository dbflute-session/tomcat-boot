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
package org.dbflute.tomcat.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.catalina.startup.ContextConfig;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;

/**
 * @author jflute
 */
public class RhythmicalContextConfig extends ContextConfig {

    protected final AnnotationHandling annotationHandling;
    protected final MetaInfoResourceHandling metaInfoResourceHandling;
    protected final TldHandling tldHandling;
    protected final WebFragmentsHandling webFragmentsHandling;

    public RhythmicalContextConfig(AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling,
            TldHandling tldHandling, WebFragmentsHandling webFragmentsHandling) {
        this.annotationHandling = annotationHandling;
        this.metaInfoResourceHandling = metaInfoResourceHandling;
        this.tldHandling = tldHandling;
        this.webFragmentsHandling = webFragmentsHandling;
    }

    @Override
    protected Map<String, WebXml> processJarsForWebFragments(WebXml application) {
        if (WebFragmentsHandling.DETECT.equals(webFragmentsHandling)) {
            return super.processJarsForWebFragments(application);
        }
        return new HashMap<String, WebXml>(2);
    }

    @Override
    protected void processServletContainerInitializers() {
        // initializers are needed for tld search
        if (isAvailableInitializers()) {
            super.processServletContainerInitializers();
        }
        removeJettyInitializer();
    }

    protected boolean isAvailableInitializers() {
        return AnnotationHandling.DETECT.equals(annotationHandling) // e.g. Servlet annotation
                || TldHandling.DETECT.equals(tldHandling) // .tld in jar files
                ;
    }

    protected void removeJettyInitializer() {
        initializerClassMap.keySet().stream().filter(initializer -> {
            return initializer.getClass().getName().startsWith("org.eclipse.jetty");
        }).collect(Collectors.toList()).forEach(initializer -> {
            initializerClassMap.remove(initializer);
        });
    }

    @Override
    protected void processAnnotations(Set<WebXml> fragments, boolean handlesTypesOnly) {
        if (AnnotationHandling.DETECT.equals(annotationHandling)) {
            super.processAnnotations(fragments, handlesTypesOnly);
        }
    }

    @Override
    protected void processResourceJARs(Set<WebXml> fragments) {
        if (MetaInfoResourceHandling.DETECT.equals(metaInfoResourceHandling)) {
            super.processResourceJARs(fragments);
        }
    }
}
