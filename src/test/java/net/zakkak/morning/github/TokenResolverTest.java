package net.zakkak.morning.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Research R7. */
class TokenResolverTest {

    private static final List<String> NO_GH = List.of("/nonexistent/gh");

    @Test
    void githubTokenWins() {
        TokenResolver r = new TokenResolver(Map.of("GITHUB_TOKEN", "one", "GH_TOKEN", "two")::get, NO_GH);
        assertEquals("one", r.resolve().token());
    }

    @Test
    void ghTokenIsSecond() {
        assertEquals("two", new TokenResolver(Map.of("GITHUB_TOKEN", " ", "GH_TOKEN", "two")::get, NO_GH).resolve().token());
    }

    @Test
    void ghCliIsLast() {
        TokenResolver r = new TokenResolver(k -> null, List.of("sh", "-c", "echo '  from-gh  '"));
        assertEquals("from-gh", r.resolve().token());
    }

    @Test
    void failingGhCliMeansNotFound() {
        TokenResolver r = new TokenResolver(k -> null, List.of("sh", "-c", "echo oops; exit 1"));
        CredentialsException e = assertThrows(CredentialsException.class, r::resolve);
        assertEquals(TokenResolver.NOT_FOUND, e.getMessage());
    }

    @Test
    void missingGhCliMeansNotFound() {
        assertThrows(CredentialsException.class, new TokenResolver(k -> null, NO_GH)::resolve);
    }

    @Test
    void credentialsNeverPrintTheToken() {
        assertFalse(new GitHubCredentials("ghp_secret").toString().contains("secret"));
    }
}
