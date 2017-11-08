# WEB-INF/lib Bean Scanner for Liferay 7.0+ CDI Portlets

This project is a CDI extension which scans all jars in a **`.war`**'s **`WEB-INF/lib`** for `@Named` CDI beans and creates producers for them so that the CDI implementation can `@Inject` those beans.

## Using WEB-INF_lib-bean-scanner:

1. Clone the [WEB-INF_lib-bean-scanner](https://github.com/stiemannkj1/WEB-INF_lib-bean-scanner) project:

    ```
    git clone https://github.com/stiemannkj1/WEB-INF_lib-bean-scanner.git
    ```

2. Build the project:

    ```
    cd WEB-INF_lib-bean-scanner && git checkout 0.1.0 && mvn clean install
	```

3. Add the following dependency to your Liferay **`.war`** file:


    ```
    <dependency>
        <groupId>com.liferay.cdi.extension</groupId>
        <artifactId>com.liferay.cdi.WEB-INF_lib.bean.scanner</artifactId>
        <version>0.1.0</version>
    </dependency>
    ```

Producers will not be created for beans in `WEB-INF/classes` since the CDI implementation finds those beans and injects them normally. You can blacklist certain packages to avoid scanning them for beans by setting the `com.liferay.cdi.WEB-INF/lib.scan.packages.blacklist` property in your **`.war`**'s **`liferay-plugin-package.properties`**. In order to avoid creating duplicate producers for beans which the CDI implementation correctly discovers, the property defaults to:

```
com.liferay.cdi.WEB-INF/lib.scan.packages.blacklist=\
	com.sun,\
	javax,\
	org.jboss.weld
```
