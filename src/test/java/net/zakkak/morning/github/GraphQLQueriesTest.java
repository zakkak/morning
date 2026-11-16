package net.zakkak.morning.github;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Constitution Principle III: only queries. */
class GraphQLQueriesTest {

    @ParameterizedTest
    @EnumSource(Query.class)
    void everyDocumentLoads(Query query) {
        assertDoesNotThrow(query::document);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mutation AddComment { addComment(input: {}) { clientMutationId } }",
            "query A { a }\nmutation B { b }",
            "subscription S { s }" })
    void writesAreRejected(String document) {
        assertThrows(IllegalStateException.class, () -> Query.checkReadOnly("test", document));
    }

    @Test
    void commentsMayMentionMutations() {
        assertDoesNotThrow(() -> Query.checkReadOnly("test", "# no mutation here\nquery A { viewerSubscription }"));
    }
}
