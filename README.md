# Innovatiview contact form and navigation automation

Java 17, Selenium, JUnit 5, and Allure tests for the Innovatiview website.

## How the project is organized

- `pages/` contains Page Objects: Selenium selectors and browser actions for the contact form and site navigation.
- `tests/` contains JUnit 5 scenarios and expected outcomes; parameterized tests reuse one scenario across multiple input values or routes.
- `utils/DriverFactory.java` creates a consistently configured Chrome session for each test.
- `src/test/resources/allure.properties` selects where the Allure JUnit adapter writes raw results.
- `.github/workflows/daily-qa-report.yml` runs the suite, builds and publishes an Allure report, retains a ZIP artifact, and emails the report link.
- `pom.xml` sets Java/dependency versions and configures Surefire and Allure Maven.

The tests use explicit Selenium waits, a fresh browser per test, and Allure text/screenshot attachments to make failures easier to diagnose. The contact suite checks required fields, malformed email and phone input, manipulated inquiry type, repeated submission, and input cases where no business rule is specified. The navigation suite covers desktop header links, a mobile menu, mega menus, footer routes, and social links that open new tabs.

For an interview explanation: the test class states *what* behavior is expected, the Page Object encapsulates *how* the UI is located and operated, and the driver factory centralizes browser setup. GitHub Actions runs the same Maven suite in headless mode, then publishes the result artifacts even when assertions fail so the failure remains inspectable.

## Run locally

```bash
mvn test
```

Allure result files are written to `target/allure-results`.

## Daily GitHub Actions report

The `Daily Innovatiview QA report` workflow runs at **9:00 AM India Standard Time** every day. It can also be started manually from **GitHub → Actions → Daily Innovatiview QA report → Run workflow**. It runs on the repository's default branch. It does not send an email for every code push.

The workflow runs Chrome headlessly, generates a static Allure report, publishes it to GitHub Pages, uploads a downloadable ZIP artifact, and emails the ZIP with the test outcome and links to the live report and CI run. It generates and emails the report after a test failure too, then marks the workflow failed so GitHub shows the test result accurately. If report generation fails, the email step is skipped. If required email settings are missing, the workflow reports which setting name is missing without exposing its value.

The published report is accessible to anyone who can reach the Pages URL. Treat report contents and screenshots as public; use test data that contains no personal or confidential information. GitHub Pages availability and private-site access depend on your GitHub plan and organization settings.

### Configure the repository

1. Push this project to a GitHub repository's default branch. The scheduled workflow runs from that branch.
2. In **Settings → Pages**, choose **GitHub Actions** as the build and deployment source.
3. In **Settings → Secrets and variables → Actions**, add these repository settings:

   **Repository secrets**

   - `GMAIL_APP_PASSWORD`: a Gmail App Password for the sender account. Do not use the regular Gmail password.
   - `GMAIL_FROM`: the sender Gmail address.
   - `GMAIL_TO`: the recipient address. Multiple recipients can be comma-separated.

   **Optional repository variable**

   - `SITE_URL` (optional): site origin without a trailing slash. Defaults to `https://www.innovatiview.com`.

4. Under **Settings → Actions → General → Workflow permissions**, allow read access to repository contents. The workflow declares its Pages deployment permissions itself.
5. Run the workflow once manually and check **Actions** for the run, **Settings → Pages** for the live site, and the email inbox.

Use a Gmail App Password for `GMAIL_APP_PASSWORD`, not your regular Gmail password. A previously shared App Password should be revoked and replaced before use. Store it only as a GitHub Actions secret; never commit it or paste it into workflow files.

The schedule is configured in `.github/workflows/daily-qa-report.yml` with `cron: '0 9 * * *'` and `timezone: Asia/Kolkata`. Change those values to adjust the time or timezone; remove the `schedule` trigger to disable daily runs while keeping manual runs. GitHub may start scheduled workflows a little later during periods of high load. See [GitHub's workflow syntax documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax) for timezone-aware schedules.

The email contains a ZIP attachment of the generated static report. The Actions artifact is retained for 14 days. The Pages URL serves the latest successful deployment, so use the run link to find a specific execution and its downloadable artifact.

### Push this local folder to GitHub

Create an empty repository on GitHub (do not initialize it with a README), then run these commands in this folder, replacing the branch and URL as needed. Use the branch name configured as the repository's default branch:

```bash
git init -b <DEFAULT_BRANCH>
git add .
git commit -m "Add Innovatiview Selenium automation and daily Allure report"
git remote add origin https://github.com/<YOUR_GITHUB_USER>/<YOUR_REPOSITORY>.git
git push -u origin <DEFAULT_BRANCH>
```

Do not add Gmail credentials to source files or commit them. The Gmail App Password should be stored only as `GMAIL_APP_PASSWORD` in GitHub Actions Secrets.
