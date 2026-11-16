package net.zakkak.morning.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;

/**
 * End-to-end: the real CLI against WireMock fixtures (src/test/resources/fixtures/my-prs). Covers
 * spec 001 US1 scenarios 1–4, US3 scenarios 1–4, SC-001, SC-002 and SC-003.
 */
@QuarkusMainTest
class PrsCommandTest {

    /** Set by surefire (pom.xml) as GITHUB_TOKEN. */
    private static final String SENTINEL = "morning-test-sentinel-token";
    private static final String CONFIG = "src/test/resources/config/e2e.yaml";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String URL_PROPERTY = "morning.github.graphql-url";

    private static WireMockServer server;
    private static WireMock wiremock;

    /**
     * A local stand-in for GitHub, bound to localhost on a random port. The URL is passed as a
     * system property: JVM-wide, so the launched app sees it whatever its class loader
     * ({@code @QuarkusMainTest} does not inject test resources).
     */
    @BeforeAll
    static void startGitHub() {
        server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        server.start();
        wiremock = new WireMock("127.0.0.1", server.port());
        System.setProperty(URL_PROPERTY, server.baseUrl() + "/graphql");
    }

    @AfterAll
    static void stopGitHub() {
        System.clearProperty(URL_PROPERTY);
        server.stop();
    }

    @BeforeEach
    void stubs() throws IOException {
        wiremock.resetMappings();
        wiremock.resetRequests();
        stub("MyPrs", "search.json");
        stub("PrChecksPage", "checks-page.json");
        stub("BaseAndAttempts", "base-and-attempts.json");
    }

    private void stub(String operation, String fixture) throws IOException {
        wiremock.register(post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.operationName", equalTo(operation)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(Files.readString(Path.of("src/test/resources/fixtures/my-prs", fixture)))));
    }

    private int requests() {
        return wiremock.find(postRequestedFor(urlEqualTo("/graphql"))).size();
    }

    private static void assertNoSecret(LaunchResult result) {
        assertFalse(result.getOutput().contains(SENTINEL), "token in stdout");
        assertFalse(result.getErrorOutput().contains(SENTINEL), "token in stderr");
    }

    @Test
    void jsonReportIsSortedAndClassified(QuarkusMainLauncher launcher) throws IOException {
        LaunchResult result = launcher.launch("--config", CONFIG, "prs", "--format", "json");

        assertEquals(0, result.exitCode(), result.getErrorOutput());
        JsonNode report = JSON.readTree(result.getOutput());
        assertEquals("morning.my-prs/1", report.path("schema").asText());
        assertEquals("Mine", report.path("section").asText());

        // SC-002 / FR-004: groups in order, oldest update first within each group.
        List<Integer> order = report.path("prs").valueStream().map(p -> p.path("number").asInt()).toList();
        assertEquals(List.of(1, 4, 7, 3, 2, 5, 6), order);
        JsonNode prs = report.path("prs");

        // US1 scenario 1: changes requested first, action "address review".
        assertEquals("CHANGES_REQUESTED", prs.get(0).path("group").asText());
        assertEquals("ADDRESS_REVIEW", prs.get(0).path("actions").get(0).asText());

        // US1 scenario 4: earlier attempt passed.
        assertEquals("LIKELY_FLAKY", prs.get(1).path("failures").get(0).path("class").asText());

        // Paging: PR #7's second page adds a failing status; pending on base means not analysed.
        assertEquals(List.of("lint", "ci/page-two"),
                prs.get(2).path("failures").valueStream().map(f -> f.path("check").asText()).toList());
        assertEquals("NOT_ANALYSED", prs.get(2).path("failures").get(1).path("class").asText());

        // US1 scenario 3: passed on the newest base commit with that check.
        assertEquals("CI_FAILURE", prs.get(3).path("group").asText());
        assertEquals("NOT_ANALYSED", prs.get(3).path("failures").get(0).path("class").asText());

        // US1 scenario 2: fails on base too, so not in the CI-failure group.
        assertEquals("OTHER", prs.get(4).path("group").asText());
        assertEquals("UNRELATED_FAILS_ON_BASE", prs.get(4).path("failures").get(0).path("class").asText());
        assertEquals("failed on base a2a2a2a (2026-11-11)", prs.get(4).path("failures").get(0).path("evidence").asText());

        assertEquals("READY_TO_MERGE", prs.get(5).path("actions").get(0).asText());
        assertEquals("UNKNOWN", prs.get(6).path("mergeable").asText());

        // SC-001: search, one page, base-and-attempts.
        assertEquals(3, report.path("apiRequests").asInt());
        assertEquals(3, requests());
        assertNoSecret(result);
    }

    @Test
    void tokenOnlyTravelsInTheAuthorizationHeader(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", CONFIG, "prs", "--format", "md");

        assertEquals(0, result.exitCode(), result.getErrorOutput());
        for (LoggedRequest r : wiremock.find(postRequestedFor(urlEqualTo("/graphql")))) {
            assertEquals("Bearer " + SENTINEL, r.getHeader("Authorization"));
            assertFalse(r.getBodyAsString().contains(SENTINEL), "token in request body");
            assertFalse(r.getUrl().contains(SENTINEL), "token in URL");
        }
        assertNoSecret(result);
    }

    @Test
    void markdownTableHasLinksAndFailureLabels(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", CONFIG, "prs", "--format", "md");

        String out = result.getOutput();
        assertTrue(out.startsWith("## Mine (7 open)"), out);
        assertTrue(out.contains("[example-org/alpha#1](https://github.com/example-org/alpha/pull/1)"), out);
        assertTrue(out.contains("unrelated (fails on base): build"), out);
        assertTrue(out.contains("likely flaky: it"), out);
        assertTrue(out.indexOf("example-org/alpha#1]") < out.indexOf("example-org/beta#4]"), out);
    }

    @Test
    void ttyTableFitsTheWidth(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", CONFIG, "prs", "--format", "tty", "--width", "100");

        assertEquals(0, result.exitCode(), result.getErrorOutput());
        for (String line : result.getOutput().split("\n")) {
            assertTrue(line.codePointCount(0, line.length()) <= 100, line);
        }
    }

    @Test
    void globalOptionsWorkBeforeTheSubcommand(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", CONFIG, "--format", "json", "prs");
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        assertTrue(result.getOutput().contains("\"schema\""), result.getOutput());
    }

    @Test
    void noOpenPrs(QuarkusMainLauncher launcher) throws IOException {
        stub("MyPrs", "search-empty.json");
        LaunchResult result = launcher.launch("--config", CONFIG, "prs", "--format", "md");

        assertEquals(0, result.exitCode(), result.getErrorOutput());
        assertTrue(result.getOutput().contains("No open PRs"), result.getOutput());
        assertEquals(1, requests(), "no follow-up requests without PRs");
    }

    @Test
    void missingConfigPointsToExamples(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", "/nonexistent/config.yaml", "prs");

        assertEquals(2, result.exitCode());
        assertTrue(result.getErrorOutput().contains(
                "morning: no configuration found at /nonexistent/config.yaml. See examples/"), result.getErrorOutput());
        assertEquals(0, requests());
    }

    @Test
    void invalidConfigNamesSectionAndFieldBeforeAnyRequest(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", "src/test/resources/config/missing-repo.yaml", "prs");

        assertEquals(2, result.exitCode());
        assertTrue(result.getErrorOutput().contains("morning: invalid configuration src/test/resources/config/missing-repo.yaml: "
                + "morning.sections[1].repo: must not be null"), result.getErrorOutput());
        assertEquals(0, requests());
    }

    @Test
    void orgFiltersBecomeSearchQualifiers(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", "src/test/resources/config/e2e-orgs.yaml", "prs",
                "--section", "Mine", "--format", "json");

        assertEquals(0, result.exitCode(), result.getErrorOutput());
        List<LoggedRequest> searches = wiremock.find(postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.operationName", equalTo("MyPrs"))));
        assertEquals(1, searches.size(), "--section runs only the named section");
        assertTrue(searches.get(0).getBodyAsString().contains("org:example-org -org:other-org"),
                searches.get(0).getBodyAsString());
    }

    @Test
    void unknownSectionIsAUsageError(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", CONFIG, "prs", "--section", "Nope");

        assertEquals(2, result.exitCode());
        assertTrue(result.getErrorOutput().contains("no section named \"Nope\"; configured: \"Mine\", \"CI\""),
                result.getErrorOutput());
        assertEquals(0, requests());
    }

    @Test
    void allRunsMyPrsAndNotesUnimplementedSections(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--config", CONFIG, "--format", "md");

        assertEquals(0, result.exitCode(), result.getErrorOutput());
        assertTrue(result.getErrorOutput().contains(
                "morning: section \"CI\": ci-status is not supported yet (planned in feature 002)"),
                result.getErrorOutput());
        assertTrue(result.getOutput().contains("## Mine (7 open)"), result.getOutput());
    }

    @Test
    void githubErrorFailsTheSectionWithoutLeakingTheToken(QuarkusMainLauncher launcher) {
        wiremock.register(post(urlEqualTo("/graphql")).atPriority(1)
                .willReturn(aResponse().withStatus(401).withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Bad credentials\"}")));
        LaunchResult result = launcher.launch("--config", CONFIG, "prs", "--format", "md");

        assertEquals(1, result.exitCode());
        assertTrue(result.getErrorOutput().contains("morning: section \"Mine\": GitHub request MyPrs failed"),
                result.getErrorOutput());
        assertNoSecret(result);
    }
}
