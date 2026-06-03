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
package org.openhab.core.voice.text;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.common.registry.RegistryChangeListener;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataKey;
import org.openhab.core.items.MetadataRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Utility class to resolve if an Item is accessible for Human Language Interpreters.
 * <p>
 * The access rules are as follows:
 * <ul>
 * <li>Explicit configuration: An Item can explicitly allow or deny access via the {@code expose} property in the
 * {@code voiceSystem} metadata namespace.</li>
 * <li>Inheritance: If no explicit configuration is found on the Item itself, it inherits permissions from its parent
 * groups.</li>
 * <li>Merging and Priority: Parent permissions are merged. Access ({@code expose=true}) has priority over no-access
 * ({@code expose=false}). If any ancestor group explicitly allows access, the Item is accessible. If no ancestor
 * allows access but at least one explicitly denies it, the Item is not accessible.</li>
 * <li>System Default: If no explicit configuration is found on the Item or any of its ancestors, the system-wide
 * default for implicit Item access is used.</li>
 * </ul>
 *
 * @author Florian Hotze - Initial contribution
 */
@Component(immediate = true)
@NonNullByDefault
public class ItemAccessResolver {
    public static final String VOICE_SYSTEM_NAMESPACE = "voiceSystem";
    public static final String EXPOSE_PROPERTY = "expose";

    private final ItemRegistry itemRegistry;
    private final MetadataRegistry metadataRegistry;
    private final ConcurrentHashMap<String, Boolean> accessCache = new ConcurrentHashMap<>();

    private boolean implicitAccessEnabled = true;

    private final RegistryChangeListener<Item> itemRegistryChangeListener = new RegistryChangeListener<>() {
        @Override
        public void added(Item element) {
            invalidate();
        }

        @Override
        public void removed(Item element) {
            invalidate();
        }

        @Override
        public void updated(Item oldElement, Item element) {
            invalidate();
        }
    };

    private final RegistryChangeListener<Metadata> voiceSystemMetadataChangeListener = new RegistryChangeListener<>() {
        @Override
        public void added(Metadata element) {
            invalidateIfVoiceSystemMetadata(element);
        }

        @Override
        public void removed(Metadata element) {
            invalidateIfVoiceSystemMetadata(element);
        }

        @Override
        public void updated(Metadata oldElement, Metadata element) {
            invalidateIfVoiceSystemMetadata(element);
        }

        private void invalidateIfVoiceSystemMetadata(Metadata metadata) {
            if (metadata.getUID().getNamespace().equals(VOICE_SYSTEM_NAMESPACE)) {
                invalidate();
            }
        }
    };

    @Activate
    public ItemAccessResolver(final @Reference ItemRegistry itemRegistry,
            final @Reference MetadataRegistry metadataRegistry) {
        this.itemRegistry = itemRegistry;
        this.metadataRegistry = metadataRegistry;
        this.itemRegistry.addRegistryChangeListener(itemRegistryChangeListener);
        this.metadataRegistry.addRegistryChangeListener(voiceSystemMetadataChangeListener);
    }

    @Deactivate
    public void dispose() {
        itemRegistry.removeRegistryChangeListener(itemRegistryChangeListener);
        metadataRegistry.removeRegistryChangeListener(voiceSystemMetadataChangeListener);
    }

    private void invalidate() {
        accessCache.clear();
    }

    public void setImplicitAccessEnabled(boolean implicitAccessEnabled) {
        this.implicitAccessEnabled = implicitAccessEnabled;
        invalidate();
    }

    /**
     * Returns whether an item is accessible for {@link HumanLanguageInterpreter}s.
     *
     * @param item the item to check
     * @return true if the item is accessible, false otherwise
     */
    public boolean isAccessible(Item item) {
        return accessCache.computeIfAbsent(item.getName(), (k) -> computeIsAccessible(item));
    }

    private boolean computeIsAccessible(Item item) {
        Boolean expose = getExposeMetadata(item);
        if (expose != null) {
            return expose;
        }

        Set<String> visitedGroups = new HashSet<>();
        if (item instanceof GroupItem) {
            visitedGroups.add(item.getName());
        }
        Boolean inherited = resolveInheritedAccess(item, visitedGroups);
        return inherited != null ? inherited : implicitAccessEnabled;
    }

    private @Nullable Boolean resolveInheritedAccess(Item item, Set<String> visitedGroups) {
        boolean anyDenied = false;
        for (String groupName : item.getGroupNames()) {
            if (visitedGroups.add(groupName)) {
                Item group = itemRegistry.get(groupName);
                if (group != null) {
                    Boolean expose = getExposeMetadata(group);
                    if (expose != null) {
                        if (expose) {
                            return true; // Access has priority over no-access
                        }
                        anyDenied = true;
                    }
                    Boolean inherited = resolveInheritedAccess(group, visitedGroups);
                    if (inherited != null) {
                        if (inherited) {
                            return true;
                        }
                        anyDenied = true;
                    }
                }
            }
        }
        return anyDenied ? false : null;
    }

    private @Nullable Boolean getExposeMetadata(Item item) {
        Metadata metadata = metadataRegistry.get(new MetadataKey(VOICE_SYSTEM_NAMESPACE, item.getName()));
        if (metadata != null) {
            Object exposeValue = metadata.getConfiguration().get(EXPOSE_PROPERTY);
            if (exposeValue instanceof Boolean b) {
                return b;
            } else if (exposeValue instanceof String s) {
                return Boolean.parseBoolean(s);
            }
        }
        return null;
    }
}
