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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.openhab.core.voice.text.ItemAccessResolver.EXPOSE_PROPERTY;
import static org.openhab.core.voice.text.ItemAccessResolver.SYSTEM_DEFAULT_SOURCE;
import static org.openhab.core.voice.text.ItemAccessResolver.VOICE_SYSTEM_NAMESPACE;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataKey;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.library.items.SwitchItem;

/**
 * Tests for {@link ItemAccessResolver}.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
@ExtendWith(MockitoExtension.class)
public class ItemAccessResolverTest {
    private @Mock @NonNullByDefault({}) ItemRegistry itemRegistry;
    private @Mock @NonNullByDefault({}) MetadataRegistry metadataRegistry;

    private @NonNullByDefault({}) ItemAccessResolver itemAccessResolver;
    private SwitchItem item = new SwitchItem("TestItem");

    @BeforeEach
    public void setUp() {
        itemAccessResolver = new ItemAccessResolver(itemRegistry, metadataRegistry);
        item = new SwitchItem("TestItem");
    }

    @AfterEach
    public void tearDown() {
        itemAccessResolver.dispose();
        itemAccessResolver = null;
    }

    @Test
    public void testCacheWorks() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        assertTrue(itemAccessResolver.isAccessible(item));

        // Metadata registry should only be queried once
        verify(metadataRegistry, times(1)).get(any(MetadataKey.class));
    }

    @Test
    public void testCacheInvalidatedOnItemAdded() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        verify(itemRegistry).addRegistryChangeListener(argThat(l -> {
            l.added(item);
            return true;
        }));
        assertTrue(itemAccessResolver.isAccessible(item));

        verify(metadataRegistry, times(2)).get(any(MetadataKey.class));
    }

    @Test
    public void testCacheInvalidatedOnItemRemoved() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        verify(itemRegistry).addRegistryChangeListener(argThat(l -> {
            l.removed(item);
            return true;
        }));
        assertTrue(itemAccessResolver.isAccessible(item));

        verify(metadataRegistry, times(2)).get(any(MetadataKey.class));
    }

    @Test
    public void testCacheInvalidatedOnItemUpdated() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        verify(itemRegistry).addRegistryChangeListener(argThat(l -> {
            l.updated(item, item);
            return true;
        }));
        assertTrue(itemAccessResolver.isAccessible(item));

        verify(metadataRegistry, times(2)).get(any(MetadataKey.class));
    }

    @Test
    public void testCacheInvalidatedOnVoiceSystemMetadataAdded() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        verify(metadataRegistry).addRegistryChangeListener(argThat(l -> {
            l.added(new Metadata(new MetadataKey(VOICE_SYSTEM_NAMESPACE, item.getName()), "", Map.of()));
            return true;
        }));
        assertTrue(itemAccessResolver.isAccessible(item));

        verify(metadataRegistry, times(2)).get(any(MetadataKey.class));
    }

    @Test
    public void testCacheInvalidatedOnVoiceSystemMetadataRemoved() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        verify(metadataRegistry).addRegistryChangeListener(argThat(l -> {
            l.removed(new Metadata(new MetadataKey(VOICE_SYSTEM_NAMESPACE, item.getName()), "", Map.of()));
            return true;
        }));
        assertTrue(itemAccessResolver.isAccessible(item));

        verify(metadataRegistry, times(2)).get(any(MetadataKey.class));
    }

    @Test
    public void testCacheInvalidatedOnVoiceSystemMetadataUpdated() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        verify(metadataRegistry).addRegistryChangeListener(argThat(l -> {
            Metadata metadata = new Metadata(new MetadataKey(VOICE_SYSTEM_NAMESPACE, item.getName()), "", Map.of());
            l.updated(metadata, metadata);
            return true;
        }));
        assertTrue(itemAccessResolver.isAccessible(item));

        verify(metadataRegistry, times(2)).get(any(MetadataKey.class));
    }

    @Test
    public void testCacheNotInvalidatedOnOtherMetadataNamespace() {
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        verify(metadataRegistry).addRegistryChangeListener(argThat(l -> {
            l.added(new Metadata(new MetadataKey("otherNamespace", item.getName()), "", Map.of()));
            return true;
        }));
        assertTrue(itemAccessResolver.isAccessible(item));

        // Metadata registry should still only be queried once
        verify(metadataRegistry, times(1)).get(any(MetadataKey.class));
    }

    @Test
    public void testExplicitAllowOnItem() {
        itemAccessResolver.setImplicitAccessEnabled(false);
        stubMetadata(item.getName(), true);

        assertTrue(itemAccessResolver.isAccessible(item));
        assertEquals(item.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testExplicitDenyOnItem() {
        itemAccessResolver.setImplicitAccessEnabled(true);
        stubMetadata(item.getName(), false);

        assertFalse(itemAccessResolver.isAccessible(item));
        assertEquals(item.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testInheritAllowFromParentGroup() {
        itemAccessResolver.setImplicitAccessEnabled(false);
        item.addGroupName("ParentGroup");
        GroupItem parentGroup = new GroupItem("ParentGroup");
        when(itemRegistry.get("ParentGroup")).thenReturn(parentGroup);
        stubMetadata("ParentGroup", true);

        assertTrue(itemAccessResolver.isAccessible(parentGroup));
        assertTrue(itemAccessResolver.isAccessible(item));
        assertEquals(parentGroup.getName(), itemAccessResolver.getItemAccess(parentGroup).source());
        assertEquals(parentGroup.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testInheritDenyFromParentGroup() {
        itemAccessResolver.setImplicitAccessEnabled(true);
        item.addGroupName("ParentGroup");
        GroupItem parentGroup = new GroupItem("ParentGroup");
        when(itemRegistry.get("ParentGroup")).thenReturn(parentGroup);
        stubMetadata("ParentGroup", false);

        assertFalse(itemAccessResolver.isAccessible(parentGroup));
        assertFalse(itemAccessResolver.isAccessible(item));
        assertEquals(parentGroup.getName(), itemAccessResolver.getItemAccess(parentGroup).source());
        assertEquals(parentGroup.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testMergingDenyHasPriorityOverAllow() {
        itemAccessResolver.setImplicitAccessEnabled(false);

        item.addGroupName("DenyGroup");
        item.addGroupName("AllowGroup");

        GroupItem denyGroup = new GroupItem("DenyGroup");
        GroupItem allowGroup = new GroupItem("AllowGroup");

        lenient().when(itemRegistry.get("DenyGroup")).thenReturn(denyGroup);
        lenient().when(itemRegistry.get("AllowGroup")).thenReturn(allowGroup);

        stubMetadata("DenyGroup", false);
        stubMetadata("AllowGroup", true);

        // Even though one group allows, the other denies, and no-access has priority.
        assertFalse(itemAccessResolver.isAccessible(item));
        assertEquals(denyGroup.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testMultiLevelInheritance() {
        itemAccessResolver.setImplicitAccessEnabled(false);

        item.addGroupName("ParentGroup");
        GroupItem parentGroup = new GroupItem("ParentGroup");
        parentGroup.addGroupName("GrandparentGroup");
        GroupItem grandparentGroup = new GroupItem("GrandparentGroup");

        lenient().when(itemRegistry.get("ParentGroup")).thenReturn(parentGroup);
        lenient().when(itemRegistry.get("GrandparentGroup")).thenReturn(grandparentGroup);

        // Parent has no metadata, grandparent allows
        stubMetadata("GrandparentGroup", true);

        assertTrue(itemAccessResolver.isAccessible(item));
        assertEquals(grandparentGroup.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testFallbackToSystemDefaultTrue() {
        itemAccessResolver.setImplicitAccessEnabled(true);

        assertTrue(itemAccessResolver.isAccessible(item));
        assertEquals(SYSTEM_DEFAULT_SOURCE, itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testFallbackToSystemDefaultFalse() {
        itemAccessResolver.setImplicitAccessEnabled(false);

        assertFalse(itemAccessResolver.isAccessible(item));
        assertEquals(SYSTEM_DEFAULT_SOURCE, itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testCircularGroupMembership() {
        item.addGroupName("GroupA");
        GroupItem groupA = new GroupItem("GroupA");
        groupA.addGroupName("GroupB");
        GroupItem groupB = new GroupItem("GroupB");
        groupB.addGroupName("GroupA"); // Circular

        lenient().when(itemRegistry.get("GroupA")).thenReturn(groupA);
        lenient().when(itemRegistry.get("GroupB")).thenReturn(groupB);

        // No explicit allow/deny, should fallback to default
        itemAccessResolver.setImplicitAccessEnabled(true);
        assertTrue(itemAccessResolver.isAccessible(item));
        assertEquals(SYSTEM_DEFAULT_SOURCE, itemAccessResolver.getItemAccess(item).source());
        itemAccessResolver.setImplicitAccessEnabled(false);
        assertFalse(itemAccessResolver.isAccessible(item));
        assertEquals(SYSTEM_DEFAULT_SOURCE, itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testGrandparentAllowParentDeny() {
        itemAccessResolver.setImplicitAccessEnabled(true);

        item.addGroupName("ParentGroup");
        GroupItem parentGroup = new GroupItem("ParentGroup");
        parentGroup.addGroupName("GrandparentGroup");
        GroupItem grandparentGroup = new GroupItem("GrandparentGroup");

        lenient().when(itemRegistry.get("ParentGroup")).thenReturn(parentGroup);
        lenient().when(itemRegistry.get("GrandparentGroup")).thenReturn(grandparentGroup);

        stubMetadata("ParentGroup", false);
        stubMetadata("GrandparentGroup", true);

        // Parent denies, which should have priority over grandparent allowing.
        assertFalse(itemAccessResolver.isAccessible(item));
        assertEquals(parentGroup.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    @Test
    public void testGrandparentDenyParentAllow() {
        itemAccessResolver.setImplicitAccessEnabled(true);

        item.addGroupName("ParentGroup");
        GroupItem parentGroup = new GroupItem("ParentGroup");
        parentGroup.addGroupName("GrandparentGroup");
        GroupItem grandparentGroup = new GroupItem("GrandparentGroup");

        lenient().when(itemRegistry.get("ParentGroup")).thenReturn(parentGroup);
        lenient().when(itemRegistry.get("GrandparentGroup")).thenReturn(grandparentGroup);

        stubMetadata("ParentGroup", true);
        stubMetadata("GrandparentGroup", false);

        // Parent allows, which should have priority over grandparent denying.
        assertTrue(itemAccessResolver.isAccessible(item));
        assertEquals(parentGroup.getName(), itemAccessResolver.getItemAccess(item).source());
    }

    private void stubMetadata(String itemName, boolean expose) {
        MetadataKey key = new MetadataKey(VOICE_SYSTEM_NAMESPACE, itemName);
        Map<String, Object> config = new HashMap<>();
        config.put(EXPOSE_PROPERTY, expose);
        Metadata metadata = new Metadata(key, "", config);
        lenient().when(metadataRegistry.get(key)).thenReturn(metadata);
    }
}
