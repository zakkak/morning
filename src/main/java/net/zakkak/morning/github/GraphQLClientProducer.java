package net.zakkak.morning.github;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Builds the GitHub GraphQL client. The only reader of {@link GitHubCredentials#token()}
 * (ArchUnit-enforced): the token goes into the Authorization header and nowhere else.
 */
@ApplicationScoped
public class GraphQLClientProducer {

    @ConfigProperty(name = "morning.github.graphql-url")
    String url;

    public GraphQLQueries open(GitHubCredentials credentials) {
        return new GraphQLQueries(DynamicGraphQLClientBuilder.newBuilder()
                .url(url)
                .header("Authorization", "Bearer " + credentials.token())
                .header("User-Agent", "morning")
                .build());
    }
}
