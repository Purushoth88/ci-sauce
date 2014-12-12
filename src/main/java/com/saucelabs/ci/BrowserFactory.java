/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.saucelabs.ci;

import com.saucelabs.saucerest.SauceREST;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles invoking the Sauce REST API to retrieve the list of valid Browsers.  The list of browser is cached for
 * an hour.
 *
 * @author Ross Rowe
 */
public class BrowserFactory {

    private static final Logger logger = Logger.getLogger(BrowserFactory.class.getName());

    public static final String BROWSER_URL = "http://saucelabs.com/rest/v1/info/platforms";

    public static final int ONE_HOUR_IN_MILLIS = 1000 * 60 * 60;

    private SauceREST sauceREST;

    private Map<String, Browser> seleniumLookup = new HashMap<String, Browser>();
    private Map<String, Browser> appiumLookup = new HashMap<String, Browser>();
    private Map<String, Browser> webDriverLookup = new HashMap<String, Browser>();
    protected Timestamp lastLookup = null;
    private static final String IEHTA = "iehta";
    private static final String CHROME = "chrome";
    private static BrowserFactory instance;

    public BrowserFactory() {
        this(null);
    }

    public BrowserFactory(SauceREST sauceREST) {
        if (sauceREST == null) {
            this.sauceREST = new SauceREST(null, null);
        } else {
            this.sauceREST = sauceREST;
        }
        try {
            initializeSeleniumBrowsers();
            initializeWebDriverBrowsers();
            initializeAppiumBrowsers();
        } catch (IOException e) {
            //TODO exception could mean we're behind firewall
            logger.log(Level.WARNING, "Error retrieving browsers, attempting to continue", e);
        } catch (JSONException e) {
            logger.log(Level.WARNING, "Error retrieving browsers, attempting to continue", e);
        }
    }

    public List<Browser> getSeleniumBrowsers() throws IOException, JSONException {
        List<Browser> browsers;
        if (shouldRetrieveBrowsers()) {
            browsers = initializeSeleniumBrowsers();
        } else {
            browsers = new ArrayList<Browser>(seleniumLookup.values());
        }
        Collections.sort(browsers);

        return browsers;
    }

    public List<Browser> getAppiumBrowsers() throws IOException, JSONException {
        List<Browser> browsers;
        if (shouldRetrieveBrowsers()) {
            browsers = initializeAppiumBrowsers();
        } else {
            browsers = new ArrayList<Browser>(appiumLookup.values());
        }
        Collections.sort(browsers);

        return browsers;
    }

    public List<Browser> getWebDriverBrowsers() throws IOException, JSONException {
        List<Browser> browsers;
        if (shouldRetrieveBrowsers()) {
            browsers = initializeWebDriverBrowsers();
        } else {
            browsers = new ArrayList<Browser>(webDriverLookup.values());
        }
        Collections.sort(browsers);

        return browsers;
    }

    public boolean shouldRetrieveBrowsers() {
        return lastLookup == null || CacheTimeUtil.pastAcceptableDuration(lastLookup, ONE_HOUR_IN_MILLIS);
    }

    private List<Browser> initializeSeleniumBrowsers() throws IOException, JSONException {
        List<Browser> browsers = getSeleniumBrowsersFromSauceLabs();
        seleniumLookup = new HashMap<String, Browser>();
        for (Browser browser : browsers) {
            seleniumLookup.put(browser.getKey(), browser);
        }
        lastLookup = new Timestamp(new Date().getTime());
        return browsers;
    }

    private List<Browser> initializeAppiumBrowsers() throws IOException, JSONException {
        List<Browser> browsers = getAppiumBrowsersFromSauceLabs();
        appiumLookup = new HashMap<String, Browser>();
        for (Browser browser : browsers) {
            appiumLookup.put(browser.getKey(), browser);
        }
        lastLookup = new Timestamp(new Date().getTime());
        return browsers;
    }

    private List<Browser> initializeWebDriverBrowsers() throws IOException, JSONException {
        List<Browser> browsers = getWebDriverBrowsersFromSauceLabs();
        webDriverLookup = new HashMap<String, Browser>();
        for (Browser browser : browsers) {
            webDriverLookup.put(browser.getKey(), browser);
        }
        lastLookup = new Timestamp(new Date().getTime());
        return browsers;
    }

    private List<Browser> getSeleniumBrowsersFromSauceLabs() throws IOException, JSONException {
        String response = sauceREST.retrieveResults(new URL(BROWSER_URL + "/selenium-rc"));
        if (response.equals("")) {
            response = "[]";
        }
        List<Browser> browsers = getBrowserListFromJson(response);
        List<Browser> toRemove = new ArrayList<Browser>();
        for (Browser browser : browsers) {
            if (browser.getBrowserName().equals(CHROME)) {
                toRemove.add(browser);
            }
        }
        for (Browser browser : toRemove) {
            browsers.remove(browser);
        }
        return browsers;
    }

    private List<Browser> getWebDriverBrowsersFromSauceLabs() throws IOException, JSONException {
        String response = sauceREST.retrieveResults(new URL(BROWSER_URL + "/webdriver"));
        if (response.equals("")) {
            response = "[]";
        }
        return getBrowserListFromJson(response);
    }

    private List<Browser> getAppiumBrowsersFromSauceLabs() throws IOException, JSONException {
        String response = sauceREST.retrieveResults(new URL(BROWSER_URL + "/appium"));
        if (response.equals("")) {
            response = "[]";
        }
        return getBrowserListFromJson(response);
    }

    public SauceFactory getSauceAPIFactory() {
        return new SauceFactory();
    }

    /**
     * Parses the JSON response and constructs a List of {@link Browser} instances.
     *
     * @param browserListJson
     * @return
     * @throws JSONException
     */
    public List<Browser> getBrowserListFromJson(String browserListJson) throws JSONException {
        List<Browser> browsers = new ArrayList<Browser>();

        JSONArray browserArray = new JSONArray(browserListJson);
        for (int i = 0; i < browserArray.length(); i++) {
            JSONObject browserObject = browserArray.getJSONObject(i);
            String seleniumName = browserObject.getString("api_name");
            if (seleniumName.equals(IEHTA)) {
                //exclude these browsers from the list, as they replicate iexplore and firefox
                continue;
            }
            if (browserObject.has("device")) {
                //appium browser
                String longName = browserObject.getString("long_name");
                String longVersion = browserObject.getString("long_version");
                String osName = browserObject.getString("os");
                String shortVersion = browserObject.getString("short_version");
                String device = browserObject.getString("device");

                String deviceType = null;

                if (browserObject.has("device-type")) {
                    deviceType = browserObject.getString("device-type");
                }
                Browser browser = createBrowser(seleniumName, longName, longVersion, osName, device, deviceType, shortVersion, "portrait");
                browsers.add(browser);
                browser = createBrowser(seleniumName, longName, longVersion, osName, device, deviceType, shortVersion, "landscape");
                browsers.add(browser);


            } else {
                //webdriver/selenium browser
                String longName = browserObject.getString("long_name");
                String longVersion = browserObject.getString("long_version");
                String osName = browserObject.getString("os");
                String shortVersion = browserObject.getString("short_version");
                String browserKey = osName + seleniumName + shortVersion;
                //replace any spaces with _s
                browserKey = browserKey.replaceAll(" ", "_");
                String label = osName + " " + longName + " " + longVersion;
                browsers.add(new Browser(browserKey, osName, seleniumName, longName, shortVersion, longVersion, label));
            }
        }
        return browsers;
    }

    private Browser createBrowser(String seleniumName, String longName, String longVersion, String osName, String device, String deviceType, String shortVersion, String orientation) {
        String browserKey = device + orientation + seleniumName + longVersion;
        //replace any spaces with _s
        browserKey = browserKey.replaceAll(" ", "_");
        StringBuilder label = new StringBuilder();
        label.append(longName).append(' ');
        if (deviceType != null) {
            label.append(deviceType).append(' ');
        }
        label.append(longVersion);
        label.append('(').append(orientation).append(')');
        //add browser for both landscape and portrait orientation
        Browser browser = new Browser(browserKey, osName, seleniumName, longName, shortVersion, longVersion, label.toString());
        browser.setDevice(device);
        browser.setDeviceType(deviceType);
        browser.setDeviceOrientation(orientation);
        return browser;
    }

    /**
     * Return the selenium rc browser which matches the key.
     *
     * @param key
     * @return
     */
    public Browser seleniumBrowserForKey(String key) {
        return seleniumLookup.get(key);
    }

    public Browser seleniumBrowserForKey(String key, boolean useLatestVersion) {
        Browser browser = webDriverBrowserForKey(key);
        if (useLatestVersion) {
            return getLatestSeleniumBrowserVersion(browser);
        } else {
            return browser;
        }
    }

    private Browser getLatestSeleniumBrowserVersion(Browser originalBrowser) {
        Browser candidateBrowser = null;
        for (Browser browser : seleniumLookup.values()) {

            if (browser.getBrowserName().equals(originalBrowser.getBrowserName())
                    && browser.getOs().equals(originalBrowser.getOs())
                    && Integer.parseInt(browser.getLongVersion()) > Integer.parseInt(originalBrowser.getLongVersion())) {
                candidateBrowser = browser;
            }
        }
        return candidateBrowser == null ? originalBrowser : candidateBrowser;
    }

    /**
     * Return the web driver browser which matches the key.
     *
     * @param key
     * @return
     */
    public Browser webDriverBrowserForKey(String key) {
        return webDriverLookup.get(key);
    }

    public Browser webDriverBrowserForKey(String key, boolean useLatestVersion) {
        Browser browser = webDriverBrowserForKey(key);
        if (useLatestVersion) {
            return getLatestWebDriverBrowserVersion(browser);
        } else {
            return browser;
        }
    }

    private Browser getLatestWebDriverBrowserVersion(Browser originalBrowser) {
        Browser candidateBrowser = originalBrowser;
        for (Browser browser : webDriverLookup.values()) {

            try {
                if (browser.getBrowserName().equals(originalBrowser.getBrowserName())
                        && browser.getOs().equals(originalBrowser.getOs())
                        && Integer.parseInt(browser.getLongVersion()) > Integer.parseInt(candidateBrowser.getLongVersion())) {
                    candidateBrowser = browser;
                }
            } catch (NumberFormatException e) {
                continue;
            }
        }
        return candidateBrowser;
    }

    /**
     * Return the appium browser which matches the key.
     *
     * @param key
     * @return
     */

    public Browser appiumBrowserForKey(String key) {
        return appiumLookup.get(key);
    }

    /**
     * Returns a singleton instance of SauceFactory.  This is required because
     * remote agents don't have the Bamboo component plugin available, so the Spring
     * auto-wiring doesn't work.
     *
     * @return
     */
    public static BrowserFactory getInstance() {
        return getInstance(null);
    }

    public static BrowserFactory getInstance(SauceREST sauceREST) {
        if (instance == null) {
            instance = new BrowserFactory(sauceREST);
        }
        return instance;
    }

}
