# native-image-annotations

A set of annotations and an [annotation processor](https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/Processor.html) to help with [GraalVM native image](https://www.graalvm.org/native-image/).

For a native image to be built, all reflection, proxies, JNI, classpath resource and other elements that would usually be discovered at runtime, must be declared at compile time.

Instead of using the [GraalVM tracing agent](https://www.graalvm.org/22.0/reference-manual/native-image/Agent/), or hand crafting native image [configuration files](https://www.graalvm.org/22.0/reference-manual/native-image/BuildConfiguration/), you can annotate your classes, interfaces, methods and fields. These annotations are then turned into JSON files consumed by the native image compiler.

It is currently most useful for exposing certain elements to reflection, and the reason I created it was to help generate natively compiled DBus services using [DBus Java](https://github.com/hypfvieh/dbus-java) on Linux.  

The annotations are only source only, so the library is only needed during development and build time. 

## Installation

The library is available in Maven Central (and OSS Snapshots). You need to add it as a compile time dependency, and an annotation processor. Gradle also supports annotations processors, or you can use them with the standard Java compiler.

### Dependency

```xml
<dependency>
    <groupId>uk.co.bithatch</groupId>
    <artifactId>native-image-annotations</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency> 
```

#### Snapshot Repository

*Not required if using a stable version.*

```xml

<repository>
    <id>oss-snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    <snapshots />
    <releases>
        <enabled>false</enabled>
    </releases>
</repository>
```


### Annotation Processor

You must add the annotation processor to your compiler. 

#### Maven

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>uk.co.bithatch</groupId>
                        <artifactId>native-image-annotations</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### JPMS

The module must be added, even though it won't be available at runtime.

```java
requires static uk.co.bithatch.nativeimage.annotations
```

## Usage

To use, simply annotation you source elements appropriately. In the example below, reflection configuration will be created, exposing `MyNativeService` and all of its methods, as well as a single field and a method from `MyObject`.

```java
@Reflectable
@TypeReflect(methods = true, classes = true)
public class MyNativeService {

    public List<String> getListOfSomething() {
        return Arrays.asList("one", "two", "three");
    }

    public MyObject getAnObject(String name) {
        return new MyObject(name, name + " private");
    }

    @Reflectable
    @TypeReflect(constructors = true)
    public static class MyObject {
        @Reflectable
        private String someValue;
        private String somePrivateValue;

        public MyObject(String someValue, String somePrivateValue) {
            this.someValue = someValue;
            this.somePrivateValue = somePrivateValue;
        }
        
        @Reflectable
        public String getSomeValue() {
            return someValue;
        }
        
        public String getSomePrivateValue() {
            return somePrivateValue;
        }
    }
}
```

### Annotations

#### @Reflectable

Marks either a `TYPE`, `CONSTRUCTOR`, `FIELD` or `METHOD` as a candidate for reflection. It has a single attribute, `all()` that will signal that *all* child elements will also be reflectable.

Configuration will be added to `reflect-config.json`.

#### @TypeReflect

Used in on a `TYPE` in conjunction with a `@Reflectable`, this annotation provides 4 attributes allowing control of whether all child elements of a certain type should be reflectable.

It supports, `constructors()`, `fields()`, `methods()` or `classes()`.

Configuration will be added to `reflect-config.json`.

#### @Proxy

Marks a `TYPE` as a Proxy type. 

Configuration will be added to `proxy-config.json`.

#### @Serialization

Marks a `TYPE` for serialization. 

Configuration will be added to `serialization-config.json`.

#### @Bundle

Adds an i18n resource bundle for a `TYPE`. A single attribute is supported, `locales()` which is an optional list of locales to include.

For example, if the class `com.acme.MyObject` was annotated with `@Bundle`, then the default resource path of `com/acme/MyObject.properties` must exist. There can be optional resources such as `com/acme/MyObject_fr.properties` etc.

#### @Resource

Introduces classpath resources. Exact behaviour will depend on attributes used.

 * If the annotation has no attributes, then all resources in the same package, and prefixed with the same class name will be included. For example, if the annotated class's full qualified name is `com.acme.MyObject`, then `/com/acme/MyObject.properties`, `/com/acme/MyObject.css` and so on will be included.
 * If the annotation sets the `siblings()` attribute to true, then all resources in the same project will be incluided.
 * Otherwise, the annotation `value()` is an array of *absolute* resource paths. 

Configuration will be added to `resources-config.json`.
