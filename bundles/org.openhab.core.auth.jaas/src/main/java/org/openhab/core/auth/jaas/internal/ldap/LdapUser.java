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
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.AuthenticatedUser;
import org.openhab.core.auth.GenericUser;
import org.openhab.core.auth.PendingToken;
import org.openhab.core.auth.UserApiToken;
import org.openhab.core.auth.UserSession;

/**
 * A {@link org.openhab.core.auth.User} sources from an LDAP server.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class LdapUser extends GenericUser implements AuthenticatedUser {
    private @Nullable PendingToken pendingToken = null;
    private List<UserSession> sessions = new ArrayList<>();
    private List<UserApiToken> apiTokens = new ArrayList<>();

    public LdapUser(String name, Set<String> roles) {
        super(name, roles);
    }

    public LdapUser(LdapUser other) {
        super(other.getName(), other.getRoles());
        pendingToken = other.pendingToken;
        sessions = other.sessions;
        apiTokens = other.apiTokens;
    }

    void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    @Override
    public @Nullable PendingToken getPendingToken() {
        return pendingToken;
    }

    @Override
    public void setPendingToken(@Nullable PendingToken pendingToken) {
        this.pendingToken = pendingToken;
    }

    @Override
    public List<UserSession> getSessions() {
        return sessions;
    }

    @Override
    public void setSessions(List<UserSession> sessions) {
        this.sessions = sessions;
    }

    @Override
    public List<UserApiToken> getApiTokens() {
        return apiTokens;
    }

    @Override
    public void setApiTokens(List<UserApiToken> apiTokens) {
        this.apiTokens = apiTokens;
    }
}
