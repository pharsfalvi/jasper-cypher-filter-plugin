# jasper-cypher-filter-plugin

This maven plugin enables the resource filtering of password values into the datasource XMLs of the jasper reports server.

## Usage: 

Create a filter file called filter.properties in src/main/resources of your maven project.
```
...
somekey=value
jdbc.jasper_host=localhost
jdbc.jasper_port=3306
jasper.schemaname=jasperschema
jdbc.jasper_username=jasperuser
jdbc.jasper_password=jasperpassword
otherkey=othervalue
...
```

## Configure the plugin in your project pom.xml
```
            <filters>
              <filter>${basedor}/src/main/filters/filter.properties</filter>
            </filters>
    
            <plugin>
                <groupId>org.gembaboo.maven</groupId>
                <artifactId>jasper-cypher-filter-plugin</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <executions>
                    <execution>
                        <phase>process-resources</phase>
                        <goals>
                            <goal>resources</goal>
                        </goals>
                        <configuration>
                            <passwordKeys>
                                <!-- Put here the password keys which have to be encrypted as jasper reports password -->
                                <value>jdbc.jasper_password</value>
                            </passwordKeys>
                        </configuration>
                    </execution>
                </executions>
                <configuration>
                    <overwrite>true</overwrite>
                    <resources>
                        <resource>
                            <!-- Include here those files which have to be filtered -->
                            <directory>${basedir}/src/main/resources</directory>
                            <includes>
                                <include>**/JASPER_DS.xml</include>
                            </includes>
                        </resource>
                    </resources>
                </configuration>
            </plugin>
```

## Export your report template files 
from your jasper studio (or jasper server) into src/main/resources. The data source file (called JASPER_DS.xml above) should have a layout like this:

```
<?xml version="1.0" encoding="UTF-8"?>
<jdbcDataSource exportedWithPermissions="false">
    <folder>/Somefolder</folder>
    ...
    <connectionUrl>jdbc:mysql://@jdbc.jasper_host@:@jdbc.jasper_port@/@jasper.schemaname@</connectionUrl>
    <connectionUser>@jdbc.jasper_username@</connectionUser>
    <connectionPassword>@jdbc.jasper_password@</connectionPassword>
    ...
</jdbcDataSource>
```


## How it works

The above plugin configuration will search for the JASPER_DS.xml in your src/main/resources directory and copy it to
target/classes/ accordingly. During copying it will apply the filter property values from filter.properties

Given that the plugin configuration references a property key in the filter file (e.g. jdbc.jasper_password above), the
value of it will be injected with an encrypted format like this:

```
<connectionPassword>ENC&lt;C3448BD8B3552497EB00DDE1EBF15472&gt;</connectionPassword>
```
