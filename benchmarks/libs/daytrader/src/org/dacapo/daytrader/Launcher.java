/*
 * Copyright (c) 2009 The Australian National University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0.
 * You may obtain the license at
 * 
 *    http://www.opensource.org/licenses/apache2.0.php
 */
package org.dacapo.daytrader;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/** 
* date:  $Date: 2009-12-24 11:19:36 +1100 (Thu, 24 Dec 2009) $
* id: $Id: Launcher.java 738 2009-12-24 00:19:36Z steveb-oss $
*/
public class Launcher {
  // Geronimo configuration
  private final static String VERSION = "17.0.0";
  private final static String TYPE = "Final";
  private final static String DIRECTORY = "wildfly-" + VERSION + "." + TYPE;

  // This jar contains the code that knows how to create and communicate with
  // geronimo environments
  private final static String[] DACAPO_CLI_JAR = { ".."+File.separator+".."+File.separator+"jar"+File.separator+"tradebeans"+File.separator+"daytrader.jar" };

  // The following list is defined in the "Class-Path:" filed of MANIFEST.MF for the client and server jars
  private final static String[] WILDFLY_SERVER_JARS = {"jboss-modules.jar"};

  private static int numThreads = -1;
  private static String size;
  private static boolean useBeans = true;

  private static ClassLoader serverCLoader = null;
  private static Method clientMethod = null;
  private static File root = null;

  public static void initialize(File rootdir, int threads, String dtSize, boolean beans) {
    numThreads = threads;
    size = dtSize;
    useBeans = beans;
    root = new File(rootdir.getAbsolutePath());
    setWildflyProperties();
    ClassLoader originalCLoader = Thread.currentThread().getContextClassLoader();

    try {
      // Create a server environment
      System.err.println("Creating...");



      serverCLoader = createWildflyClassLoader(originalCLoader, true);
      Thread.currentThread().setContextClassLoader(serverCLoader);
      Class<?> clazz = serverCLoader.loadClass("org.dacapo.daytrader.DaCapoServerRunner");
      Method method = clazz.getMethod("initialize");
      method.invoke(null);

      // Create a client environment
      clazz = serverCLoader.loadClass("org.dacapo.daytrader.DaCapoClientRunner");
      method = clazz.getMethod("initialize", String.class, int.class, boolean.class);
      method.invoke(null, size, numThreads, useBeans);
      clientMethod = clazz.getMethod("runIteration", String.class, int.class, boolean.class);

    } catch (Exception e) {
      System.err.println("Exception during initialization: " + e.toString());
      e.printStackTrace();
      Runtime.getRuntime().halt(1);
    } finally {
      Thread.currentThread().setContextClassLoader(originalCLoader);
    }
  }

  private static void setWildflyProperties() {
    System.setProperty("jboss.home.dir", new File(root, DIRECTORY).getPath());
    System.setProperty("module.path", new File(root, DIRECTORY + File.separator + "modules").getPath());
  }

  public static void performIteration() {
    if (numThreads == -1) {
      System.err.println("Trying to run Daytrader before initializing.  Exiting.");
      System.exit(0);
    }
    ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(serverCLoader);
      clientMethod.invoke(null, size, numThreads, useBeans);
    } catch (Exception e) {
      System.err.println("Exception during iteration: " + e.toString());
      e.printStackTrace();
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassloader);
    }
  }

  public static void shutdown(){
    try {
      Class<?> clazz = serverCLoader.loadClass("org.dacapo.daytrader.DaCapoServerRunner");
      Method method = clazz.getMethod("shutdown");
      method.invoke(null);
    }catch (Exception e) {
      System.err.println("Exception during iteration: " + e.toString());
      e.printStackTrace();
    }
  }

  /**
   * Create the classloader from within which to start the Geronimo client
   * and/or server kernels.
   * 
   * @param server Is this the server (or client) classloader
   * @return The classloader
   * @throws Exception
   */
  private static ClassLoader createWildflyClassLoader(ClassLoader parent, boolean server) {
    File wildfly = new File(root, DIRECTORY).getAbsoluteFile();
    System.err.println("Creating..."+wildfly.getAbsolutePath());

    return new URLClassLoader(getWildflyLibraryJars(wildfly, server), parent);
  }

  /**
   * Get a list of jars (if any) which should be in the library classpath for
   * this benchmark
   * 
   * @param wildfly The base directory for the jars
   * @return An array of URLs, one URL for each jar
   */
  private static URL[] getWildflyLibraryJars(File wildfly, boolean server) {
    List<URL> jars = new ArrayList<URL>();

    if (server) {          System.err.println("server...");

      addJars(jars, wildfly, WILDFLY_SERVER_JARS);
    }
    System.err.println("cli...");

    addJars(jars, root, DACAPO_CLI_JAR);

    return jars.toArray(new URL[jars.size()]);
  }

  /**
   * Compile a list of paths to jars from a base directory and relative paths.
   * 
   * @param jars The url contain URL to jar files
   * @param directory The root directory, in which the jars will be located
   * @param jarNames The name of jar files to be added
   * @return An array of URLs, one URL for each jar
   */
  private static void addJars(List<URL> jars, File directory, String[] jarNames) {
    if (jarNames != null) {
      for (int i = 0; i < jarNames.length; i++) {
        File jar = new File(directory, jarNames[i]);
        try {
          URL url = jar.toURI().toURL();
          System.err.println("adding..."+url.toString());
          jars.add(url);
        } catch (MalformedURLException e) {
          System.err.println("Unable to create URL for jar: " + jarNames[i] + " in " + directory.toString());
          e.printStackTrace();
          System.exit(-1);
        }
      }
    }
  }
}
