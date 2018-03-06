# TomcatBoot
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
    <version>0.6.2</version>
</dependency>
```

## if you use JSP
Add the jasper to your dependencies like this:
```xml
<dependency> <!-- for jsp -->
    <groupId>org.apache.tomcat</groupId>
    <artifactId>tomcat-jasper</artifactId>
    <version>8.5.21</version>
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

