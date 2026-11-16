package net.zakkak.morning.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.Validator;

/**
 * Every checked-in document parses, holds only {@code query} operations, and validates against
 * GitHub's vendored public schema ({@code src/test/resources/github/SCHEMA_VERSION}).
 */
class GraphQLDocumentsTest {

    private static GraphQLSchema schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        String sdl = Files.readString(Path.of("src/test/resources/github/schema.docs.graphql"));
        schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(new SchemaParser().parse(sdl));
    }

    static Stream<Path> documents() throws IOException {
        return Files.list(Path.of("src/main/resources/graphql")).filter(p -> p.toString().endsWith(".graphql")).sorted();
    }

    @ParameterizedTest
    @MethodSource("documents")
    void onlyQueriesThatMatchTheSchema(Path file) throws IOException {
        Document document = Parser.parse(Files.readString(file));
        List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);
        assertEquals(1, operations.size(), file + ": exactly one operation");
        assertEquals(OperationDefinition.Operation.QUERY, operations.get(0).getOperation(), file.toString());
        List<ValidationError> errors = new Validator().validateDocument(schema, document, Locale.ENGLISH);
        assertTrue(errors.isEmpty(), file + ": " + errors);
    }

    @ParameterizedTest
    @MethodSource("documents")
    void everyDocumentIsListedInTheQueryEnum(Path file) {
        assertTrue(Stream.of(Query.values()).anyMatch(q -> q.resource().equals("/graphql/" + file.getFileName())),
                file + " has no Query constant");
    }
}
