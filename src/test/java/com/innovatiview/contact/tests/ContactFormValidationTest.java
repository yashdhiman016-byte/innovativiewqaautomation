package com.innovatiview.contact.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innovatiview.contact.pages.ContactPage;
import com.innovatiview.contact.utils.DriverFactory;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import org.junit.jupiter.api.DisplayName;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

@Epic("Innovatiview website")
@Feature("Contact form validation")
@DisplayName("Contact form validation")
class ContactFormValidationTest {
    // The test owns the browser; page actions are delegated to ContactPage (Page Object pattern).
    private WebDriver driver;
    private ContactPage contactPage;

    @BeforeEach
    void setUp() {
        driver = DriverFactory.createDriver();
        contactPage = new ContactPage(driver);
        contactPage.open(System.getProperty("baseUrl", "https://www.innovatiview.com/contact"));
    }

    @AfterEach
    void tearDown() {
        // Always close Chrome so parameterized invocations do not leak browser processes.
        if (driver != null) driver.quit();
    }

    @Test
    @DisplayName("Submit with all required fields empty")
    @Description("Submitting the empty form displays each mandatory field's inline error.")
    void emptySubmitShowsInlineValidationErrors() {
        contactPage.submit();
        List<String> errors = contactPage.getInlineErrors();
        attachEvidence("Empty mandatory fields", errors);
        assertEquals(List.of("Enter your first name", "Enter your work email",
                "Enter your phone number", "Select your inquiry type"), errors);
    }

    static Stream<Arguments> blankAndWhitespaceRequiredFields() {
        // Each argument is one independent JUnit invocation: readable case label, field, input, expected message.
        return Stream.of(
                Arguments.of("First Name is blank", "First Name", "", "Enter your first name"),
                Arguments.of("First Name contains whitespace", "First Name", "     ", "Enter your first name"),
                Arguments.of("Email is blank", "Email", "", "Enter your work email"),
                Arguments.of("Email contains whitespace", "Email", "          ", "Enter your work email"),
                Arguments.of("Phone is blank", "Phone No.", "", "Enter your phone number"),
                Arguments.of("Phone contains whitespace", "Phone No.", "     ", "Enter your phone number"),
                Arguments.of("Inquiry type is unselected", "Inquiry Type", "", "Select your inquiry type"),
                Arguments.of("Message contains whitespace", "Please let us know how we can be of help.", "       ", ""));
    }

    @DisplayName("Required field validation")
    @ParameterizedTest(name = "{0}")
    @MethodSource("blankAndWhitespaceRequiredFields")
    @Description("Blank or whitespace mandatory input must show a related inline message. Message body is optional, so its whitespace case is observed only.")
    void requiredAndWhitespaceCases(String caseName, String field, String value, String expectedError) {
        fillValidRequiredFieldsExcept(field);
        if (!field.equals("Inquiry Type")) contactPage.fillField(field, value);
        contactPage.submit();
        List<String> errors = contactPage.getInlineErrors();
        attachEvidence(caseName, errors);
        if (expectedError.isEmpty()) {
            Allure.addAttachment("Observed result", "text/plain",
                    "Message field has no required marker; validation behavior recorded without an assumed rule.");
        } else {
            assertTrue(errors.contains(expectedError), "Expected inline error '" + expectedError + "'; actual: " + errors);
            assertEquals(List.of(expectedError), errors, "Only the target field's error should be displayed");
        }
    }

    static Stream<Arguments> malformedEmails() {
        // Representative syntax failures exercise browser-level email constraints without duplicating test logic.
        return Stream.of("yash.example.com", "@example.com", "yash@", "yash@example",
                "yash..dhiman@example.com", "yash dhiman@example.com", "yash@@example.com",
                ".yash@example.com")
                .map(value -> Arguments.of("Reject malformed email: " + value, value));
    }

    @DisplayName("Email format validation")
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedEmails")
    @Description("Malformed email input must be rejected by browser validation or the site's related inline validation.")
    void malformedEmailShowsRelatedInlineError(String caseName, String email) {
        fillValidRequiredFieldsExcept("Email");
        contactPage.fillField("Email", email);
        contactPage.submit();
        List<String> errors = contactPage.getInlineErrors();
        String browserMessage = contactPage.getEmailBrowserValidationMessage();
        boolean browserValid = contactPage.isEmailValid();
        attachEvidence(caseName, errors, browserMessage);
        assertFalse(browserValid, "Browser accepted malformed email: " + email);
        assertTrue(!browserMessage.isBlank() || errors.stream().anyMatch(e -> e.toLowerCase().contains("email")),
                "Expected a browser or site email validation message; inline errors: " + errors);
        assertTrue(errors.stream().allMatch(e -> e.toLowerCase().contains("email")),
                "Unrelated inline validation message: " + errors);
    }

    static Stream<Arguments> invalidPhones() {
        // Include length, non-numeric, all-zero, and unsupported country-prefix examples.
        return Stream.of("987654321", "98765432101", "98765ABCDE", "98@76#43210", "0000000000", "+999987654321")
                .map(value -> Arguments.of("Reject invalid phone: " + value, value));
    }

    @DisplayName("Phone number validation")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPhones")
    @Description("Invalid phone values must produce a phone-related inline validation message.")
    void invalidPhoneShowsRelatedInlineError(String caseName, String phone) {
        fillValidRequiredFieldsExcept("Phone No.");
        contactPage.fillField("Phone No.", phone);
        contactPage.submit();
        List<String> errors = contactPage.getInlineErrors();
        attachEvidence(caseName, errors);
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("phone")),
                "Expected a phone-related inline message; actual: " + errors);
        assertEquals(1, errors.size(), "Only the Phone field's inline error should be displayed");
    }

    static Stream<Arguments> unruledTextInputs() {
        // These values probe input handling; absent a documented rule, the test records behavior instead of inventing one.
        String script = "<script>alert(1)</script>";
        String longValue = "A".repeat(256);
        return Stream.of(
                Arguments.of("First name accepts numeric input", "First Name", "123456"),
                Arguments.of("First name accepts alphanumeric input", "First Name", "Yash123"),
                Arguments.of("First name accepts symbols", "First Name", "@#$%"),
                Arguments.of("First name handles script-like text", "First Name", script),
                Arguments.of("First name handles 256 characters", "First Name", longValue),
                Arguments.of("Email handles surrounding whitespace", "Email", "  yash@example.com  "),
                Arguments.of("Phone handles surrounding whitespace", "Phone No.", "  9876543210  "),
                Arguments.of("Message handles script-like text", "Please let us know how we can be of help.", script),
                Arguments.of("Message handles SQL-like text", "Please let us know how we can be of help.", " OR 1=1 --"),
                Arguments.of("Message handles 256 characters", "Please let us know how we can be of help.", longValue));
    }

    @DisplayName("Input handling and sanitization")
    @ParameterizedTest(name = "{0}")
    @MethodSource("unruledTextInputs")
    @Description("Input is submitted and visible outcome recorded. No unprovided business validation rule is assumed; any displayed messages are captured in Allure.")
    void recordUnruledInputBehavior(String caseName, String field, String value) {
        fillValidRequiredFieldsExcept(field);
        contactPage.fillField(field, value);
        contactPage.submit();
        List<String> errors = contactPage.getInlineErrors();
        attachEvidence(caseName, errors);
        boolean retained = contactPage.isValuePresent(field, value);
        Allure.addAttachment("Field value retained", "text/plain", Boolean.toString(retained));
        assertTrue(errors.isEmpty(), "Unexpected inline validation message for an input with no specified rejection rule: " + errors);
    }

    @Test
    @DisplayName("Reject an unknown inquiry type")
    @Description("Tampered inquiry type must be rejected with a related inline error.")
    void tamperedInquiryTypeShowsRelatedInlineError() {
        fillValidRequiredFieldsExcept("Inquiry Type");
        contactPage.setTamperedInquiryType("Unknown Type");
        contactPage.submit();
        List<String> errors = contactPage.getInlineErrors();
        attachEvidence("Tampered inquiry type", errors);
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("inquiry")),
                "Expected inquiry-related inline message; actual: " + errors);
        assertEquals(1, errors.size(), "Only the Inquiry Type field's inline error should be displayed");
    }

    @Test
    @DisplayName("Repeated invalid submissions retain required field errors")
    @Description("Repeated invalid submissions should continue to expose only the applicable required-field errors.")
    void repeatedInvalidSubmitKeepsRelevantErrors() {
        // Re-submit the same invalid form to catch errors that disappear or accumulate after repeated interaction.
        for (int attempt = 1; attempt <= 3; attempt++) {
            contactPage.submit();
            List<String> errors = contactPage.getInlineErrors();
            attachEvidence("Repeated invalid submit attempt " + attempt, errors);
            assertEquals(List.of("Enter your first name", "Enter your work email",
                    "Enter your phone number", "Select your inquiry type"), errors);
        }
    }

    private void fillValidRequiredFieldsExcept(String targetField) {
        // Isolate a single field's behavior by satisfying the other required controls first.
        if (!targetField.equalsIgnoreCase("First Name")) contactPage.fillField("First Name", "Yash");
        if (!targetField.equalsIgnoreCase("Email")) contactPage.fillField("Email", "yash@example.com");
        if (!targetField.equalsIgnoreCase("Phone No.")) contactPage.fillField("Phone No.", "9876543210");
        if (!targetField.equalsIgnoreCase("Inquiry Type")) contactPage.selectInquiryType("General Inquiry");
    }

    @Step("Attach inline validation messages and screenshot to Allure")
    private void attachEvidence(String caseName, List<String> errors) {
        attachEvidence(caseName, errors, "");
    }

    private void attachEvidence(String caseName, List<String> errors, String browserValidationMessage) {
        // Human-readable text plus a screenshot makes a CI failure diagnosable without rerunning locally.
        String reportText = "Case: " + caseName + "\n" +
                (errors.isEmpty() ? "No site inline validation errors found." : String.join("\n", errors)) +
                (browserValidationMessage.isBlank() ? "" : "\nBrowser validation message: " + browserValidationMessage);
        Allure.addAttachment("Inline validation - " + caseName, "text/plain",
                new ByteArrayInputStream(reportText.getBytes(StandardCharsets.UTF_8)), "txt");
        if (driver instanceof TakesScreenshot screenshotDriver) {
            byte[] screenshot = captureFormScreenshot(screenshotDriver);
            Allure.addAttachment("Screenshot - " + caseName, "image/png",
                    new ByteArrayInputStream(screenshot), "png");
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] captureFormScreenshot(TakesScreenshot screenshotDriver) {
        if (driver instanceof ChromeDriver chromeDriver) {
            try {
                // Chrome DevTools can capture just the whole form, including portions below the viewport.
                Map<String, Object> formBounds = (Map<String, Object>) ((JavascriptExecutor) driver)
                        .executeScript("const r=document.querySelector('form.form-class').getBoundingClientRect();"
                                + "return {x:r.left+window.scrollX,y:r.top+window.scrollY,width:r.width,height:r.height};");
                Map<String, Object> clip = Map.of(
                        "x", ((Number) formBounds.get("x")).doubleValue(),
                        "y", ((Number) formBounds.get("y")).doubleValue(),
                        "width", ((Number) formBounds.get("width")).doubleValue(),
                        "height", ((Number) formBounds.get("height")).doubleValue(),
                        "scale", 1);
                Map<String, Object> screenshot = chromeDriver.executeCdpCommand("Page.captureScreenshot",
                        Map.of("format", "png", "captureBeyondViewport", true, "clip", clip));
                return Base64.getDecoder().decode((String) screenshot.get("data"));
            } catch (RuntimeException ignored) {
                // Fall back to Selenium's portable viewport screenshot if CDP clipping is unavailable.
            }
        }
        return screenshotDriver.getScreenshotAs(OutputType.BYTES);
    }
}
