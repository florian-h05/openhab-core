/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.auth.jaas.internal.console;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.auth.jaas.internal.ldap.LdapUserProvider;
import org.openhab.core.io.console.Console;
import org.openhab.core.io.console.extensions.AbstractConsoleCommandExtension;

/**
 * Console command extension to manage LDAP authentication.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class LdapConsoleCommandExtension extends AbstractConsoleCommandExtension {
    private static final String SUBCMD_CLEANUP = "cleanup";
    private static final String SUBCMD_REFRESH = "refresh";

    private final LdapUserProvider ldapUserProvider;

    public LdapConsoleCommandExtension(LdapUserProvider ldapUserProvider) {
        super("ldap", "Manage LDAP authentication.");
        this.ldapUserProvider = ldapUserProvider;
    }

    @Override
    public List<String> getUsages() {
        return List.of(
                buildCommandUsage(SUBCMD_CLEANUP,
                        "clean up LDAP user data (sessions and API tokens) for non-existing users"),
                buildCommandUsage(SUBCMD_REFRESH, "refresh users from the LDAP server"));
    }

    @Override
    public void execute(String[] args, Console console) {
        if (args.length > 0) {
            String subCommand = args[0];
            switch (subCommand) {
                case SUBCMD_CLEANUP:
                    int count = ldapUserProvider.cleanup();
                    console.println("Removed " + count + " user data entries.");
                    break;
                case SUBCMD_REFRESH:
                    ldapUserProvider.refresh();
                    console.println("LDAP users refreshed.");
                    break;
                default:
                    console.println("Unknown command '" + subCommand + "'");
                    printUsage(console);
                    break;
            }
        } else {
            printUsage(console);
        }
    }
}
