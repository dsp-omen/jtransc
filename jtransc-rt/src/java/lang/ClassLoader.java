/*
 * Copyright 2016 Carlos Ballesteros Velasco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;

public abstract class ClassLoader {
	private ClassLoader parent;

	protected ClassLoader(ClassLoader parent) {
		this.parent = parent;
	}

	protected ClassLoader() {
		this(null);
	}

	public final ClassLoader getParent() {
		return parent;
	}

	public static ClassLoader getSystemClassLoader() {
		return _ClassInternalUtils.getSystemClassLoader();
	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		//System.err.println("ClassLoader.loadClass('" + name + "');");
		System.out.println("ClassLoader.loadClass('" + name + "');");
		return Class.forName(name);
	}

	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return loadClass(name);
	}

	protected Object getClassLoadingLock(String className) {
		return this;
	}

	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return loadClass(name);
	}

	@Deprecated
	native protected final Class<?> defineClass(byte[] b, int off, int len) throws ClassFormatError;

	native protected final Class<?> defineClass(String name, byte[] b, int off, int len) throws ClassFormatError;

	//native protected final Class<?> defineClass(String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) throws ClassFormatError;
	//native protected final Class<?> defineClass(String name, java.nio.ByteBuffer b, ProtectionDomain protectionDomain) throws ClassFormatError;
	native protected final void resolveClass(Class<?> c);

	protected final Class<?> findSystemClass(String name) throws ClassNotFoundException {
		return loadClass(name);
	}

	protected final Class<?> findLoadedClass(String name) {
		try {
			return loadClass(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	protected final void setSigners(Class<?> c, Object[] signers) {
	}

	public URL getResource(String name) {
		try {
			return new URL("file:///" + name);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	public Enumeration<URL> getResources(String name) throws IOException {
		return new Vector<>(Arrays.asList(getResource(name))).elements();
	}

	native protected URL findResource(String name);

	native protected Enumeration<URL> findResources(String name) throws IOException;

	native protected static boolean registerAsParallelCapable();

	native public static URL getSystemResource(String name);

	native public static Enumeration<URL> getSystemResources(String name) throws IOException;

	public InputStream getResourceAsStream(String name) {
		try {
			return new FileInputStream(name);
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	native public static InputStream getSystemResourceAsStream(String name);

	native protected Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException;

	native protected Package getPackage(String name);

	native protected Package[] getPackages();

	native protected String findLibrary(String libname);

	native public void setDefaultAssertionStatus(boolean enabled);

	native public void setPackageAssertionStatus(String packageName, boolean enabled);

	native public void setClassAssertionStatus(String className, boolean enabled);

	native public void clearAssertionStatus();
}
