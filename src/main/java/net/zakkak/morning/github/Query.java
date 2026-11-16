package net.zakkak.morning.github;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The checked-in GraphQL documents under {@code src/main/resources/graphql/}. This enum is the
 * only way to run a query (constitution Principle III): there is no API that takes document
 * text.
 */
public enum Query {
    MY_PRS("my-prs", "MyPrs"),
    PR_CHECKS_PAGE("pr-checks-page", "PrChecksPage"),
    BASE_AND_ATTEMPTS("base-and-attempts", "BaseAndAttempts");

    private static final Pattern COMMENT = Pattern.compile("#[^\\n]*");
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(mutation|subscription)\\b");

    private final String resource;
    private final String operationName;
    private volatile String document;

    Query(String file, String operationName) {
        this.resource = "/graphql/" + file + ".graphql";
        this.operationName = operationName;
    }

    public String operationName() {
        return operationName;
    }

    public String resource() {
        return resource;
    }

    /** The document text, checked to contain no mutation or subscription. */
    public String document() {
        String d = document;
        if (d == null) {
            d = checkReadOnly(resource, read(resource));
            document = d;
        }
        return d;
    }

    static String checkReadOnly(String name, String text) {
        if (FORBIDDEN.matcher(COMMENT.matcher(text).replaceAll("")).find()) {
            throw new IllegalStateException(name + " contains a mutation or subscription; only queries are allowed");
        }
        return text;
    }

    private static String read(String resource) {
        try (InputStream in = Query.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing GraphQL document " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
