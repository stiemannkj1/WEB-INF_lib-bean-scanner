# WEB-INF/lib Bean Scanner for Liferay 7.0+ CDI Portlets

This project is a CDI [`Extension`](https://docs.jboss.org/cdi/api/1.2/javax/enterprise/inject/spi/Extension.html) which scans all jars in a **`.war`**'s **`WEB-INF/lib`** for annotated CDI [`@NormalScope`](https://docs.jboss.org/cdi/api/1.2/javax/enterprise/context/NormalScope.html)s and beans. The `Extension` uses [`BeforeBeanDiscovery`](https://docs.jboss.org/cdi/api/1.2/javax/enterprise/inject/spi/BeforeBeanDiscovery.html)'s [`addScope()`](https://docs.jboss.org/cdi/api/1.2/javax/enterprise/inject/spi/BeforeBeanDiscovery.html#addScope(java.lang.Class,%20boolean,%20boolean)) and [`addAnnotatedType()`](https://docs.jboss.org/cdi/api/1.2/javax/enterprise/inject/spi/BeforeBeanDiscovery.html#addAnnotatedType(javax.enterprise.inject.spi.AnnotatedType)) to declare unscanned scopes and beans respectively to the CDI implementation.

## Using WEB-INF_lib-bean-scanner:

1. Clone the [WEB-INF_lib-bean-scanner](https://github.com/stiemannkj1/WEB-INF_lib-bean-scanner) project:

    ```
    git clone https://github.com/stiemannkj1/WEB-INF_lib-bean-scanner.git
    ```

2. Build the project:

    ```
    cd WEB-INF_lib-bean-scanner && git checkout 0.2.0 && mvn clean install
	```

3. Add the following dependency to your Liferay **`.war`** file:


    ```
    <dependency>
        <groupId>com.liferay.cdi.extension</groupId>
        <artifactId>com.liferay.cdi.WEB-INF_lib.bean.scanner</artifactId>
        <version>0.2.0</version>
    </dependency>
    ```

Scopes and beans in `WEB-INF/classes` will not be scanned since the CDI implementation finds those scopes and beans normally. You can blacklist certain packages to avoid scanning them for scopes and beans by setting the `com.liferay.cdi.WEB-INF/lib.scanner.blacklisted.packages` property in your **`.war`**'s **`liferay-plugin-package.properties`**. In order to avoid scanning scopes and beans which the CDI implementation correctly discovers, the property defaults to:

```
com.liferay.cdi.WEB-INF/lib.scanner.blacklisted.packages=\
	com.sun,\
	javax,\
	org.jboss.weld
```

The `Extension` scans for beans annotated with `@Named` and/or any `@NormalScope` (including custom `@NormalScope`s). You can whitelist certain annotations to ensure that all annotated beans are found by setting the `com.liferay.cdi.WEB-INF/lib.scanner.whitelisted.annotations.to.scan.for` property in your **`.war`**'s **`liferay-plugin-package.properties`**. In order to ensure that all annotated beans are found, the property defaults to:

```
com.liferay.cdi.WEB-INF/lib.scanner.whitelisted.annotations.to.scan.for=\
	javax.enterprise.context.ApplicationScoped,\
	javax.enterprise.context.ConversationScoped,\
	javax.enterprise.context.Dependent,\
	javax.enterprise.context.NormalScope,\
	javax.enterprise.context.RequestScoped,\
	javax.enterprise.context.SessionScoped,\
	javax.inject.Named
#	Any custom scopes that were annotated with @NormalScope are also scanned for.
```
