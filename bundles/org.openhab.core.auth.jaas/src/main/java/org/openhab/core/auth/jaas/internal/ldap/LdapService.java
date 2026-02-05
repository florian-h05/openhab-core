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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.karaf.jaas.config.JaasRealm;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.UserProvider;
import org.openhab.core.auth.jaas.internal.console.LdapConsoleCommandExtension;
import org.openhab.core.io.console.extensions.ConsoleCommandExtension;
import org.openhab.core.storage.StorageService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A component managing the LDAP realm.
 * It dynamically registers both LDAP {@link JaasRealm} and {@link LdapUserProvider} for LDAP authentication.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
@Component(service = LdapService.class, configurationPid = "org.openhab.jaas.ldap")
public class LdapService {
    public static final String LDAP_REALM = "ldap";

    private final Logger logger = LoggerFactory.getLogger(LdapService.class);
    private final StorageService storageService;
    private final BundleContext bundleContext;

    private @Nullable LdapLoginConfiguration configuration;
    private final List<ServiceRegistration<?>> registrations = new ArrayList<>();
    private @Nullable LdapUserProvider ldapUserProvider;

    @Activate
    public LdapService(final @Reference StorageService storageService, ComponentContext componentContext,
            Map<String, Object> properties) {
        this.storageService = storageService;
        this.bundleContext = componentContext.getBundleContext();
        modified(properties);
    }

    public @Nullable LdapLoginConfiguration getConfiguration() {
        return configuration;
    }

    public void enable() {
        if (!registrations.isEmpty()) {
            return;
        }
        LdapLoginConfiguration configuration = this.configuration;
        if (configuration == null) {
            throw new IllegalStateException("LDAP authentication configuration is missing");
        }

        logger.debug("Registering LDAP OSGi services");
        registrations.add(bundleContext.registerService(JaasRealm.class, new LdapUserRealm(), null));

        LdapUserProvider ldapUserProvider = this.ldapUserProvider;
        if (ldapUserProvider == null) {
            ldapUserProvider = this.ldapUserProvider = new LdapUserProvider(storageService, configuration);
        }
        registrations.add(bundleContext.registerService(
                new String[] { LdapUserProvider.class.getCanonicalName(), UserProvider.class.getCanonicalName() },
                ldapUserProvider, null));

        registrations.add(bundleContext.registerService(ConsoleCommandExtension.class,
                new LdapConsoleCommandExtension(ldapUserProvider), null));
    }

    public void disable() {
        if (registrations.isEmpty()) {
            return;
        }
        logger.debug("Unregistering LDAP OSGi services");
        for (ServiceRegistration<?> registration : registrations) {
            registration.unregister();
        }
        registrations.clear();

        if (ldapUserProvider != null) {
            ldapUserProvider.dispose();
            ldapUserProvider = null;
        }
    }

    @Modified
    protected void modified(Map<String, Object> properties) {
        if (properties.isEmpty()) {
            return;
        }
        logger.debug("LDAP configuration changed: {}", properties);

        LdapLoginConfiguration configuration = this.configuration = new LdapLoginConfiguration(properties);
        LdapUserProvider ldapUserProvider = this.ldapUserProvider;
        if (ldapUserProvider != null) {
            ldapUserProvider.modified(configuration);
        }
    }

    @Deactivate
    protected void deactivate() {
        disable();
    }
}
