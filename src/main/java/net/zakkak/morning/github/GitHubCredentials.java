package net.zakkak.morning.github;

/**
 * The GitHub token. Only {@link GraphQLClientProducer} may read it (ArchUnit-enforced). It is
 * never logged: {@link #toString()} is masked.
 */
public record GitHubCredentials(String token) {
    @Override
    public String toString() {
        return "GitHubCredentials[***]";
    }
}
