/**
 * Copyright (c) 2000-2017 Liferay, Inc. All rights reserved.
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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Named;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWiring;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.InjectionTargetFactory;


/**
 * @author  Kyle Stiemann
 */
public final class WEB_INF_libBeanScanner implements Extension {

	// Logger
	private static final Log logger = LogFactoryUtil.getLog(WEB_INF_libBeanScanner.class);

	// Private Constants
	private static final boolean FRAMEWORK_UTIL_DETECTED;

	//J-
	private static final String DEFAULT_BLACKLISTED_SCAN_PACKAGES =
		"com.sun," +
		"org.jboss.weld," +
		"javax";
	//J+

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

	private static List<String> getPackageBlacklist(Bundle thickWebApplicationBundle) {

		Properties properties = new Properties();
		String liferayPluginPackagePropertiesPath = "/WEB-INF/liferay-plugin-package.properties";
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

		String blacklistedPackagesString = (String) properties.getOrDefault(
				"com.liferay.cdi.WEB-INF/lib.scan.packages.blacklist", DEFAULT_BLACKLISTED_SCAN_PACKAGES);
		List<String> blacklistedPackages = new ArrayList<>();

		String[] blacklistedPackagesArray = blacklistedPackagesString.split(",");

		for (String blacklistedPackage : blacklistedPackagesArray) {
			blacklistedPackages.add(blacklistedPackage.trim());
		}

		return Collections.unmodifiableList(blacklistedPackages);
	}

	public void afterBean(@Observes final AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {

		if (!FRAMEWORK_UTIL_DETECTED) {

			logger.warn("OSGi not detected. Skipping unnecessary scanning of WEB-INF/lib for beans.\n" +
				"com.liferay.cdi.WEB-INF_lib.bean.scanner.jar can be removed from your war's WEB-INF/lib folder.");

			return;
		}

		List<String> blacklistedPackages = null;
		Bundle thickWebApplicationBundle = FrameworkUtil.getBundle(WEB_INF_libBeanScanner.class);
		BundleWiring bundleWiring = thickWebApplicationBundle.adapt(BundleWiring.class);
		Collection<String> classFilePaths = bundleWiring.listResources("/", "*.class",
				BundleWiring.LISTRESOURCES_RECURSE);

		for (String classFilePath : classFilePaths) {

			URL resource = thickWebApplicationBundle.getResource(classFilePath);
			boolean classFromWEB_INF_classes = resource.getPort() < 1;
			String className = classFilePath.replaceAll("\\.class$", "").replace("/", ".");

			if (blacklistedPackages == null) {
				blacklistedPackages = getPackageBlacklist(thickWebApplicationBundle);
			}

			if (classFromWEB_INF_classes ||
				blacklistedPackages.parallelStream()
					.anyMatch(blacklistedPackage -> className.startsWith(blacklistedPackage))) {
				continue;
			}

			ClassLoader bundleClassLoader = bundleWiring.getClassLoader();
			Class<?> clazz;

			try {
				clazz = bundleClassLoader.loadClass(className);
			}
			catch (ClassNotFoundException | NoClassDefFoundError e) {
				continue;
			}

			Named namedAnnotation = clazz.getAnnotation(Named.class);

			if (namedAnnotation == null) {
				continue;
			}

			AnnotatedType annotatedType = beanManager.createAnnotatedType(clazz);
			BeanAttributes beanAttributes = beanManager.createBeanAttributes(annotatedType);
			InjectionTargetFactory injectionTargetFactory = beanManager.getInjectionTargetFactory(annotatedType);
			Bean producer = beanManager.createBean(beanAttributes, clazz, injectionTargetFactory);
			afterBeanDiscovery.addBean(producer);
		}
	}
}
