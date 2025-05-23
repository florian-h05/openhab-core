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
package org.openhab.core.model.thing.test.hue;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.model.core.ModelRepository;
import org.openhab.core.test.java.JavaOSGiTest;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.events.AbstractThingRegistryEvent;
import org.openhab.core.thing.events.ThingAddedEvent;
import org.openhab.core.thing.events.ThingRemovedEvent;
import org.openhab.core.thing.events.ThingUpdatedEvent;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * @author Henning Treu - Initial contribution
 */
@NonNullByDefault
public class GenericThingProviderTest extends JavaOSGiTest {

    private static final String TESTMODEL_NAME = "testModel.things";

    private @NonNullByDefault({}) ModelRepository modelRepository;
    private @NonNullByDefault({}) ThingRegistry thingRegistry;

    @BeforeEach
    public void setUp() {
        thingRegistry = getService(ThingRegistry.class);
        assertThat(thingRegistry, is(notNullValue()));
        modelRepository = getService(ModelRepository.class);
        assertThat(modelRepository, is(notNullValue()));
        modelRepository.removeModel(TESTMODEL_NAME);
    }

    @AfterEach
    public void tearDown() {
        modelRepository.removeModel(TESTMODEL_NAME);
    }

    @Test
    public void assertThatThingsThatAreContainedInThingsFilesAreAddedToThingRegistry() {
        Collection<Thing> things = thingRegistry.getAll();
        assertThat(things.size(), is(0));

        String model = "Bridge hue:bridge:myBridge @ \"basement\" [ ip = \"1.2.3.4\", username = \"123\" ] {" + //
                "    LCT001 bulb1 [ lightId = \"1\" ] { Switch : notification }" + //
                "    Bridge bridge myBridge2 [ ] {" + //
                "        LCT001 bulb2 [ ]" + //
                "    }" + //
                "}" + //
                "hue:TEST:bulb4 [ lightId = \"5\"]{" + //
                "    Switch : notification [ duration = \"5\" ]" + //
                "}" + //

                "hue:LCT001:bulb3 @ \"livingroom\" [ lightId = \"4\" ] {" + //
                "    Switch : notification [ duration = \"5\" ]" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(6));

        Thing bridge1 = actualThings.stream().filter(t -> "hue:bridge:myBridge".equals(t.getUID().toString()))
                .findFirst().get();

        assertThat(bridge1, instanceOf(Bridge.class));
        assertThat(bridge1.getChannels().size(), is(0));
        assertThat(bridge1.getBridgeUID(), is(nullValue()));
        assertThat(bridge1.getConfiguration().values().size(), is(2));
        assertThat(bridge1.getConfiguration().get("ip"), is("1.2.3.4"));
        assertThat(bridge1.getConfiguration().get("username"), is("123"));
        assertThat(bridge1.getThingTypeUID().toString(), is("hue:bridge"));
        assertThat(bridge1.getLocation(), is("basement"));

        Thing bridge2 = actualThings.stream().filter(t -> "hue:bridge:myBridge:myBridge2".equals(t.getUID().toString()))
                .findFirst().get();

        assertThat(bridge2, instanceOf(Bridge.class));
        assertThat(bridge2.getChannels().size(), is(0));
        assertThat(bridge2.getBridgeUID(), is(bridge1.getUID()));
        assertThat(bridge2.getConfiguration().values().size(), is(0));
        assertThat(bridge2.getThingTypeUID().toString(), is("hue:bridge"));

        Thing bulb1 = actualThings.stream().filter(t -> "hue:LCT001:myBridge:bulb1".equals(t.getUID().toString()))
                .findFirst().get();

        assertThat(bulb1, isA(Thing.class));
        // three channels should be defined, color and color_temperature from thingType and switch from dsl
        assertThat(bulb1.getChannels().size(), is(3));
        bulb1.getChannels().forEach(c -> {
            assertThat(c.getUID().toString(),
                    anyOf(is("hue:LCT001:myBridge:bulb1:notification"), is("hue:LCT001:myBridge:bulb1:color"),
                            is("hue:LCT001:myBridge:bulb1:color_temperature"), is("hue:TEST:bulb4")));
            assertThat(c.getAcceptedItemType(),
                    anyOf(is(CoreItemFactory.SWITCH), is(CoreItemFactory.COLOR), is(CoreItemFactory.DIMMER)));
        });
        assertThat(bulb1.getBridgeUID(), is(bridge1.getUID()));
        assertThat(bulb1.getConfiguration().values().size(), is(1));
        assertThat(bulb1.getConfiguration().get("lightId"), is("1"));
        assertThat(bulb1.getThingTypeUID().toString(), is("hue:LCT001"));

        Thing bulb2 = actualThings.stream()
                .filter(t -> "hue:LCT001:myBridge:myBridge2:bulb2".equals(t.getUID().toString())).findFirst().get();

        assertThat(bulb2, isA(Thing.class));
        assertThat(bulb2.getChannels().size(), is(2));
        assertThat(bulb2.getBridgeUID(), is(bridge2.getUID()));
        assertThat(bulb2.getConfiguration().values().size(), is(0));
        assertThat(bulb2.getThingTypeUID().toString(), is("hue:LCT001"));

        Thing bulb3 = actualThings.stream().filter(t -> "hue:LCT001:bulb3".equals(t.getUID().toString())).findFirst()
                .get();

        assertThat(bulb3, isA(Thing.class));
        assertThat(bulb3.getChannels().size(), is(3));
        Channel firstChannel = bulb3.getChannels().stream()
                .filter(c -> "hue:LCT001:bulb3:notification".equals(c.getUID().toString())).findFirst().get();
        assertThat(firstChannel.getUID().toString(), is("hue:LCT001:bulb3:notification"));
        assertThat(firstChannel.getAcceptedItemType(), is(CoreItemFactory.SWITCH));
        assertThat(firstChannel.getConfiguration().values().size(), is(1));
        assertThat(firstChannel.getConfiguration().get("duration"), is("5"));
        assertThat(bulb3.getBridgeUID(), is(nullValue()));
        assertThat(bulb3.getConfiguration().values().size(), is(1));
        assertThat(bulb3.getConfiguration().get("lightId"), is("4"));
        assertThat(bulb3.getThingTypeUID().toString(), is("hue:LCT001"));
        assertThat(bulb3.getLocation(), is("livingroom"));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatTheThingsInAnUpdatedThingsFileIsRegisteredInTheThingRegistry() {
        ThingRegistry thingRegistry = getService(ThingRegistry.class);
        assertThat(thingRegistry, is(notNullValue()));
        Collection<Thing> things = thingRegistry.getAll();
        assertThat(things.size(), is(0));
        ModelRepository modelRepository = getService(ModelRepository.class);
        assertThat(modelRepository, is(notNullValue()));

        String model = "Bridge hue:bridge:myBridge [ ip = \"1.2.3.4\", username = \"123\" ] {" + //
                "    LCT001 bulb1 [ lightId = \"1\" ] { Switch : notification }" + //
                "    Bridge bridge myBridge2 [ ] {" + //
                "        LCT001 bulb2 [ ]" + //
                "    }" + //
                "}" + //
                "hue:LCT001:bulb3 [ lightId = \"4\" ] {" + //
                "    Switch : notification [ duration = \"5\" ]" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));

        String newModel = "Bridge hue:bridge:myBridge [ ip = \"5.6.7.8\", secret = \"123\" ] {" + //
                "    LCT001 bulb1 [ ]" + //
                "}" + //
                "hue:LCT001:bulb2 [ lightId = \"2\" ] {" + //
                "    Color : color" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(newModel.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(3));

        Thing bridge1 = actualThings.stream().filter(t -> "hue:bridge:myBridge".equals(t.getUID().toString()))
                .findFirst().get();

        assertThat(bridge1, instanceOf(Bridge.class));
        assertThat(bridge1.getChannels().size(), is(0));
        assertThat(bridge1.getBridgeUID(), is(nullValue()));
        assertThat(bridge1.getConfiguration().values().size(), is(2));
        assertThat(bridge1.getConfiguration().get("ip"), is("5.6.7.8"));
        assertThat(bridge1.getConfiguration().get("secret"), is("123"));
        assertThat(bridge1.getThingTypeUID().toString(), is("hue:bridge"));

        Thing bulb1 = actualThings.stream().filter(t -> "hue:LCT001:myBridge:bulb1".equals(t.getUID().toString()))
                .findFirst().get();

        assertThat(bulb1, isA(Thing.class));
        // there should be color and color_temperature from thingType definition
        assertThat(bulb1.getChannels().size(), is(2));
        assertThat(bulb1.getBridgeUID(), is(bridge1.getUID()));
        assertThat(bulb1.getConfiguration().values().size(), is(0));
        assertThat(bulb1.getThingTypeUID().toString(), is("hue:LCT001"));

        Thing bulb2 = actualThings.stream().filter(t -> "hue:LCT001:bulb2".equals(t.getUID().toString())).findFirst()
                .get();

        assertThat(bulb2, isA(Thing.class));
        // channels should be Color as defined in dsl and color_temperature from thingType
        assertThat(bulb2.getChannels().size(), is(2));
        Channel firstChannel = bulb2.getChannels().getFirst();
        assertThat(firstChannel.getUID().toString(), is("hue:LCT001:bulb2:color"));
        assertThat(firstChannel.getAcceptedItemType(), is(CoreItemFactory.COLOR));
        assertThat(bulb2.getBridgeUID(), is(nullValue()));
        assertThat(bulb2.getConfiguration().values().size(), is(1));
        assertThat(bulb2.getConfiguration().get("lightId"), is("2"));
        assertThat(bulb2.getThingTypeUID().toString(), is("hue:LCT001"));
    }

    @Test
    public void assertThatThingIdCanContainAllCharactersAllowedInConfigDescriptionXSD() {
        Collection<Thing> things = thingRegistry.getAll();
        assertThat(things.size(), is(0));

        String model = "hue:1-thing-id-with-5-dashes_and_3_underscores:thing1 [ lightId = \"1\"]{" + //
                "    Switch : notification [ duration = \"5\" ]" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(1));

        Thing thing1 = actualThings.stream()
                .filter(t -> "hue:1-thing-id-with-5-dashes_and_3_underscores:thing1".equals(t.getUID().toString()))
                .findFirst().get();

        assertThat(thing1, isA(Thing.class));
        assertThat(thing1.getBridgeUID(), is(nullValue()));
        assertThat(thing1.getConfiguration().values().size(), is(1));
        assertThat(thing1.getConfiguration().get("lightId"), is("1"));
        assertThat(thing1.getThingTypeUID().toString(), is("hue:1-thing-id-with-5-dashes_and_3_underscores"));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatBridgeUIDcanBbeSet() {
        Collection<Thing> things = thingRegistry.getAll();
        assertThat(things.size(), is(0));

        String model = //
                "hue:bridge:bridge1 []\n" + //
                        "hue:LCT001:bridge1:bulb (hue:bridge:bridge1) []\n";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(2));

        Thing thing = actualThings.stream().filter(t -> !(t instanceof Bridge)).findFirst().get();
        Bridge bridge = (Bridge) actualThings.stream().filter(Bridge.class::isInstance).findFirst().get();

        assertThat(thing.getBridgeUID().toString(), is("hue:bridge:bridge1"));
        assertThat(bridge.getThings().contains(thing), is(true));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatChannelDefinitionsCanBeReferenced() {
        Collection<Thing> things = thingRegistry.getAll();
        assertThat(things.size(), is(0));

        String model = "Bridge hue:bridge:bridge1 [] {" + //
                "    LCT001 bulb_default []" + //
                "    LCT001 bulb_custom [] {" + //
                "        Channels:" + //
                "            Type color : manual []" + //
                "            Type color : manualWithLabel \"With Label\" []" + //
                "    }" + //
                "    LCT001 bulb_broken [] {" + //
                "        Channels:" + //
                "            Type broken : manual []" + //
                "            Type broken : manualWithLabel \"With Label\" []" + //
                "    }" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(4));

        actualThings.stream().filter(t -> "bulb_default".equals(t.getUID().getId())).findFirst().get();

        Thing thingDefault = actualThings.stream().filter(t -> "bulb_default".equals(t.getUID().getId())).findFirst()
                .get();
        assertThat(thingDefault.getChannels().size(), is(2));

        Thing thingCustom = actualThings.stream().filter(t -> "bulb_custom".equals(t.getUID().getId())).findFirst()
                .get();
        assertThat(thingCustom.getChannels().size(), is(4));
        assertThat(thingCustom.getChannel("manual").getChannelTypeUID(),
                is(equalTo(new ChannelTypeUID("hue", "color"))));
        assertThat(thingCustom.getChannel("manual").getLabel(), is("colorLabel")); // default from thing type
        assertThat(thingCustom.getChannel("manualWithLabel").getLabel(), is("With Label")); // manual overrides default

        Thing thingBroken = actualThings.stream().filter(t -> "bulb_broken".equals(t.getUID().getId())).findFirst()
                .get();
        assertThat(thingBroken.getChannels().size(), is(4));
        assertThat(thingBroken.getChannel("manual").getChannelTypeUID(),
                is(equalTo(new ChannelTypeUID("hue", "broken"))));
        assertThat(thingBroken.getChannel("manual").getKind(), is(ChannelKind.STATE));
        assertThat(thingBroken.getChannel("manual").getAcceptedItemType(), is(nullValue()));
        assertThat(thingBroken.getChannel("manual").getLabel(), is(nullValue()));
        assertThat(thingBroken.getChannel("manualWithLabel").getLabel(), is("With Label"));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatChannelDefinitionsWithDimensionAreParsed() {
        Collection<Thing> things = thingRegistry.getAll();
        assertThat(things.size(), is(0));

        String model = "hue:SENSOR:sensor_custom [] {" + //
                "    Number:Temperature : sensor1" + //
                "    Number:Pressure : sensor2" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(1));

        Thing thingDefault = actualThings.stream().filter(t -> "sensor_custom".equals(t.getUID().getId())).findFirst()
                .get();
        assertThat(thingDefault.getChannels().size(), is(2));

        assertThat(thingDefault.getChannel("sensor1").getAcceptedItemType(), is("Number:Temperature"));
        assertThat(thingDefault.getChannel("sensor2").getAcceptedItemType(), is("Number:Pressure"));
    }

    @Test
    public void assertThatConfigParameterListsAreParsed() {
        Collection<Thing> things = thingRegistry.getAll();
        assertThat(things.size(), is(0));

        String model = "hue:SENSOR:sensor_custom [config = \"value1\",\"value2\",\"value3\"]";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(1));

        Thing thingDefault = actualThings.stream().filter(t -> "sensor_custom".equals(t.getUID().getId())).findFirst()
                .get();

        @SuppressWarnings("unchecked")
        Collection<String> valueCollection = (Collection<String>) thingDefault.getConfiguration().get("config");
        assertThat(valueCollection, hasItems("value1", "value2", "value3"));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatThingsCanBeEmbeddedWithinBridgesInShortNotation() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = "Bridge hue:bridge:myBridge @ \"basement\" [ ip = \"1.2.3.4\", username = \"123\" ] {" + //
                "    LCT001 bulb1 [ lightId = \"1\" ] { Switch : notification }" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(2));

        assertThat(actualThings.stream().filter(t -> "hue:bridge:myBridge".equals(t.getUID().toString())).findFirst()
                .get(), is(notNullValue()));
        assertThat(actualThings.stream().filter(t -> "hue:LCT001:myBridge:bulb1".equals(t.getUID().toString()))
                .findFirst().get(), is(notNullValue()));
        assertThat(actualThings.stream().filter(t -> "hue:LCT001:myBridge:bulb1".equals(t.getUID().toString()))
                .findFirst().get().getBridgeUID().toString(), is(equalTo("hue:bridge:myBridge")));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatThingsCanBeEmbeddedWithinBridgesInLongNotation() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = "Bridge hue:bridge:myBridge @ \"basement\" [ ip = \"1.2.3.4\", username = \"123\" ] {" + //
                "    hue:LCT001:bulb1 [ lightId = \"1\" ] { Switch : notification }" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(2));

        assertThat(actualThings.stream().filter(t -> "hue:bridge:myBridge".equals(t.getUID().toString())).findFirst()
                .get(), is(notNullValue()));
        assertThat(
                actualThings.stream().filter(t -> "hue:LCT001:bulb1".equals(t.getUID().toString())).findFirst().get(),
                is(notNullValue()));
        assertThat(actualThings.stream().filter(t -> "hue:LCT001:bulb1".equals(t.getUID().toString())).findFirst().get()
                .getBridgeUID().toString(), is(equalTo("hue:bridge:myBridge")));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatStandaloneThingsCanHaveBridgesInLongNotation() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = """
                Bridge hue:bridge:myBridge @ "basement" [ ip = "1.2.3.4", username = "123" ]
                hue:LCT001:bulb1 (hue:bridge:myBridge) [ lightId = "1" ] { Switch : notification }
                """;

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(2));

        assertThat(actualThings.stream().filter(t -> "hue:bridge:myBridge".equals(t.getUID().toString())).findFirst()
                .get(), is(notNullValue()));
        assertThat(
                actualThings.stream().filter(t -> "hue:LCT001:bulb1".equals(t.getUID().toString())).findFirst().get(),
                is(notNullValue()));
        assertThat(actualThings.stream().filter(t -> "hue:LCT001:bulb1".equals(t.getUID().toString())).findFirst().get()
                .getBridgeUID().toString(), is(equalTo("hue:bridge:myBridge")));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatStandaloneThingWithAbridgeWorksInShortNotation() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = "Bridge hue:bridge:myBridge @ \"basement\" [ ip = \"1.2.3.4\", username = \"123\" ]\n"
                + "LCT001 bulb1 (hue:bridge:myBridge) [ lightId = \"1\" ] { Switch : notification }";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(2));

        assertThat(actualThings.stream().filter(t -> "hue:bridge:myBridge".equals(t.getUID().toString())).findFirst()
                .get(), is(notNullValue()));
        assertThat(
                actualThings.stream().filter(t -> "hue:LCT001:bulb1".equals(t.getUID().toString())).findFirst().get(),
                is(notNullValue()));
        assertThat(actualThings.stream().filter(t -> "hue:LCT001:bulb1".equals(t.getUID().toString())).findFirst().get()
                .getBridgeUID().toString(), is(equalTo("hue:bridge:myBridge")));
    }

    @Test
    public void assertThatStandaloneThingWithoutAbridgeDoesNotWorkInShortNotation() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = """
                LCT001 bulb1 [ lightId = "1" ] { Switch : notification }
                hue:LCT001:bulb2 (hue:bridge:myBridge) [ lightId = "2" ] { Switch : notification }
                """;

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(1));
        assertThat(
                actualThings.stream().filter(t -> "hue:LCT001:bulb2".equals(t.getUID().toString())).findFirst().get(),
                is(notNullValue()));
    }

    @Test
    public void assertThatUpdatingAnEmbeddedThingCausesTheRightBehaviorWrtAddingAndUpdatingAndNoRemovedEventsAreSent() {
        List<AbstractThingRegistryEvent> receivedEvents = new ArrayList<>();

        EventSubscriber thingEventSubscriber = new EventSubscriber() {
            @Override
            public Set<String> getSubscribedEventTypes() {
                return Set.of(ThingUpdatedEvent.TYPE, ThingRemovedEvent.TYPE, ThingAddedEvent.TYPE);
            }

            @Override
            public void receive(Event event) {
                AbstractThingRegistryEvent registryEvent = (AbstractThingRegistryEvent) event;
                if (List.of("hue:bridge:my1234Bridge", "hue:LCT001:my1234Bridge:myKitchenBulb1")
                        .contains(registryEvent.getThing().UID)) {
                    receivedEvents.add(registryEvent);
                }
            }
        };

        registerService(thingEventSubscriber);

        assertThat(thingRegistry.getAll().size(), is(0));

        String model = "Bridge hue:bridge:my1234Bridge [ ip = \"1.2.3.4\", username = \"123\" ] {" + //
                "    LCT001 myKitchenBulb1 [ lightId = \"1\" ] { Switch : notification }" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        assertThat(thingRegistry.getAll().size(), is(2));
        waitForAssert(() -> {
            assertThat(receivedEvents.size(), is(equalTo(2)));
            receivedEvents.forEach(e -> assertThat(e, instanceOf(ThingAddedEvent.class)));
        });
        receivedEvents.clear();

        String newModel = "Bridge hue:bridge:my1234Bridge [ ip = \"1.2.3.4\", username = \"123\" ]  {" + //
                "    LCT001 myKitchenBulb1 [ lightId = \"2\" ] { Switch : notification }" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(newModel.getBytes()));
        assertThat(thingRegistry.getAll().size(), is(2));

        waitForAssert(() -> {
            assertEquals(1, receivedEvents.size());
            Event event = receivedEvents.getFirst();
            assertEquals(ThingUpdatedEvent.class, event.getClass());
            ThingUpdatedEvent thingUpdatedEvent = (ThingUpdatedEvent) event;
            assertEquals("hue:LCT001:my1234Bridge:myKitchenBulb1", thingUpdatedEvent.getThing().UID);
        });
    }

    @Test
    public void assertThatAnEmptyModelDoesNotCauseAnyHarm() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = "";
        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(0));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatDefaultConfigurationsAreAppliedToChannels() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = "Bridge hue:bridge:myBridge @ \"basement\" [ ] {" + //
                "    LCT001 myBulb [ lightId = \"1\" ] {" + //
                "        Type color : myChannel []" + //
                "    }" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        // ensure a standard channel has its default values
        assertThat(
                actualThings.stream().filter(t -> "hue:LCT001:myBridge:myBulb".equals(t.getUID().toString()))
                        .findFirst().get().getChannel("color").getConfiguration().get("defaultConfig"),
                is(equalTo("defaultValue")));

        // ensure a user-defined channel has its default values
        assertThat(
                actualThings.stream().filter(t -> "hue:LCT001:myBridge:myBulb".equals(t.getUID().toString()))
                        .findFirst().get().getChannel("myChannel").getConfiguration().get("defaultConfig"),
                is(equalTo("defaultValue")));
    }

    @Test
    @SuppressWarnings("null")
    public void assertThatChannelsWithinChannelGroupsCanBeOverridden() {
        assertThat(thingRegistry.getAll().size(), is(0));

        String model = "Bridge hue:bridge:myBridge [] {" + //
                "    Thing grouped myGroupedThing [] {" + //
                "        Type color : group#bar [" + //
                "            myProp=\"successful\"," + //
                "            customConfig=\"yes\"" + //
                "        ]" + //
                "    }" + //
                "}";

        modelRepository.addOrRefreshModel(TESTMODEL_NAME, new ByteArrayInputStream(model.getBytes()));
        Collection<Thing> actualThings = thingRegistry.getAll();

        assertThat(actualThings.size(), is(equalTo(2)));

        // ensure the non-declared channel is there and has its default properties
        Thing myGroupedThing = actualThings.stream()
                .filter(t -> "hue:grouped:myBridge:myGroupedThing".equals(t.getUID().toString())).findFirst().get();
        assertThat(myGroupedThing.getChannel("group#foo"), is(notNullValue()));
        assertThat(myGroupedThing.getChannel("group#foo").getConfiguration().get("defaultConfig"),
                is(equalTo("defaultValue")));
        assertThat(myGroupedThing.getChannel("group#foo").getConfiguration().get("customConfig"), is(equalTo("none")));

        assertThat(myGroupedThing.getChannel("group#bar"), is(notNullValue()));
        // ensure the non-declared default property is there
        assertThat(myGroupedThing.getChannel("group#bar").getConfiguration().get("defaultConfig"),
                is(equalTo("defaultValue")));
        // ensure overriding a default property worked
        assertThat(myGroupedThing.getChannel("group#bar").getConfiguration().get("customConfig"), is(equalTo("yes")));
        // ensure an additional property can be set
        assertThat(myGroupedThing.getChannel("group#bar").getConfiguration().get("myProp"), is(equalTo("successful")));
    }
}
