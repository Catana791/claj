package com.xpdustry.claj.server.util;

import java.net.URL;
import java.net.URLClassLoader;

import arc.files.Fi;


public class JarLoader {
  public static ClassLoader load(Fi jar, ClassLoader parent) throws Exception {
    return new URLClassLoader(new URL[] {jar.file().toURI().toURL()}, parent) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        //check for loaded state
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass == null) {
          //try to load own class first
          try { loadedClass = findClass(name); }
          //use parent if not found
          catch (ClassNotFoundException e) { 
            if (parent != null) return parent.loadClass(name); 
            throw e;
          }
        }

        if (resolve) resolveClass(loadedClass);
        return loadedClass;
      }
    };
  }
}
