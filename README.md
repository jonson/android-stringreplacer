Maven plugin to replace values in a strings.xml file with ones defined within maven properties.  Useful when you want to keep a valid strings.xml file (that is, no replacement placeholders such as ${replace.me}) to use in an IDE, but also want to have some values replaced by maven.

Usage (strings.xml):
    ...
    <string name="server.url" replaceable="true">http://the.default.url</string>
    ...

Usage (pom.xml):
    ...
    <properties>
      <server.url>http://the.replaced.url</server.url>
    </properties>
    ...
    <plugins>
      <plugin>
        <groupId>com.dajodi.android</groupId>
        <artifactId>android-stringreplacer-maven-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>
        <configuration>
                <files>
                        <file>values/strings.xml</file>
                </files>
                <outputDirectory>${project.build.directory}/filtered-res</outputDirectory>
        </configuration>
        <executions>
                <execution>
                        <phase>initialize</phase>
                        <goals>
                                <goal>replace</goal>
                        </goals>
                </execution>
        </executions>
      </plugin>
    </plugins>
    ...
    