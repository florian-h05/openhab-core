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
package org.openhab.core.voice.internal.text;

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
import org.openhab.core.voice.text.ItemAccessResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Implementation of {@link ItemAccessResolver}.
 *
 * @author Florian Hotze - Initial contribution
 */
@Component(service = ItemAccessResolver.class, immediate = true)
@NonNullByDefault
public class ItemAccessResolverImpl implements ItemAccessResolver {
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
    public ItemAccessResolverImpl(final @Reference ItemRegistry itemRegistry,
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

    @Override
    public void setImplicitAccessEnabled(boolean implicitAccessEnabled) {
        this.implicitAccessEnabled = implicitAccessEnabled;
        invalidate();
    }

    @Override
    public boolean isAccessible(Item item) {
        return accessCache.computeIfAbsent(item.getUID(), (k) -> getItemAccess(item).access());
    }

    @Override
    public ItemAccess getItemAccess(Item item) {
        Boolean expose = getExposeMetadata(item);
        if (expose != null) {
            return new ItemAccess(expose, item.getName());
        }

        Set<String> visitedGroups = new HashSet<>();
        if (item instanceof GroupItem) {
            visitedGroups.add(item.getUID());
        }
        ItemAccess inherited = resolveInheritedAccess(item, visitedGroups);
        return inherited != null ? inherited : new ItemAccess(implicitAccessEnabled, SYSTEM_DEFAULT_SOURCE);
    }

    private ItemAccessResolver.@Nullable ItemAccess resolveInheritedAccess(Item item, Set<String> visitedGroups) {
        Set<String> currentLayer = new HashSet<>();
        for (String groupName : item.getGroupNames()) {
            if (visitedGroups.add(groupName)) {
                currentLayer.add(groupName);
            }
        }

        while (!currentLayer.isEmpty()) {
            ItemAccess layerAllowed = null;
            Set<String> nextLayer = new HashSet<>();

            for (String groupName : currentLayer) {
                Item group = itemRegistry.get(groupName);
                if (group == null) {
                    continue;
                }
                Boolean expose = getExposeMetadata(group);
                if (expose != null) {
                    if (!expose) {
                        return new ItemAccess(false, groupName); // Deny has priority in same layer
                    }
                    layerAllowed = new ItemAccess(true, groupName);
                }
                for (String parentGroupName : group.getGroupNames()) {
                    if (visitedGroups.add(parentGroupName)) {
                        nextLayer.add(parentGroupName);
                    }
                }
            }

            if (layerAllowed != null) {
                return layerAllowed;
            }
            currentLayer = nextLayer;
        }

        return null;
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
