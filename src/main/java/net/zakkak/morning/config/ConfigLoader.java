package net.zakkak.morning.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Loads the user's routine from YAML into {@link MorningConfig} (ADR-0008), the way Quarkus's own
 * CLI reads {@code ~/.quarkus/config.yaml}: a plain {@code --config} option and Jackson, not
 * SmallRye Config.
 * <p>
 * Order of checks: YAML syntax, secret-looking values (before anything can echo a value),
 * structure (Jackson), constraints (Bean Validation), then cross-field rules. All of this runs
 * before any API call.
 */
@ApplicationScoped
public class ConfigLoader {

    private static final List<String> SECTION_TYPES = List.of(MyPrsSection.TYPE, CiStatusSection.TYPE,
            ReviewCandidatesSection.TYPE, SummarySection.TYPE);
    private static final Pattern CAMEL = Pattern.compile("([a-z0-9])([A-Z])");

    private final YAMLMapper mapper = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();

    /** Writes enum constants by their {@code @JsonProperty} names, for error messages. */
    private static final ObjectMapper ENUM_NAMES = new ObjectMapper();

    private final Validator validator;
    private final Function<String, String> env;

    @Inject
    public ConfigLoader(Validator validator) {
        this(validator, System::getenv);
    }

    ConfigLoader(Validator validator, Function<String, String> env) {
        this.validator = validator;
        this.env = env;
    }

    /** The file that {@link #load(Path)} reads when {@code --config} is not given. */
    public Path defaultPath() {
        String xdg = env.apply("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".config");
        return base.resolve("morning").resolve("config.yaml");
    }

    /**
     * @param explicit the {@code --config} value, or null for the default location
     */
    public MorningConfig load(Path explicit) {
        Path file = explicit != null ? explicit : defaultPath();
        if (!Files.isRegularFile(file)) {
            throw new ConfigException(file, "no configuration found at " + file
                    + ". See examples/ in the morning repository, or pass --config.");
        }
        JsonNode tree;
        try {
            tree = mapper.readTree(file.toFile());
        } catch (JsonProcessingException e) {
            JsonLocation l = e.getLocation();
            String where = l == null ? "" : " at line " + l.getLineNr() + ", column " + l.getColumnNr();
            throw new ConfigException(file, List.of(new ConfigError("", "not valid YAML" + where)));
        } catch (IOException e) {
            throw new ConfigException(file, "cannot read configuration " + file + ": " + e.getMessage());
        }
        if (tree == null || tree.isMissingNode() || tree.isNull()) {
            throw new ConfigException(file, List.of(new ConfigError("", "the file is empty")));
        }

        List<ConfigError> errors = new ArrayList<>();
        scanForSecrets(tree, "", errors);
        if (!errors.isEmpty()) {
            throw new ConfigException(file, errors);
        }

        ConfigFile parsed;
        try {
            parsed = mapper.treeToValue(tree, ConfigFile.class);
        } catch (JsonMappingException e) {
            throw new ConfigException(file, List.of(describe(e)));
        } catch (JsonProcessingException e) {
            throw new ConfigException(file, List.of(new ConfigError("", "cannot be read: " + e.getOriginalMessage())));
        }

        for (ConstraintViolation<ConfigFile> v : validator.validate(parsed)) {
            errors.add(new ConfigError(yamlPath(v.getPropertyPath().toString()), v.getMessage()));
        }
        if (errors.isEmpty()) {
            crossFieldChecks(parsed.morning(), errors);
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparing(ConfigError::path));
            throw new ConfigException(file, errors);
        }
        return parsed.morning();
    }

    private static void scanForSecrets(JsonNode node, String path, List<ConfigError> errors) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String child = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                if (SecretPatterns.containsSecret(field.getKey())) {
                    errors.add(secretError(path));
                }
                scanForSecrets(field.getValue(), child, errors);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                scanForSecrets(node.get(i), path + "[" + i + "]", errors);
            }
        } else if (node.isTextual() && SecretPatterns.containsSecret(node.asText())) {
            errors.add(secretError(path));
        }
    }

    private static ConfigError secretError(String path) {
        return new ConfigError(path, "looks like a secret; secrets must come from the environment");
    }

    private void crossFieldChecks(MorningConfig config, List<ConfigError> errors) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < config.sections().size(); i++) {
            Section s = config.sections().get(i);
            String at = "morning.sections[" + i + "]";
            if (!names.add(s.name())) {
                errors.add(new ConfigError(at + ".name", "duplicate section name \"" + s.name() + "\""));
            }
            if (s instanceof MyPrsSection p) {
                Set<String> both = new HashSet<>(lower(p.includeOrgs()));
                both.retainAll(lower(p.excludeOrgs()));
                if (!both.isEmpty()) {
                    errors.add(new ConfigError(at + ".exclude-orgs",
                            "include-orgs and exclude-orgs must not overlap: " + String.join(", ", both.stream().sorted().toList())));
                }
            }
        }
    }

    private static List<String> lower(List<String> values) {
        return values.stream().map(String::toLowerCase).toList();
    }

    private ConfigError describe(JsonMappingException e) {
        String path = jacksonPath(e);
        if (e instanceof UnrecognizedPropertyException u) {
            String known = u.getKnownPropertyIds() == null ? ""
                    : "; expected one of: " + u.getKnownPropertyIds().stream().map(String::valueOf).sorted()
                            .collect(Collectors.joining(", "));
            return new ConfigError(path, "unknown key" + known);
        }
        if (e instanceof InvalidTypeIdException t) {
            String at = path + ".type";
            return t.getTypeId() == null
                    ? new ConfigError(at, "missing; expected one of: " + String.join(", ", SECTION_TYPES))
                    : new ConfigError(at, "unknown section type \"" + t.getTypeId() + "\"; expected one of: "
                            + String.join(", ", SECTION_TYPES));
        }
        if (e instanceof InvalidFormatException f && f.getTargetType() != null && f.getTargetType().isEnum()) {
            return new ConfigError(path, "invalid value; expected one of: " + enumValues(f.getTargetType()));
        }
        if (e instanceof MismatchedInputException m && m.getTargetType() != null) {
            return new ConfigError(path, "expected " + describeType(m.getTargetType()));
        }
        return new ConfigError(path, Optional.ofNullable(e.getOriginalMessage()).orElse("invalid value"));
    }

    private String enumValues(Class<?> type) {
        return Arrays.stream(type.getEnumConstants()).map(c -> {
            try {
                return ENUM_NAMES.writeValueAsString(c).replace("\"", "");
            } catch (JsonProcessingException ex) {
                return String.valueOf(c);
            }
        }).collect(Collectors.joining(", "));
    }

    private static String describeType(Class<?> type) {
        if (List.class.isAssignableFrom(type)) {
            return "a list";
        }
        if (Map.class.isAssignableFrom(type) || type.isRecord()) {
            return "a mapping";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true or false";
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return "a number";
        }
        return "a " + type.getSimpleName().toLowerCase();
    }

    private static String jacksonPath(JsonMappingException e) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    /** Bean Validation path ({@code morning.sections[1].includeOrgs[0].<list element>}) to YAML. */
    static String yamlPath(String beanPath) {
        String p = beanPath.replaceAll("\\.<[^>]+>", "");
        Matcher m = CAMEL.matcher(p);
        return m.replaceAll(r -> r.group(1) + "-" + r.group(2).toLowerCase());
    }
}
