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

import static org.openhab.core.common.ThreadPoolManager.THREAD_POOL_NAME_COMMON;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.karaf.jaas.modules.ldap.LDAPCache;
import org.apache.karaf.jaas.modules.ldap.LDAPOptions;
import org.apache.karaf.jaas.modules.ldap.ManagedSSLSocketFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.User;
import org.openhab.core.auth.UserProvider;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.common.registry.AbstractProvider;
import org.openhab.core.common.registry.ManagedProvider;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link UserProvider} which queries an LDAP server for {@link LdapUser}s.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class LdapUserProvider extends AbstractProvider<User> implements UserProvider, ManagedProvider<User, String> {
    private static final String STORAGE_NAME = "users_ldap";

    private final Logger logger = LoggerFactory.getLogger(LdapUserProvider.class);
    private final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool(THREAD_POOL_NAME_COMMON);
    private final Storage<LdapUserAuxiliaryData> storage;

    private volatile @Nullable LdapContext context;
    private @Nullable ScheduledFuture<?> refreshJob;

    private final Lock userCacheLock = new ReentrantLock();
    private final Map<String, LdapUser> userCache = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    protected LdapUserProvider(StorageService storageService, LdapLoginConfiguration config) {
        ClassLoader classLoader = getClass().getClassLoader();
        this.storage = storageService.getStorage(STORAGE_NAME, classLoader);
        modified(config);
        createRefreshTask();
    }

    protected void modified(LdapLoginConfiguration config) {
        String userUidAttribute = (String) config.getOptions().getOrDefault("user.id.attribute", "uid");
        LDAPOptions options = new LDAPOptions(config.getOptions());
        LDAPCache cache = LDAPCache.getCache(options);
        synchronized (this) {
            this.context = new LdapContext(options, cache, userUidAttribute);
        }
    }

    private void createRefreshTask() {
        cancelRefreshTask();
        this.refreshJob = scheduler.scheduleWithFixedDelay(this::refresh, 0, 5, TimeUnit.MINUTES);
    }

    private void cancelRefreshTask() {
        ScheduledFuture<?> refreshJob = this.refreshJob;
        if (refreshJob != null) {
            refreshJob.cancel(false);
        }
        this.refreshJob = null;
    }

    public void dispose() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
    }

    @Override
    public void add(User element) {
        throw new UnsupportedOperationException("Adding users to LDAP is not supported.");
    }

    @Override
    public @Nullable User remove(String key) {
        throw new UnsupportedOperationException("Removing users from LDAP is not supported.");
    }

    private LdapUserAuxiliaryData getAuxiliaryData(LdapUser element) {
        LdapUserAuxiliaryData data = new LdapUserAuxiliaryData();
        data.sessions = new ArrayList<>(element.getSessions());
        data.apiTokens = new ArrayList<>(element.getApiTokens());
        return data;
    }

    private String getKeyAsString(User user) {
        return user.getName();
    }

    @Override
    public @Nullable User update(User element) {
        String key = getKeyAsString(element);
        LdapUser user = (LdapUser) element;

        // Persist sessions & API tokens to storage
        storage.put(key, getAuxiliaryData(user));

        // Store to cache
        LdapUser oldUser;
        userCacheLock.lock();
        try {
            oldUser = userCache.put(key, user);
        } finally {
            userCacheLock.unlock();
        }

        if (oldUser != null) {
            notifyListenersAboutUpdatedElement(oldUser, user);
        }
        return oldUser;
    }

    @Override
    public @Nullable User get(String key) {
        return userCache.get(key);
    }

    @Override
    public Collection<User> getAll() {
        return Collections.unmodifiableCollection(userCache.values());
    }

    /**
     * Clean up auxiliary user data from the storage for non-existing users.
     *
     * @return the number of removed entries
     */
    public int cleanup() {
        logger.debug("Cleaning up LDAP user data");
        if (!initialized) {
            logger.debug("LDAP user data cleanup skipped, LDAP user cache not initialized yet.");
            return 0;
        }

        int counter = 0;
        userCacheLock.lock();
        try {
            for (String key : storage.getKeys()) {
                if (get(key) == null) {
                    storage.remove(key);
                    counter++;
                    logger.debug("Removed LDAP user data for non-existing user '{}'.", key);
                }
            }
        } finally {
            userCacheLock.unlock();
        }
        return counter;
    }

    /**
     * Refresh user data from the LDAP server.
     */
    public void refresh() {
        logger.debug("Refreshing LDAP user data");
        try {
            loadUsers();
        } catch (LdapServiceException e) {
            logger.error("Failed to load LDAP user data", e);
        }
    }

    private void loadUsers() throws LdapServiceException {
        logger.debug("Loading LDAP users");
        LdapContext context;
        synchronized (this) {
            context = this.context;
        }

        if (context == null) {
            throw new LdapServiceException("LDAP connection context unavailable");
        }

        LDAPOptions options = context.options;
        LDAPCache cache = context.cache;
        String userUidAttribute = context.userUidAttribute;

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        DirContext ctx;
        NamingEnumeration<SearchResult> results = null;

        // step 1: open the connection
        try {
            ctx = cache.open();
        } catch (NamingException e) {
            throw new LdapServiceException("Failed to connect to LDAP server", e);
        }
        logger.debug("Connected to LDAP server");

        List<Runnable> notifyListeners = new ArrayList<>();
        Set<String> names = new HashSet<>();

        try {
            // step 2: setup search controls and filter
            SearchControls controls = new SearchControls();
            controls.setSearchScope(
                    options.getUserSearchSubtree() ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);
            String filter = options.getUserFilter().replace("%u", "*");

            // step 3: query all users
            results = ctx.search(options.getUserBaseDn(), filter, controls);
            logger.debug("Queried LDAP users");

            userCacheLock.lock();
            try {
                // step 4: update cache from results
                while (results != null && results.hasMore()) {
                    SearchResult result = results.next();
                    Attribute uidAttr = result.getAttributes().get(userUidAttribute);

                    if (uidAttr != null) {
                        String name = (String) uidAttr.get();
                        Set<String> roles = fetchRoles(name, result, cache, options);
                        names.add(name);

                        userCache.compute(name, (k, existingUser) -> {
                            if (existingUser == null) {
                                // user not in cache: new user or not cached yet -> no pending token in memory
                                logger.trace("Found new LDAP user '{}'", name);
                                LdapUser newUser = new LdapUser(name, roles);
                                LdapUserAuxiliaryData auxiliaryData = storage.get(getKeyAsString(newUser));
                                if (auxiliaryData != null) {
                                    if (auxiliaryData.sessions != null) {
                                        newUser.setSessions(auxiliaryData.sessions);
                                    }
                                    if (auxiliaryData.apiTokens != null) {
                                        newUser.setApiTokens(auxiliaryData.apiTokens);
                                    }
                                }
                                notifyListeners.add(() -> notifyListenersAboutAddedElement(newUser));
                                return newUser;
                            } else if (!existingUser.getRoles().equals(roles)) {
                                // user in cache, but roles have changed -> create copy of user with updated roles
                                logger.trace("LDAP user '{}' has changed roles", name);
                                LdapUser newUser = new LdapUser(existingUser);
                                newUser.setRoles(roles);
                                notifyListeners.add(() -> notifyListenersAboutUpdatedElement(existingUser, newUser));
                                return newUser;
                            }
                            return existingUser;
                        });
                    }
                }

                // step 5: remove stale cache entries
                userCache.keySet().removeIf(existingName -> {
                    if (!names.contains(existingName)) {
                        LdapUser removed = userCache.remove(existingName);
                        if (removed != null) {
                            logger.trace("Removed stale LDAP user '{}'", existingName);
                            notifyListeners.add(() -> notifyListenersAboutRemovedElement(removed));
                        }
                        return true;
                    }
                    return false;
                });
                logger.debug("LDAP user cache updated");

                initialized = true;
            } finally {
                userCacheLock.unlock();
            }
        } catch (NamingException e) {
            throw new LdapServiceException("Failed to query LDAP users", e);
        } finally {
            // step 6: close resources to prevent leaks
            if (results != null) {
                try {
                    results.close();
                } catch (NamingException e) {
                    // ignore
                }
            }
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    // ignore
                }
            }
            ManagedSSLSocketFactory.setSocketFactory(null);
            Thread.currentThread().setContextClassLoader(tccl);
            logger.debug("Closed LDAP connection");
        }

        // step 7: notify listeners
        notifyListeners.forEach(Runnable::run);
        logger.debug("Notified listeners about LDAP user changes");
    }

    private Set<String> fetchRoles(String uid, SearchResult result, LDAPCache cache, LDAPOptions options)
            throws LdapServiceException {
        try {
            // Handle specific LDAP DN formatting quirks
            String userDNNamespace = result.getNameInNamespace();
            // Fallback logic for DN calculation
            String userDN = result.getName();
            if (userDNNamespace != null) {
                int idx = userDNNamespace.toLowerCase().indexOf("," + options.getUserBaseDn().toLowerCase());
                if (idx > 0)
                    userDN = userDNNamespace.substring(0, idx);
            }

            String[] roles = cache.getUserRoles(uid, userDN, userDNNamespace);
            if (roles != null) {
                Set<String> roleSet = new HashSet<>();
                Collections.addAll(roleSet, roles);
                return roleSet;
            }
        } catch (Exception e) {
            throw new LdapServiceException("Failed to get roles for user: " + uid, e);
        }
        return Collections.emptySet();
    }

    private record LdapContext(LDAPOptions options, LDAPCache cache, String userUidAttribute) {
    }
}
