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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.items.Item;

/**
 * Defines an utility service to resolve if an Item is accessible for Human Language Interpreters.
 * <p>
 * The access rules are as follows:
 * <ul>
 * <li>Explicit configuration: An Item can explicitly allow or deny access via the {@code expose} property in the
 * {@code voiceSystem} metadata namespace.</li>
 * <li>Inheritance: If no explicit configuration is found on the Item itself, it inherits permissions from its parent
 * groups.</li>
 * <li>Merging and Priority: Parent permissions are merged. Within the same layer, no-access ({@code expose=false}) has
 * priority over access ({@code expose=true}). The closest parent layer has priority over further away layers. If the
 * closest layer(s) with explicit configuration contain a deny, the Item is not accessible. If they only contain
 * allows, the Item is accessible.</li>
 * <li>System Default: If no explicit configuration is found on the Item or any of its ancestors, the system-wide
 * default for implicit Item access is used.</li>
 * </ul>
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public interface ItemAccessResolver {
    String VOICE_SYSTEM_NAMESPACE = "voiceSystem";
    String EXPOSE_PROPERTY = "expose";
    String SYSTEM_DEFAULT_SOURCE = "system:default";

    /**
     * Returns whether an item is accessible for {@link HumanLanguageInterpreter}s.
     *
     * @param item the item to check
     * @return true if the item is accessible, false otherwise
     */
    boolean isAccessible(Item item);

    /**
     * Gets the {@link ItemAccess} for an item.
     *
     * @param item the item to check
     * @return whether the item is accessible and what source defined the access state
     */
    ItemAccess getItemAccess(Item item);

    /**
     * Sets whether implicit item access is enabled through system settings.
     * 
     * @param implicitAccessEnabled whether all items are implicitly exposed to {@link HumanLanguageInterpreter}s
     */
    void setImplicitAccessEnabled(boolean implicitAccessEnabled);

    record ItemAccess(boolean access, @Nullable String source) {
    }
}
