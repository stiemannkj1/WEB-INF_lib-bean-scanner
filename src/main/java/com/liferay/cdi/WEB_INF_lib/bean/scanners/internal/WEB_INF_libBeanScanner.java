/**
 * Copyright (c) 2000-2018 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.cdi.WEB_INF_lib.bean.scanners.internal;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ConversationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.NormalScope;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Named;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWiring;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;


/**
 * @author  Kyle Stiemann
 */
public final class WEB_INF_libBeanScanner implements Extension {

	// Logger
	private static final Log logger = LogFactoryUtil.getLog(WEB_INF_libBeanScanner.class);

	// Private Constants
	private static final Set<Class<? extends Annotation>> ANNOTATIONS_TO_SCAN_FOR;
	private static final Set<String> BLACKLISTED_PACKAGES;

	//J-
	private static final Set<String> DEFAULT_BLACKLISTED_PACKAGES =
			Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
					"com.sun",
					"javax",
					"org.jboss.weld"
			)));
	//J+

	//J-
	private static final Set<Class<? extends Annotation>> DEFAULT_ANNOTATIONS_TO_SCAN_FOR =
			Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
					ApplicationScoped.class,
					ConversationScoped.class,
					Dependent.class,
					RequestScoped.class,
					SessionScoped.class,
					Named.class
			)));
	//J+
	private static final boolean FRAMEWORK_UTIL_DETECTED;

	static {

		boolean frameworkUtilDetected = false;

		try {

			Class.forName("org.osgi.framework.FrameworkUtil");
			frameworkUtilDetected = true;
		}
		catch (Throwable t) {

			if (!((t instanceof NoClassDefFoundError) || (t instanceof ClassNotFoundException))) {
				logger.error("An unexpected error occurred when attempting to detect OSGi", t);
			}
		}

		FRAMEWORK_UTIL_DETECTED = frameworkUtilDetected;

		Properties properties = new Properties();
		String liferayPluginPackagePropertiesPath = "/WEB-INF/liferay-plugin-package.properties";

		if (FRAMEWORK_UTIL_DETECTED) {

			Bundle thickWebApplicationBundle = FrameworkUtil.getBundle(WEB_INF_libBeanScanner.class);
			URL propertiesURL = thickWebApplicationBundle.getEntry(liferayPluginPackagePropertiesPath);
			InputStream inputStream = null;

			if (propertiesURL != null) {

				try {

					inputStream = propertiesURL.openStream();
					properties.load(inputStream);
				}
				catch (IOException e) {
					logger.error("Error reading properties from " + liferayPluginPackagePropertiesPath, e);
				}
				finally {
					close(inputStream);
				}
			}
		}

		BLACKLISTED_PACKAGES = getPropertyValueAsSet(properties,
				"com.liferay.cdi.WEB-INF/lib.scanner.blacklisted.packages", Function.identity(),
				DEFAULT_BLACKLISTED_PACKAGES);

		ANNOTATIONS_TO_SCAN_FOR = getPropertyValueAsSet(properties,
				"com.liferay.cdi.WEB-INF/lib.scanner.whitelisted.annotations.to.scan.for",
				(String className) -> {
					try {
						return (Class<? extends Annotation>) Class.forName(className);
					} catch (ClassNotFoundException e) {
						throw new RuntimeException(e);
					}
				},
				DEFAULT_ANNOTATIONS_TO_SCAN_FOR);
	}

	private static void close(Closeable closeable) {

		if (closeable != null) {

			try {
				closeable.close();
			}
			catch (IOException e) {
				logger.error("Failed to close InputStream", e);
			}
		}
	}

	private static Set<Class<?>> getAnnotatedClasses(Set<Class<? extends Annotation>> annotationsToScanFor) {

		Set<Class<?>> annotatedClasses = new HashSet<>();
		Bundle thickWebApplicationBundle = FrameworkUtil.getBundle(WEB_INF_libBeanScanner.class);
		BundleWiring bundleWiring = thickWebApplicationBundle.adapt(BundleWiring.class);
		Collection<String> classFilePaths = bundleWiring.listResources("/", "*.class",
				BundleWiring.LISTRESOURCES_RECURSE);

		for (String classFilePath : classFilePaths) {

			URL resource = thickWebApplicationBundle.getResource(classFilePath);
			boolean classFromWEB_INF_classes = resource.getPort() < 1;

			if (classFromWEB_INF_classes) {
				continue;
			}

			String className = classFilePath.replaceAll("\\.class$", "").replace("/", ".");

			if (BLACKLISTED_PACKAGES.parallelStream()
					.anyMatch(blacklistedPackage -> className.startsWith(blacklistedPackage))) {
				continue;
			}

			Class<?> clazz;

			try {
				clazz = thickWebApplicationBundle.loadClass(className);
			}
			catch (ClassNotFoundException | NoClassDefFoundError e) {
				continue;
			}

			if (annotationsToScanFor.parallelStream()
					.allMatch(annotation -> clazz.getAnnotation(annotation) == null)) {
				continue;
			}

			annotatedClasses.add(clazz);
		}

		return Collections.unmodifiableSet(annotatedClasses);
	}

	private static <T> Set<T> getPropertyValueAsSet(Properties properties, String propertyKey,
		Function<? super String, T> stringToTFunction, Set<T> defaultPropertyValueAsSet) {

		String propertyValueAsString = (String) properties.getProperty(propertyKey);

		if (propertyValueAsString == null) {
			return defaultPropertyValueAsSet;
		}

		return Collections.unmodifiableSet(Arrays.asList(propertyValueAsString.split(",")).stream()
				.map(string -> stringToTFunction.apply(string.trim()))
				.collect(Collectors.toSet()));
	}

	public void beforeBeanDiscovery(@Observes final BeforeBeanDiscovery beforeBeanDiscovery, BeanManager beanManager) {

		if (!FRAMEWORK_UTIL_DETECTED) {

			logger.warn("OSGi not detected. Skipping unnecessary scanning of WEB-INF/lib for beans.\n" +
				"com.liferay.cdi.WEB-INF_lib.bean.scanner.jar can be removed from your war's WEB-INF/lib folder.");

			return;
		}

		Set<Class<? extends Annotation>> customScopeAnnotations = getAnnotatedClasses(Collections.singleton(
					NormalScope.class)).stream()
				.map(customScope -> (Class<? extends Annotation>) customScope)
				.collect(Collectors.toSet());

		for (Class<? extends Annotation> customScopeAnnotation : customScopeAnnotations) {

			NormalScope normalScopeAnnotation = customScopeAnnotation.getAnnotation(NormalScope.class);
			beforeBeanDiscovery.addScope(customScopeAnnotation, true, normalScopeAnnotation.passivating());
		}

		Set<Class<? extends Annotation>> annotationsToScanFor = new HashSet<>(ANNOTATIONS_TO_SCAN_FOR);
		annotationsToScanFor.addAll(customScopeAnnotations);

		Set<Class<?>> annotatedBeanClasses = getAnnotatedClasses(annotationsToScanFor);

		for (Class<?> annotatedBeanClass : annotatedBeanClasses) {

			AnnotatedType<?> beanAnnotatedType = beanManager.createAnnotatedType(annotatedBeanClass);
			beforeBeanDiscovery.addAnnotatedType(beanAnnotatedType, null);
		}
	}
}
