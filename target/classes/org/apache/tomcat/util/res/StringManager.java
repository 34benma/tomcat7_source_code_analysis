/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.res;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * An internationalization / localization helper class which reduces
 * the bother of handling ResourceBundles and takes care of the
 * common cases of message formating which otherwise require the
 * creation of Object arrays and such.
 * 
 * 国际化辅助类，基于包管理该包内的错误日志信息
 * 每个包都可以通过getManager方法获取一个StringManager实例
 * 这些错误日志都写在该包properties文件内，这样我们就可以通过
 * Manager的getString方法传入该包的一个key获取该条错误日志信息
 * 错误日志信息格式如下：
 * =====================================================
 * contextBindings.unknownContext=Unknown context name : {0}
 * 
 * =====================================================
 * <p>The StringManager operates on a package basis. One StringManager
 * per package can be created and accessed via the getManager method
 * call.
 * 
 * <p>The StringManager will look for a ResourceBundle named by
 * the package name given plus the suffix of "LocalStrings". In
 * practice, this means that the localized information will be contained
 * in a LocalStrings.properties file located in the package
 * directory of the classpath.
 *
 * <p>Please see the documentation for java.util.ResourceBundle for
 * more information.
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Mel Martinez [mmartinez@g1440.com]
 * @see java.util.ResourceBundle
 */
public class StringManager {

	//单个包默认缓存的资源文件，比如a.b.c这个包，只能存储不超过10个国际化资源文件，默认提供了英,法，日，西班牙语四种
	//如果超过，默认会将放到Map中最老的那个删除，见getManager(String packageName, Locale locale)方法的实现
    private static int LOCALE_CACHE_SIZE = 10;

    /**
     * The ResourceBundle for this StringManager.
     */
    /*
     * StringManager就是通过这个类来读取本地化资源文件(LocalStrings_es.properties)
     * 这个类会自动根据国家地区码后缀匹配到正确的资源文件，从而实现本地化。
     * 这个类是java SE提供的，详细说明可以查看JDK文档
     * 
     */
    private final ResourceBundle bundle;
    
    private final Locale locale;

    /**
     * Creates a new StringManager for a given package. This is a
     * private method and all access to it is arbitrated by the
     * static getManager method call so that only one StringManager
     * per package will be created.
     *
     * @param packageName Name of package to create StringManager for.
     */
    private StringManager(String packageName, Locale locale) {
        String bundleName = packageName + ".LocalStrings";
        ResourceBundle bnd = null;
        try {
        	//ResourceBundle也是通过工程方法获取实例，根据传入的locale类读取对应的资源文件
            bnd = ResourceBundle.getBundle(bundleName, locale);
        } catch( MissingResourceException ex ) {
            // Try from the current loader (that's the case for trusted apps)
            // Should only be required if using a TC5 style classloader structure
            // where common != shared != server
        	/*
        	 * 这里当ResourceBundle类没有加载到资源文件抛出异常时，通过Tomcat的类加载器来加载
        	 * 这里是由于类加载器的权限导致
        	 */
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if( cl != null ) {
                try {
                    bnd = ResourceBundle.getBundle(bundleName, locale, cl);
                } catch(MissingResourceException ex2) {
                    // Ignore
                }
            }
        }
        bundle = bnd;
        // Get the actual locale, which may be different from the requested one
        if (bundle != null) {
            Locale bundleLocale = bundle.getLocale();
            if (bundleLocale.equals(Locale.ROOT)) {
            	//默认是英语
                this.locale = Locale.ENGLISH;
            } else {
                this.locale = bundleLocale;
            }
        } else {
        	//当拿不到locale的时候，也就说明这个资源文件没有加载成功
            this.locale = null;
        }
    }

    /**
        Get a string from the underlying resource bundle or return
        null if the String is not found.

        @param key to desired resource String
        @return resource String matching <i>key</i> from underlying
                bundle or null if not found.
        @throws IllegalArgumentException if <i>key</i> is null.
     */
    /*
     * 通过key来获取value
     * 这里获取的value是那种没有类似EL表达式{0} 的value
     */
    public String getString(String key) {
        if(key == null){
            String msg = "key may not have a null value";

            throw new IllegalArgumentException(msg);
        }

        String str = null;

        try {
            // Avoid NPE if bundle is null and treat it like an MRE
            if (bundle != null) {
                str = bundle.getString(key);
            }
        } catch(MissingResourceException mre) {
            //bad: shouldn't mask an exception the following way:
            //   str = "[cannot find message associated with key '" + key +
            //         "' due to " + mre + "]";
            //     because it hides the fact that the String was missing
            //     from the calling code.
            //good: could just throw the exception (or wrap it in another)
            //      but that would probably cause much havoc on existing
            //      code.
            //better: consistent with container pattern to
            //      simply return null.  Calling code can then do
            //      a null check.
            str = null;
        }

        return str;
    }

    /**
     * Get a string from the underlying resource bundle and format
     * it with the given set of arguments.
     *
     * @param key
     * @param args
     */
    /*
     * 通过MessageFormat来格式化传入的参数
     */
    public String getString(final String key, final Object... args) {
        String value = getString(key);
        if (value == null) {
            value = key;
        }

        MessageFormat mf = new MessageFormat(value);
        mf.setLocale(locale);
        return mf.format(args, new StringBuffer(), null).toString();
    }

    /**
     * Identify the Locale this StringManager is associated with
     */
    public Locale getLocale() {
        return locale;
    }

    // --------------------------------------------------------------
    // STATIC SUPPORT METHODS
    // --------------------------------------------------------------

    //各个包的Manager都保存在这个managers里，我们可以通过包名来获取
    private static final Map<String, Map<Locale,StringManager>> managers =
        new Hashtable<String, Map<Locale,StringManager>>();

    /**
     * Get the StringManager for a particular package. If a manager for
     * a package already exists, it will be reused, else a new
     * StringManager will be created and returned.
     *
     * @param packageName The package name
     */
    //这里加锁是因为每个包的Manager都是单例，当多个线程获取Manager的时候只要实例化
    //一个Manager即可
    public static final synchronized StringManager getManager(
            String packageName) {
        return getManager(packageName, Locale.getDefault());
    }

    /**
     * Get the StringManager for a particular package and Locale. If a manager
     * for a package/Locale combination already exists, it will be reused, else
     * a new StringManager will be created and returned.
     *
     * @param packageName The package name
     * @param locale      The Locale
     */
    public static final synchronized StringManager getManager(
            String packageName, Locale locale) {

        Map<Locale,StringManager> map = managers.get(packageName);
        //如果获取不到这个Manager，则生成一个
        if (map == null) {
            /*
             * Don't want the HashMap to be expanded beyond LOCALE_CACHE_SIZE.
             * Expansion occurs when size() exceeds capacity. Therefore keep
             * size at or below capacity.
             * removeEldestEntry() executes after insertion therefore the test
             * for removal needs to use one less than the maximum desired size
             *
             */
        	//LinkedHashMap原来还可以这样用。。。。
        	//这里accessOrder设置为true，也就是最少访问次数会最先删除
            map = new LinkedHashMap<Locale,StringManager>(LOCALE_CACHE_SIZE, 1, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Locale,StringManager> eldest) {
                    if (size() > (LOCALE_CACHE_SIZE - 1)) {
                        return true;
                    }
                    return false;
                }
            };
            managers.put(packageName, map);
        }

        StringManager mgr = map.get(locale);
        if (mgr == null) {
            mgr = new StringManager(packageName, locale);
            map.put(locale, mgr);
        }
        return mgr;
    }

    /**
     * Retrieve the StringManager for a list of Locales. The first StringManager
     * found will be returned.
     *
     * @param requestedLocales the list of Locales
     *
     * @return the found StringManager or the default StringManager
     */
    //当我们不知道有哪些资源文件时，可以一次传递许多个locale，最先查找到的被返回
    //比如我希望优先实现英语资源文件，当找不到英语资源文件时，显示法语，当找不到法语时显示西班牙语，找不到西班牙语时显示日语。如果连日语
    //也找不到时就没办法了。只要我们按优先级传入即可，它会优先选择返回
    public static StringManager getManager(String packageName,
            Enumeration<Locale> requestedLocales) {
        while (requestedLocales.hasMoreElements()) {
            Locale locale = requestedLocales.nextElement();
            StringManager result = getManager(packageName, locale);
            if (result.getLocale().equals(locale)) {
                return result;
            }
        }
        // Return the default
        return getManager(packageName);
    }
}
