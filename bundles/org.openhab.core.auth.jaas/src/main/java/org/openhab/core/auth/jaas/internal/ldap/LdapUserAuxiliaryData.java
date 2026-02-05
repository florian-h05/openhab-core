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

import org.openhab.core.auth.UserApiToken;
import org.openhab.core.auth.UserSession;

/**
 * Helper class to store auxiliary data for LDAP users.
 *
 * @author Florian Hotze - Initial contribution
 */
public class LdapUserAuxiliaryData {
    public List<UserSession> sessions = new ArrayList<>();
    public List<UserApiToken> apiTokens = new ArrayList<>();
}
