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
package org.dbflute.tomcat.core.valve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.catalina.Valve;

/**
 * @author jflute
 * @since 0.5.8 (2017/09/06 Wednesday)
 */
public class YourValveOption {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected List<Valve> valveList; // null allowed, lazy loaded

    // ===================================================================================
    //                                                                         Easy-to-Use
    //                                                                         ===========
    public YourValveOption valve(Valve valve) {
        if (valveList == null) {
            valveList = new ArrayList<Valve>();
        }
        valveList.add(valve);
        return this;
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public List<Valve> getValveList() { // read-only
        return valveList != null ? Collections.unmodifiableList(valveList) : Collections.emptyList();
    }
}
