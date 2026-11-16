package net.zakkak.morning.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.quarkus.test.junit.QuarkusTest;

/** Spec 001, US3. */
@QuarkusTest
class ConfigLoaderTest {

    @Inject
    ConfigLoader loader;
    @Inject
    Validator validator;

    private static Path fixture(String name) {
        return Path.of("src/test/resources/config", name);
    }

    @Test
    void exampleConfigParses() {
        MorningConfig config = loader.load(Path.of("examples/zakkak.yaml"));
        assertEquals(4, config.sections().size());
        MyPrsSection prs = assertInstanceOf(MyPrsSection.class, config.sections().get(0));
        assertEquals("My PRs", prs.name());
        CiStatusSection ci = assertInstanceOf(CiStatusSection.class, config.sections().get(1));
        assertEquals("graalvm/mandrel", ci.repo());
        assertEquals(CiStatusSection.Strategy.WORKFLOW_INPUTS, ci.tracking().strategy());
        assertEquals(List.of("oracle/graal"), ci.relatedRepos());
        assertInstanceOf(ReviewCandidatesSection.class, config.sections().get(2));
        assertEquals("summary", config.sections().get(3).name());
        assertEquals(15, config.llm().maxCalls());
        assertTrue(config.llm().enabled());
    }

    @Test
    void minimalConfigGetsDefaults() {
        MorningConfig config = loader.load(fixture("valid-minimal.yaml"));
        MyPrsSection prs = (MyPrsSection) config.sections().get(0);
        assertEquals("my-prs", prs.name());
        assertEquals(List.of(), prs.includeOrgs());
        assertEquals(LlmSettings.DEFAULT, config.llm());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "unknown-type.yaml   | morning.sections[1].type         | unknown section type \"weather\"",
            "missing-type.yaml   | morning.sections[0].type         | missing",
            "unknown-key.yaml    | morning.sections[0].include-org  | unknown key; expected one of: exclude-orgs, include-orgs, name",
            "missing-repo.yaml   | morning.sections[1].repo         | must not be null",
            "overlap.yaml        | morning.sections[0].exclude-orgs | must not overlap: beta",
            "bad-org.yaml        | morning.sections[0].include-orgs[1] | must be a GitHub login",
            "secret.yaml         | morning.sections[0].name         | looks like a secret",
            "duplicate-name.yaml | morning.sections[1].name         | duplicate section name \"my-prs\"",
            "empty-sections.yaml | morning.sections                 | must not be empty",
            "bad-enum.yaml       | morning.sections[0].order[1]     | expected one of: changes-requested, my-ci-failures",
            "not-yaml.yaml       | ''                               | not valid YAML at line",
    })
    void invalidConfigNamesThePath(String file, String path, String message) {
        ConfigException e = assertThrows(ConfigException.class, () -> loader.load(fixture(file)));
        ConfigError first = e.errors().get(0);
        assertEquals(path, first.path(), e.getMessage());
        assertTrue(first.message().contains(message), first.message());
    }

    @Test
    void secretValueIsNeverEchoed() {
        ConfigException e = assertThrows(ConfigException.class, () -> loader.load(fixture("secret.yaml")));
        assertFalse(e.getMessage().contains("ghp_"), e.getMessage());
    }

    @Test
    void missingFileExplainsWhatToDo() {
        ConfigException e = assertThrows(ConfigException.class, () -> loader.load(fixture("nope.yaml")));
        assertTrue(e.getMessage().startsWith("no configuration found at "), e.getMessage());
        assertTrue(e.getMessage().contains("examples/"), e.getMessage());
    }

    @Test
    void defaultPathHonoursXdgConfigHome() {
        assertEquals(Path.of("/x/morning/config.yaml"),
                new ConfigLoader(validator, Map.of("XDG_CONFIG_HOME", "/x")::get).defaultPath());
        assertEquals(Path.of(System.getProperty("user.home"), ".config/morning/config.yaml"),
                new ConfigLoader(validator, k -> null).defaultPath());
    }

    @Test
    void beanPathsBecomeYamlPaths() {
        assertEquals("morning.sections[1].include-orgs[0]",
                ConfigLoader.yamlPath("morning.sections[1].includeOrgs[0].<list element>"));
    }
}
