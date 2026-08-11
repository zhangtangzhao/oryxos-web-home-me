package com.oryxos.cli;

import picocli.CommandLine.IVersionProvider;

/**
 * Provides OryxOS version information for the Picocli --version flag.
 */
public class OryxOsVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {
            "OryxOS v1.0.0-SNAPSHOT",
            "Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
            "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")"
        };
    }
}
