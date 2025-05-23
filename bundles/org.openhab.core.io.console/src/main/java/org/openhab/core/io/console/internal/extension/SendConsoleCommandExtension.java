/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.core.io.console.internal.extension;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.io.console.Console;
import org.openhab.core.io.console.ConsoleCommandCompleter;
import org.openhab.core.io.console.extensions.AbstractConsoleCommandExtension;
import org.openhab.core.io.console.extensions.ConsoleCommandExtension;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemNotUniqueException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.events.ItemEventFactory;
import org.openhab.core.types.Command;
import org.openhab.core.types.TypeParser;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Console command extension to send command to item
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Markus Rathgeb - Create DS for command extension
 * @author Dennis Nobel - Changed service references to be injected via DS
 * @author Stefan Bußweiler - Migration to new ESH event concept
 */
@Component(service = ConsoleCommandExtension.class)
@NonNullByDefault
public class SendConsoleCommandExtension extends AbstractConsoleCommandExtension {

    private final ItemRegistry itemRegistry;
    private final EventPublisher eventPublisher;

    @Activate
    public SendConsoleCommandExtension(final @Reference ItemRegistry itemRegistry,
            final @Reference EventPublisher eventPublisher) {
        super("send", "Send a command to an item.");
        this.itemRegistry = itemRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<String> getUsages() {
        return List.of(buildCommandUsage("<item> <command>", "sends a command for an item"));
    }

    @Override
    public void execute(String[] args, Console console) {
        if (args.length > 0) {
            String itemName = args[0];
            try {
                Item item = itemRegistry.getItemByPattern(itemName);
                if (args.length > 1) {
                    String commandName = args[1];
                    Command command = TypeParser.parseCommand(item.getAcceptedCommandTypes(), commandName);
                    if (command != null) {
                        eventPublisher.post(ItemEventFactory.createCommandEvent(itemName, command));
                        console.println("Command has been sent successfully.");
                    } else {
                        console.println(
                                "Error: Command '" + commandName + "' is not valid for item '" + itemName + "'");
                        console.println("Valid command types are:");
                        for (Class<? extends Command> acceptedType : item.getAcceptedCommandTypes()) {
                            console.print("  " + acceptedType.getSimpleName());
                            if (acceptedType.isEnum()) {
                                console.print(": ");
                                for (Object e : Objects.requireNonNull(acceptedType.getEnumConstants())) {
                                    console.print(e + " ");
                                }
                            }
                            console.println("");
                        }
                    }
                } else {
                    printUsage(console);
                }
            } catch (ItemNotFoundException e) {
                console.println("Error: Item '" + itemName + "' does not exist.");
            } catch (ItemNotUniqueException e) {
                console.print("Error: Multiple items match this pattern: ");
                for (Item item : e.getMatchingItems()) {
                    console.print(item.getName() + " ");
                }
            }
        } else {
            printUsage(console);
        }
    }

    @Override
    public @Nullable ConsoleCommandCompleter getCompleter() {
        return new ItemConsoleCommandCompleter(itemRegistry,
                (Item i) -> i.getAcceptedCommandTypes().toArray(Class<?>[]::new));
    }
}
