# TomcatBoot

[![Unit Test](https://github.com/dbflute-session/tomcat-boot/actions/workflows/unit_test.yaml/badge.svg?branch=master)](https://github.com/dbflute-session/tomcat-boot/actions/workflows/unit_test.yaml)
[![OWASP Dependency Check](https://github.com/dbflute-session/tomcat-boot/actions/workflows/owasp_dependency_check.yaml/badge.svg?branch=master)](https://github.com/dbflute-session/tomcat-boot/actions/workflows/owasp_dependency_check.yaml)

simple boot library of Tomcat

```java
new TomcatBoot(8091, "/dockside").asDevelopment().bootAwait();
```

No need to shutdown previous process when you restart it.  
Automatically shutdown before next process boot

# Information
## Maven Dependency
```xml
<dependency>
    <groupId>org.dbflute.tomcat</groupId>
    <artifactId>tomcat-boot</artifactId>
    <version>0.7.8</version>
</dependency>
```

## if you use JSP
Add the jasper to your dependencies like this:
```xml
<dependency> <!-- for jsp -->
    <groupId>org.apache.tomcat</groupId>
    <artifactId>tomcat-jasper</artifactId>
    <version>9.0.19</version>
</dependency>
```

## License
Apache License 2.0

## Official site
comming soon...

# Thanks, Friends
TomcatBoot is used by:  
- Fess: https://github.com/codelibs/fess (from version10)

Deeply Thanks!

