package org.dbflute.tomcat;

import org.dbflute.utflute.core.PlainTestCase;

/**
 * @author jflute
 */
public class BootBoot extends PlainTestCase {

    public static void main(String[] args) {
        new TomcatBoot(8159, "/boot").asDevelopment(true).bootAwait();
    }
}
