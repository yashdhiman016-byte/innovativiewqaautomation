package com.innovatiview.contact.pages;

import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for the contact form. Locators and browser interactions live here so tests can
 * describe user behavior without depending on the page's DOM implementation details.
 */
public class ContactPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    // Keep selectors scoped to the form so similarly named elements elsewhere on the site do not match.
    private static final By FORM = By.cssSelector("form.form-class");
    private static final By SEND_BUTTON = By.cssSelector("form.form-class button[type='submit']");
    // Match only the red inline validation spans, excluding unrelated site spans.
    private static final By INLINE_ERRORS = By.cssSelector("form.form-class span[class*='text-[#EA412C]']");
    private static final By FIELD_LABELS = By.cssSelector("form.form-class .form-listing-heading");

    public ContactPage(WebDriver driver) {
        this.driver = driver;
        // Explicit waits are preferred to fixed sleeps because they wait only as long as the UI needs.
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void open(String url) {
        driver.get(url);
        // The form is the readiness signal for this page, rather than merely waiting for navigation.
        wait.until(ExpectedConditions.visibilityOfElementLocated(FORM));
    }

    public void submitEmptyForm() {
        submit();
    }

    public void submit() {
        WebElement sendButton = wait.until(ExpectedConditions.presenceOfElementLocated(SEND_BUTTON));
        // Place the button near the top of the viewport so sticky/footer content cannot cover it.
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", sendButton);
        wait.until(ExpectedConditions.elementToBeClickable(SEND_BUTTON)).click();
    }

    public List<String> getInlineErrors() {
        // Return normalized visible text, giving tests stable values to assert and attach to Allure.
        return driver.findElements(INLINE_ERRORS).stream()
                .map(WebElement::getText)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    public void fillField(String label, String value) {
        // Resolve fields by their visible labels so the test data stays readable and less coupled to IDs.
        WebElement labelElement = driver.findElements(FIELD_LABELS).stream()
                .filter(e -> e.getText().replace("*", "").trim().equalsIgnoreCase(label))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown form field: " + label));
        WebElement container = labelElement.findElement(By.xpath(".."));
        WebElement input = container.findElement(By.cssSelector("input, textarea"));
        input.clear();
        if (value != null && !value.isEmpty()) input.sendKeys(value);
    }

    public void selectInquiryType(String value) {
        WebElement labelElement = driver.findElements(FIELD_LABELS).stream()
                .filter(e -> e.getText().replace("*", "").trim().equalsIgnoreCase("Inquiry Type"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Inquiry Type field not found"));
        new Select(labelElement.findElement(By.xpath("../div[contains(@class,'select-wrapper')]//select")))
                .selectByValue(value);
    }

    public void setTamperedInquiryType(String value) {
        // Deliberately add an unsupported option to exercise client-side handling of manipulated input.
        WebElement labelElement = driver.findElements(FIELD_LABELS).stream()
                .filter(e -> e.getText().replace("*", "").trim().equalsIgnoreCase("Inquiry Type"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Inquiry Type field not found"));
        WebElement select = labelElement.findElement(By.xpath("../div[contains(@class,'select-wrapper')]//select"));
        ((JavascriptExecutor) driver).executeScript(
                "const option=document.createElement('option'); option.value=arguments[1]; option.text=arguments[1]; arguments[0].add(option); arguments[0].value=arguments[1]; arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
                select, value);
    }

    public boolean isValuePresent(String label, String value) {
        // Used by exploratory input cases to observe whether the browser retained the entered value.
        WebElement labelElement = driver.findElements(FIELD_LABELS).stream()
                .filter(e -> e.getText().replace("*", "").trim().equalsIgnoreCase(label))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown form field: " + label));
        WebElement field = labelElement.findElement(By.xpath("../input | ../textarea"));
        return value.equals(field.getAttribute("value"));
    }

    public boolean isEmailValid() {
        // Read HTML5 constraint-validation state directly; the browser may block submission before site JS runs.
        return (Boolean) ((JavascriptExecutor) driver).executeScript(
                "return arguments[0].validity.valid;", fieldElement("Email"));
    }

    public String getEmailBrowserValidationMessage() {
        // Preserve the native browser message as diagnostic evidence when email validation blocks submission.
        return (String) ((JavascriptExecutor) driver).executeScript(
                "return arguments[0].validationMessage;", fieldElement("Email"));
    }

    private WebElement fieldElement(String label) {
        WebElement labelElement = driver.findElements(FIELD_LABELS).stream()
                .filter(e -> e.getText().replace("*", "").trim().equalsIgnoreCase(label))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown form field: " + label));
        return labelElement.findElement(By.xpath("../input"));
    }
}
