package net.zakkak.morning.cli;

import org.eclipse.microprofile.config.ConfigProvider;

import picocli.CommandLine.IVersionProvider;

/** Reports the application version from Quarkus's build-time {@code quarkus.application.version}. */
public class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[] { "morning " + ConfigProvider.getConfig()
                .getOptionalValue("quarkus.application.version", String.class).orElse("unknown") };
    }
}
