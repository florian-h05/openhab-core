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

import org.apache.karaf.jaas.boot.ProxyLoginModule;
import org.apache.karaf.jaas.config.JaasRealm;

/**
 * A JAAS realm description for the LDAP login module.
 *
 * @author Florian Hotze - Initial contribution
 */
public class LdapUserRealm implements JaasRealm {
    public static final String REALM_NAME = "ldap";
    public static final String MODULE_CLASS = "org.apache.karaf.jaas.modules.ldap.LDAPLoginModule";
    public static final AppConfigurationEntry.LoginModuleControlFlag FLAG = AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT;

    @Override
    public String getName() {
        return REALM_NAME;
    }

    @Override
    public int getRank() {
        return 20;
    }

    @Override
    public AppConfigurationEntry[] getEntries() {
        Map<String, Object> options = new HashMap<>();
        options.put(ProxyLoginModule.PROPERTY_MODULE, MODULE_CLASS);

        return new AppConfigurationEntry[] { new AppConfigurationEntry(MODULE_CLASS, FLAG, options) };
    }
}
