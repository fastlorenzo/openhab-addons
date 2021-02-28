/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.yamahamusiccast.internal;

import static org.openhab.binding.yamahamusiccast.internal.YamahaMusiccastBindingConstants.*;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioHTTPServer;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.net.HttpServiceUtil;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link YamahamusiccastHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Lennert Coopman - Initial contribution
 * @author Lorenzo Bernardi - Add audio sink support
 */
@NonNullByDefault
@Component(configurationPid = "binding.yamahamusiccast", service = ThingHandlerFactory.class)
public class YamahaMusiccastHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(YamahaMusiccastHandlerFactory.class);

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set
            .of(YamahaMusiccastBindingConstants.THING_DEVICE, YamahaMusiccastBindingConstants.THING_TYPE_BRIDGE);

    private final YamahaMusiccastStateDescriptionProvider stateDescriptionProvider;

    private Map<String, ServiceRegistration<AudioSink>> audioSinkRegistrations = new ConcurrentHashMap<>();
    private UpnpIOService upnpIOService;
    private AudioHTTPServer audioHTTPServer;
    private NetworkAddressService networkAddressService;
    private String callbackUrl = "";

    @Activate
    public YamahaMusiccastHandlerFactory(@Reference YamahaMusiccastStateDescriptionProvider stateDescriptionProvider,
            @Reference UpnpIOService upnpIOService, @Reference NetworkAddressService networkAddressService,
            @Reference AudioHTTPServer audioHTTPServer) {
        this.stateDescriptionProvider = stateDescriptionProvider;
        this.upnpIOService = upnpIOService;
        this.networkAddressService = networkAddressService;
        this.audioHTTPServer = audioHTTPServer;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(THING_TYPE_BRIDGE)) {
            return new YamahaMusiccastBridgeHandler((Bridge) thing);
        } else if (THING_DEVICE.equals(thingTypeUID)) {
            String callbackUrl = createCallbackUrl();
            YamahaMusiccastHandler handler;
            logger.debug("Creating a new YamahaMusicCastHandler...");
            try {
                handler = new YamahaMusiccastHandler(thing, stateDescriptionProvider, upnpIOService, audioHTTPServer,
                        callbackUrl);
                if (!callbackUrl.isBlank()) {
                    @SuppressWarnings("unchecked")
                    ServiceRegistration<AudioSink> reg = (ServiceRegistration<AudioSink>) bundleContext
                            .registerService(AudioSink.class.getName(), handler, new Hashtable<>());
                    audioSinkRegistrations.put(thing.getUID().toString(), reg);
                }
                return handler;

            } catch (Exception e) {
                logger.debug("The mac address passed to WifiSocketHandler by configurations is not valid.");
            }
        }
        return null;
    }

    @Override
    public void unregisterHandler(final Thing thing) {
        ServiceRegistration<AudioSink> reg = audioSinkRegistrations.get(thing.getUID().toString());
        if (reg != null) {
            reg.unregister();
        }
        super.unregisterHandler(thing);
    }

    private String createCallbackUrl() {
        if (!callbackUrl.isBlank()) {
            return callbackUrl;
        } else {
            final String ipAddress = networkAddressService.getPrimaryIpv4HostAddress();
            if (ipAddress == null) {
                logger.warn("No network interface could be found.");
                return "";
            }

            // we do not use SSL as it can cause certificate validation issues.
            final int port = HttpServiceUtil.getHttpServicePort(bundleContext);
            if (port == -1) {
                logger.warn("Cannot find port of the http service.");
                return "";
            }

            return "http://" + ipAddress + ":" + port;
        }
    }
}
