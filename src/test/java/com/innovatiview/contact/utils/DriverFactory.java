package com.innovatiview.contact.utils;

import java.time.Duration;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public final class DriverFactory {
    // Utility class: browser creation is centralized and instances are not meaningful.
    private DriverFactory() {}

    public static WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        // CI opts into headless mode with -Dheadless=true; local runs remain visible by default.
        if (Boolean.parseBoolean(System.getProperty("headless", "false"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1440,1200", "--disable-notifications");
        // Selenium Manager resolves a compatible ChromeDriver, avoiding a hard-coded local driver path.
        WebDriver driver = new ChromeDriver(options);
        // The suite uses explicit waits so implicit waits stay zero and do not compound wait durations.
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        // Bound full navigations in case the external site or network becomes unresponsive.
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
        return driver;
    }
}
