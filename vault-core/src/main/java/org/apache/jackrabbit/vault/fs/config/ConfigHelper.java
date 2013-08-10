/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jackrabbit.vault.fs.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * <code>ConfigHelper</code>...
 */
public class ConfigHelper {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ConfigHelper.class);

    private Map defaultPackages = new HashMap();

    private Map defaultClasses = new HashMap();

    private Map<String, String> mappings = new HashMap<String, String>();

    public Map getDefaultPackages() {
        return defaultPackages;
    }

    public Map getDefaultClasses() {
        return defaultClasses;
    }

    protected Map<String, String> getMappings() {
        return mappings;
    }

    public String getDefaultPackage(String name) {
        return (String) defaultPackages.get(name);
    }

    public String getDefaultClass(String name) {
        return (String) defaultClasses.get(name);
    }

    public Object create(Element elem)
            throws ConfigurationException {

        String className = elem.getAttribute("class");
        if (className == null || className.equals("")) {
            className = (String) defaultClasses.get(elem.getNodeName());
        }
        if (className == null || className.equals("")) {
            // create string object
            return elem.getFirstChild().getNodeValue();
        }
        String field = null;
        int pos = className.indexOf('#');
        if (pos>0) {
            field = className.substring(pos + 1);
            className = className.substring(0, pos);
        }

        // try to get class without prepending package
        Class clazz = null;
        try {
            clazz = getClass().getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            // ignore
        }
        if (clazz == null) {
            // check for default package
            if (className.indexOf('.') < 0) {
                String pack = (String) defaultPackages.get(elem.getNodeName());
                if (pack == null) {
                    throw new ConfigurationException("Default package for class attribute of " + elem.getNodeName() + " missing.");
                }
                className = pack + "." + className;
            }

            // check for mapping
            if (mappings.containsKey(className)) {
                className = mappings.get(className);
            }

            try {
                clazz = getClass().getClassLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                throw new ConfigurationException("Error while creating instance for " + elem.getNodeName(), e);
            }

        }
        try {
            if (field == null) {
                return clazz.newInstance();
            } else {
                return clazz.getField(field).get(null);
            }
        } catch (InstantiationException e) {
            throw new ConfigurationException("Error while creating instance for " + elem.getNodeName(), e);
        } catch (NoSuchFieldException e) {
            throw new ConfigurationException("Error while creating instance for " + elem.getNodeName(), e);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException("Error while creating instance for " + elem.getNodeName(), e);
        }
    }

    public static String getMethodName(String prefix, String name) {
        return prefix + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public static Method getMethod(Object obj, String name, Class ... params) {
        Method[] ms = obj.getClass().getMethods();
        for (Method m : ms) {
            if (m.getName().equals(name)) {
                Class[] pt = m.getParameterTypes();
                if (pt.length == params.length) {
                    for (int j = 0; j < params.length; j++) {
                        if (!params[j].isAssignableFrom(pt[j])) {
                            m = null;
                            break;
                        }
                    }
                    if (m != null) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    public static boolean hasSetter(Object obj, String name)
            throws ConfigurationException {
        String setter = getMethodName("set", name);
        if (getMethod(obj, setter, Object.class) != null) {
            log.debug("Has setter {} on {}" , name, obj);
            return true;
        } else {
            log.debug("{} has no setter for {}" , obj, name);
            return false;
        }
    }

    public static boolean setField(Object obj, String name, Object value)
            throws ConfigurationException {
        // ignore 'class' setters
        if (name.equals("class")) {
            return false;
        }
        String setter = getMethodName("set", name);
        try {
            Method m = getMethod(obj, setter, Object.class);
            if (m == null) {
                log.error("{} has no setter for {}" , obj, name);
                throw new ConfigurationException(obj + " has not setter for " + name);
            }
            m.invoke(obj, value);
            log.debug("Setting {} on {}" , name, obj);
            return true;
        } catch (IllegalAccessException e) {
            throw new ConfigurationException("Unable to set " + setter + " of " + obj , e);
        } catch (InvocationTargetException e) {
            throw new ConfigurationException("Unable to set " + setter + " of " + obj , e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeGetter(Object obj, String name, Class<T> T)
            throws ConfigurationException {
        try {
            String getter = getMethodName("get", name);
            Method m = obj.getClass().getMethod(getter);
            if (T.isAssignableFrom(m.getReturnType())) {
                return (T) m.invoke(obj);
            } else {
                return null;
            }
        } catch (NoSuchMethodException e) {
            log.debug("{} has no field {} or type " + T, obj, name);
            return null;
        } catch (IllegalAccessException e) {
            throw new ConfigurationException("Unable to get list " + name + " of " + obj);
        } catch (InvocationTargetException e) {
            throw new ConfigurationException("Unable to get list " + name + " of " + obj);
        }
    }

}