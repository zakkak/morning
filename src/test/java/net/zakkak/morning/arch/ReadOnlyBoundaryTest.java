package net.zakkak.morning.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Constitution Principles II and III, enforced on the main code. */
@AnalyzeClasses(packages = "net.zakkak.morning", importOptions = ImportOption.DoNotIncludeTests.class)
class ReadOnlyBoundaryTest {

    @ArchTest
    static final ArchRule graphqlClientIsConfined = noClasses()
            .that().doNotHaveFullyQualifiedName("net.zakkak.morning.github.GraphQLQueries")
            .and().doNotHaveFullyQualifiedName("net.zakkak.morning.github.GraphQLClientProducer")
            .should().dependOnClassesThat().resideInAPackage("io.smallrye.graphql.client..")
            .because("only GraphQLQueries runs GraphQL, and only checked-in queries (Principle III)");

    @ArchTest
    static final ArchRule tokenIsReadOnlyByTheClientProducer = noClasses()
            .that().doNotHaveFullyQualifiedName("net.zakkak.morning.github.GraphQLClientProducer")
            .and().doNotHaveFullyQualifiedName("net.zakkak.morning.github.GitHubCredentials")
            .should().callMethod("net.zakkak.morning.github.GitHubCredentials", "token")
            .because("each secret is injected only into the client that needs it");

    @ArchTest
    static final ArchRule hub4jIsConfined = noClasses()
            .that().resideOutsideOfPackage("net.zakkak.morning.github.actions..")
            .should().dependOnClassesThat().resideInAPackage("org.kohsuke.github..")
            .allowEmptyShould(true)
            .because("hub4j is only for what GraphQL does not expose (ADR-0004)");

    @ArchTest
    static final ArchRule noHttpClientsOutsideGithub = noClasses()
            .that().resideOutsideOfPackage("net.zakkak.morning.github..")
            .should().dependOnClassesThat().resideInAnyPackage("java.net.http..", "io.vertx..")
            .because("all external access goes through the read-only GitHub package");

    @ArchTest
    static final ArchRule githubPackageDoesNotDependOnFeatures = classes()
            .that().resideInAPackage("net.zakkak.morning.github..")
            .should().onlyDependOnClassesThat().resideOutsideOfPackages("net.zakkak.morning.prs..",
                    "net.zakkak.morning.cli..", "net.zakkak.morning.render..");
}
