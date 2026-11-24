package co.com.david.inversiones.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Entry point the {@code acceptanceTest} Gradle task runs: a JUnit Platform {@link Suite} that
 * delegates to the Cucumber engine, discovering every {@code .feature} file under
 * {@code src/acceptanceTest/resources/features} and every step definition under
 * {@code co.com.david.inversiones.acceptance}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "co.com.david.inversiones.acceptance")
/*
 * `html:` writes the Gherkin-shaped report the pipeline publishes. Gradle also writes a JUnit
 * report at build/reports/tests/acceptanceTest, but that one lists Java test methods; this lists
 * the scenarios and steps in the business language they were written in, which is the reason for
 * using Cucumber at all.
 *
 * The path is relative on purpose: Gradle runs this task with the module directory as its working
 * directory, so it resolves to applications/app-service/build/reports/cucumber/report.html. It has
 * to live in this annotation rather than in build.gradle, because a @ConfigurationParameter here
 * takes precedence over a `systemProperty 'cucumber.plugin'` there — setting it in Gradle is
 * silently ignored and produces no report while the build stays green.
 */
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty, summary, html:build/reports/cucumber/report.html")
public class RunCucumberTest {
}
