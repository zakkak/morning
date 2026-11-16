package net.zakkak.morning.github;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.graphql.client.GraphQLError;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

/**
 * Runs the checked-in {@link Query} documents, with values only as variables. The single place
 * that touches {@link DynamicGraphQLClient} besides its producer (ArchUnit-enforced). Counts
 * requests for SC-001.
 */
public final class GraphQLQueries implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DynamicGraphQLClient client;
    private int requests;

    GraphQLQueries(DynamicGraphQLClient client) {
        this.client = client;
    }

    /** @return the {@code data} object of the response */
    public JsonNode execute(Query query, Map<String, Object> variables) {
        requests++;
        Response response;
        try {
            response = client.executeSync(query.document(), variables, query.operationName());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new GitHubException("GitHub request " + query.operationName() + " failed: " + brief(cause), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("GitHub request " + query.operationName() + " was interrupted", e);
        } catch (RuntimeException e) {
            throw new GitHubException("GitHub request " + query.operationName() + " failed: " + brief(e), e);
        }
        if (response.hasError()) {
            String messages = response.getErrors().stream().map(GraphQLError::getMessage)
                    .collect(Collectors.joining("; "));
            throw new GitHubException("GitHub request " + query.operationName() + " failed: " + messages);
        }
        if (response.getData() == null) {
            throw new GitHubException("GitHub request " + query.operationName() + " returned no data");
        }
        try {
            return JSON.readTree(response.getData().toString());
        } catch (JsonProcessingException e) {
            throw new GitHubException("GitHub response " + query.operationName() + " is not valid JSON", e);
        }
    }

    /** The status line of an HTTP error, without the response body; a hint for auth failures. */
    static String brief(Throwable e) {
        String m = String.valueOf(e.getMessage());
        int body = m.indexOf(", body=");
        String line = (body >= 0 ? m.substring(0, body) : m).lines().findFirst().orElse("").strip();
        if (line.contains("Code=401")) {
            return line + " (check the GitHub token)";
        }
        return line;
    }

    /** Requests made so far through this instance. */
    public int requests() {
        return requests;
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            // nothing useful to do on shutdown
        }
    }
}
