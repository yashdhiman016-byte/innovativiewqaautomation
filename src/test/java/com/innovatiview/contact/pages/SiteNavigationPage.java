package com.innovatiview.contact.pages;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import io.qameta.allure.Allure;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Page actions and URL assertions for the site header, menus, footer, and social links. */
public class SiteNavigationPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String siteRoot;
    private String lastAction = "Open home page";
    private String expectedUrl;

    public SiteNavigationPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        // One configurable origin supports local/staging runs while keeping production as the default.
        this.siteRoot = System.getProperty("siteUrl", "https://www.innovatiview.com").replaceAll("/+$", "");
        this.expectedUrl = siteRoot + "/";
    }

    public void openHome() {
        driver.get(siteRoot + "/");
        wait.until(d -> currentPath().equals("/"));
    }

    public void clickHeaderLink(String linkText, String expectedPath) {
        By link = By.xpath("//nav//a[normalize-space(.)='" + linkText + "']");
        clickAndExpectPath(link, expectedPath);
    }

    public void clickLogo() {
        clickAndExpectPath(By.xpath("//nav//a[@href='/' and .//img[contains(@alt,'Logo')]]"), "/");
    }

    public void resizeForMobileNavigation() {
        driver.manage().window().setSize(new Dimension(390, 844));
    }

    public void clickMobileMenuButton() {
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//nav//button"))).click();
    }

    public void clickMobileLink(String expectedPath) {
        lastAction = "Mobile navigation link: " + expectedPath;
        expectedUrl = siteRoot + normalizePath(expectedPath);
        Allure.parameter("Expected full URL", expectedUrl);
        By link = By.xpath("//a[@href='" + expectedPath + "']");
        // Responsive layouts may leave a hidden desktop copy of the same link in the DOM.
        WebElement visibleMenuLink = wait.until(d -> d.findElements(link).stream()
                .filter(WebElement::isDisplayed)
                .filter(element -> (Boolean) ((JavascriptExecutor) d).executeScript(
                        "const r=arguments[0].getBoundingClientRect(); return r.width>0 && r.height>0 && r.bottom>0 && r.top<innerHeight;", element))
                .findFirst()
                .orElse(null));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center',inline:'nearest'});", visibleMenuLink);
        wait.until(ExpectedConditions.elementToBeClickable(visibleMenuLink)).click();
        wait.until(d -> currentPath().equals(normalizePath(expectedPath)));
    }

    public void openMegaMenu(String menuName) {
        By menuLink = By.xpath("//nav//a[@href='#' and normalize-space(.)='" + menuName + "']");
        WebElement menu = wait.until(ExpectedConditions.visibilityOfElementLocated(menuLink));
        // Hover then click accommodates menus that reveal a panel on hover but need a click to stay open.
        new Actions(driver).moveToElement(menu).click().perform();
        wait.until(d -> currentPath().equals("/"));
    }

    public void navigateFromMegaMenu(String menuName, String expectedPath) {
        openMegaMenu(menuName);
        String menuPanel = menuName.equals("Services") ? "//nav/div[2]/div/div[2]" : "//nav/div[3]/div/div[2]";
        clickAndExpectPath(By.xpath(menuPanel + "//a[@href='" + expectedPath + "']"), expectedPath);
    }

    public void clickFooterLink(String expectedPath) {
        clickAndExpectPath(By.xpath("//footer//a[@href='" + expectedPath + "']"), expectedPath);
    }

    public void clickSocialLink(String accessibleName, String expectedUrl) {
        lastAction = "Social link: " + accessibleName;
        this.expectedUrl = expectedUrl;
        Allure.parameter("Expected full URL", expectedUrl);
        String parentHandle = driver.getWindowHandle();
        By socialLink = By.xpath("//footer//a[@aria-label='" + accessibleName + "']");
        WebElement link = wait.until(ExpectedConditions.elementToBeClickable(socialLink));
        String target = link.getAttribute("target");
        String href = link.getAttribute("href");
        // Validate the anchor contract before clicking, then verify the actual new browsing context.
        if (!"_blank".equals(target)) {
            throw new AssertionError(accessibleName + " should open in a new tab; target was " + target);
        }
        if (!expectedUrl.equals(href)) {
            throw new AssertionError(accessibleName + " href mismatch. Expected " + expectedUrl + " but was " + href);
        }

        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", link);
        wait.until(d -> d.getWindowHandles().size() > 1);
        Set<String> handles = driver.getWindowHandles();
        String newHandle = handles.stream().filter(handle -> !handle.equals(parentHandle))
                .findFirst().orElseThrow(() -> new AssertionError("Social link did not open a new tab"));
        driver.switchTo().window(newHandle);
        wait.until(d -> !"about:blank".equals(d.getCurrentUrl()));
        // Social sites can redirect internally; compare destination hosts rather than full tracking URLs.
        String actualHost = URI.create(driver.getCurrentUrl()).getHost();
        String expectedHost = URI.create(expectedUrl).getHost();
        if (actualHost == null || !actualHost.toLowerCase().contains(expectedHost.toLowerCase())) {
            throw new AssertionError(accessibleName + " opened unexpected host " + actualHost);
        }
        driver.close();
        driver.switchTo().window(parentHandle);
    }

    private void clickAndExpectPath(By locator, String expectedPath) {
        lastAction = "Navigate to " + expectedPath;
        expectedUrl = siteRoot + normalizePath(expectedPath);
        Allure.parameter("Expected full URL", expectedUrl);
        // Some responsive menus render duplicate links; interact with the visible instance only.
        WebElement link = wait.until(d -> d.findElements(locator).stream()
                .filter(WebElement::isDisplayed)
                .findFirst()
                .orElse(null));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center',inline:'nearest'});", link);
        wait.until(ExpectedConditions.elementToBeClickable(link)).click();
        wait.until(d -> currentPath().equals(normalizePath(expectedPath)));
    }

    public void attachNavigationEvidence() {
        if (driver == null) return;
        // Attach evidence during teardown so it is available for both passing and failing test cases.
        String actualUrl = driver.getCurrentUrl();
        String details = "Action: " + lastAction + "\nExpected full URL: " + expectedUrl
                + "\nActual full URL: " + actualUrl;
        Allure.addAttachment("Navigation URL evidence", "text/plain",
                new ByteArrayInputStream(details.getBytes(StandardCharsets.UTF_8)), "txt");
        if (driver instanceof TakesScreenshot screenshotDriver) {
            byte[] screenshot = screenshotDriver.getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment("Navigation page screenshot", "image/png",
                    new ByteArrayInputStream(screenshot), "png");
        }
    }

    public String fullUrl(String path) {
        return siteRoot + normalizePath(path);
    }

    private String currentPath() {
        // Compare path only: query strings and URL fragments are outside these navigation assertions.
        return normalizePath(URI.create(driver.getCurrentUrl()).getPath());
    }

    private static String normalizePath(String path) {
        // Treat /route and /route/ as equivalent; the home route is always represented by a single slash.
        if (path == null || path.isBlank()) return "/";
        String normalized = path.replaceAll("/+$", "");
        return normalized.isEmpty() ? "/" : normalized;
    }
}
