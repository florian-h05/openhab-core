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
package org.openhab.core.auth.jaas.internal.ldap;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import org.apache.karaf.jaas.modules.ldap.LDAPOptions;

/**
 * Describes a JAAS configuration for LDAP with the Karaf LDAP Login Module as a sufficient module.
 *
 * @author Florian Hotze - Initial contribution
 */
public class LdapLoginConfiguration extends Configuration {
    private final Map<String, Object> options = new HashMap<>();

    public LdapLoginConfiguration(Map<String, Object> properties) {
        options.putAll(properties);
        Object connectionUrl = options.get(LDAPOptions.CONNECTION_URL);
        if (connectionUrl != null) {
            options.putIfAbsent("java.naming.provider.url", connectionUrl);
        }
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        return new AppConfigurationEntry[] {
                new AppConfigurationEntry(LdapUserRealm.MODULE_CLASS, LdapUserRealm.FLAG, options) };
    }
}
