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
package org.apache.catalina.startup;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.catalina.LifecycleEvent;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.AnnotationHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.MetaInfoResourceHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.TldHandling;
import org.dbflute.tomcat.core.RhythmicalHandlingDef.WebFragmentsHandling;

// use the same package as JavaClassCacheEntry because of package private
/**
 * @author jflute
 */
public class RhythmicalContextConfig extends ContextConfig {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final AnnotationHandling annotationHandling;
    protected final MetaInfoResourceHandling metaInfoResourceHandling;
    protected final TldHandling tldHandling;
    protected final Predicate<String> tldFilesSelector; // null allowed
    protected final WebFragmentsHandling webFragmentsHandling;
    protected final Predicate<String> webFragmentsSelector; // null allowed

    protected boolean alreadyFirstLifecycle; // stateful

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public RhythmicalContextConfig(AnnotationHandling annotationHandling, MetaInfoResourceHandling metaInfoResourceHandling // meta
            , TldHandling tldHandling, Predicate<String> tldFilesSelector // taglib files
            , WebFragmentsHandling webFragmentsHandling, Predicate<String> webFragmentsSelector // web fragments
    ) {
        this.annotationHandling = annotationHandling;
        this.metaInfoResourceHandling = metaInfoResourceHandling;
        this.tldHandling = tldHandling;
        this.tldFilesSelector = tldFilesSelector;
        this.webFragmentsHandling = webFragmentsHandling;
        this.webFragmentsSelector = webFragmentsSelector;
    }

    // ===================================================================================
    //                                                                     Lifecycle Event
    //                                                                     ===============
    @Override
    public void lifecycleEvent(LifecycleEvent event) { // called several times
        super.lifecycleEvent(event);
        if (!alreadyFirstLifecycle) { // ContextConfig is not thread-safe so no care
            alreadyFirstLifecycle = true;
            if (isJarScannerAdjustmentEnabled()) {
                // this jar scanner that the context has is saved in ServletContext
                // so also jasper can uses it (see StandardContext@startInternal())
                final JarScanner jarScanner = extractJarScanner(); // not null
                final JarScanFilter jarScanFilter = jarScanner.getJarScanFilter(); // not null
                jarScanner.setJarScanFilter(createSelectableJarScanFilter(jarScanFilter));
            }
        }
    }

    // -----------------------------------------------------
    //                                           Jar Scanner
    //                                           -----------
    protected boolean isJarScannerAdjustmentEnabled() {
        return isTldFilesSelectorEnabled() || isWebFragmentsSelectorEnabled();
    }

    protected JarScanner extractJarScanner() {
        return context.getJarScanner(); // returns saved instance so you can set options
        // changed to be public before I knew it!?
        //final String methodName = "getJarScanner";
        //try {
        //    final Method getterMethod = BotmReflectionUtil.getWholeMethod(context.getClass(), methodName, (Class<?>[]) null);
        //    return (JarScanner) BotmReflectionUtil.invoke(getterMethod, context, null); // basically not null
        //} catch (RuntimeException e) {
        //    throw new IllegalStateException("Failed to call the method: " + methodName + "() of " + context, e);
        //}
    }

    protected JarScanFilter createSelectableJarScanFilter(JarScanFilter existingFilter) {
        if (existingFilter instanceof SelectableJarScanFilter) { // already wrapped
            return existingFilter;
        }
        return new SelectableJarScanFilter(existingFilter);
    }

    protected class SelectableJarScanFilter implements JarScanFilter {

        protected final JarScanFilter existingFilter; // not null

        public SelectableJarScanFilter(JarScanFilter existingFilter) {
            this.existingFilter = existingFilter;
        }

        public boolean check(JarScanType jarScanType, String jarName) {
            // specifed selector is prior (completely overridding determination)
            if (JarScanType.TLD.equals(jarScanType)) { // means taglib files
                if (isTldFilesSelectorEnabled()) {
                    return tldFilesSelector.test(jarName);
                }
            } else if (JarScanType.PLUGGABILITY.equals(jarScanType)) { // means web fragments
                if (isWebFragmentsSelectorEnabled()) {
                    return webFragmentsSelector.test(jarName);
                }
            }
            return existingFilter.check(jarScanType, jarName);
        }
    }

    // ===================================================================================
    //                                                               Jars for WebFragments
    //                                                               =====================
    @Override
    protected Map<String, WebXml> processJarsForWebFragments(WebXml application, WebXmlParser webXmlParser) {
        if (isWebFragmentsHandlingDetect()) {
            return super.processJarsForWebFragments(application, webXmlParser);
        }
        return new HashMap<String, WebXml>(2); // mutable just in case
    }

    // ===================================================================================
    //                                                                   Servlet Container
    //                                                                   =================
    @Override
    protected void processServletContainerInitializers() {
        if (isAvailableServletContainerInitializers()) {
            super.processServletContainerInitializers();
        }
        removeJettyInitializer();
    }

    protected boolean isAvailableServletContainerInitializers() {
        // o #thinking annotation handling...why needed? (forgotten) by jflute (2019/05/01)
        // o jasper's initializer executes tld scanning so needed here
        return isAnnotationHandlingDetect() || isTldHandlingDetect();
    }

    protected void removeJettyInitializer() {
        initializerClassMap.keySet().stream().filter(initializer -> {
            return initializer.getClass().getName().startsWith("org.eclipse.jetty");
        }).collect(Collectors.toList()).forEach(initializer -> {
            initializerClassMap.remove(initializer);
        });
    }

    // ===================================================================================
    //                                                                         Annotations
    //                                                                         ===========
    @Override
    protected void processAnnotations(Set<WebXml> fragments, boolean handlesTypesOnly, Map<String, JavaClassCacheEntry> javaClassCache) {
        if (isAnnotationHandlingDetect()) {
            super.processAnnotations(fragments, handlesTypesOnly, javaClassCache);
        }
    }

    // ===================================================================================
    //                                                                       Resource JARs
    //                                                                       =============
    @Override
    protected void processResourceJARs(Set<WebXml> fragments) { // fragments from web fragments handling
        if (isMetaInfoResourceHandlingDetect()) {
            super.processResourceJARs(fragments);
        }
    }

    // ===================================================================================
    //                                                                        Assist Logic
    //                                                                        ============
    protected boolean isAnnotationHandlingDetect() {
        return AnnotationHandling.DETECT.equals(annotationHandling);
    }

    protected boolean isMetaInfoResourceHandlingDetect() {
        return MetaInfoResourceHandling.DETECT.equals(metaInfoResourceHandling);
    }

    protected boolean isTldHandlingDetect() {
        return TldHandling.DETECT.equals(tldHandling); // .tld in jar files
    }

    protected boolean isTldFilesSelectorEnabled() {
        return isTldHandlingDetect() && tldFilesSelector != null;
    }

    protected boolean isWebFragmentsHandlingDetect() {
        return WebFragmentsHandling.DETECT.equals(webFragmentsHandling);
    }

    protected boolean isWebFragmentsSelectorEnabled() {
        return isWebFragmentsHandlingDetect() && webFragmentsSelector != null;
    }
}
