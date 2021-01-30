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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.yamahamusiccast.internal.dto.UdpMessage;
import org.openhab.core.common.NamedThreadFactory;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link YamahaMusiccastBridgeHandler} is responsible for dispatching UDP events to linked Things.
 *
 * @author Lennert Coopman - Initial contribution
 */
@NonNullByDefault
public class YamahaMusiccastBridgeHandler extends BaseBridgeHandler {
    private Gson gson = new Gson();
    private final Logger logger = LoggerFactory.getLogger(YamahaMusiccastBridgeHandler.class);

    private final ScheduledExecutorService udpScheduler = ThreadPoolManager
            .getScheduledPool("YamahaMusiccastListener" + "-" + thing.getUID().getId());

    private @Nullable ScheduledFuture<?> listenerJob;
    private final UdpListener udpListener;
    //////////////////
    // private final ScheduledExecutorService scheduler = Executors
    // .newSingleThreadExecutor(new NamedThreadFactory(thing.getUID().getAsString(), true));
    private @Nullable ExecutorService executor;
    private @Nullable Future<?> eventListenerJob;
    private static final int UDP_PORT = 41100;
    private static final int SOCKET_TIMEOUT_MILLISECONDS = 3000;
    private static final int BUFFER_SIZE = 5120;
    private @Nullable DatagramSocket socket;
    private Runnable eventListenerRunnable = () -> {
        receivePackets();
    };

    private void receivePackets() {
        try {
            DatagramSocket s = new DatagramSocket(null);
            s.setSoTimeout(SOCKET_TIMEOUT_MILLISECONDS);
            s.setReuseAddress(true);
            InetSocketAddress address = new InetSocketAddress(UDP_PORT);
            s.bind(address);
            socket = s;
            logger.debug("YXC - UDP Listener got socket on port {} with timeout {}", UDP_PORT,
                    SOCKET_TIMEOUT_MILLISECONDS);
        } catch (SocketException e) {
            logger.debug("YXC - UDP Listener got SocketException: {}", e.getMessage(), e);
            socket = null;
            return;
        }

        DatagramPacket packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
        while (socket != null) {
            try {
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                String trackingID = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
                logger.debug("YXC - Received packet: {} (Tracking: {})", received, trackingID);
                handleUDPEvent(received, trackingID);
            } catch (SocketTimeoutException e) {
                // Nothing to do on socket timeout
            } catch (IOException e) {
                logger.debug("YXC - UDP Listener got IOException waiting for datagram: {}", e.getMessage());
                socket = null;
            }
        }
        logger.debug("YXC - UDP Listener exiting");
    }

    /////////////////////////
    public YamahaMusiccastBridgeHandler(Bridge bridge) {
        super(bridge);
        udpListener = new UdpListener(this);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.ONLINE);
        startUDPListenerJob();
        /////////
        if (eventListenerJob == null || eventListenerJob.isCancelled()) {
            executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("binding-miele"));
            eventListenerJob = executor.submit(eventListenerRunnable);
        }
        //////////////////
    }

    @Override
    public void dispose() {
        stopUDPListenerJob();
        super.dispose();
        ////////////////
        if (eventListenerJob != null) {
            eventListenerJob.cancel(true);
            eventListenerJob = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        ////////////////////////
    }

    private void startUDPListenerJob() {
        logger.debug("YXC - Bridge Listener to start in 5 seconds");
        listenerJob = udpScheduler.schedule(udpListener, 5, TimeUnit.SECONDS);
    }

    private void stopUDPListenerJob() {
        if (listenerJob != null) {
            listenerJob.cancel(true);
            udpListener.shutdown();
            logger.debug("YXC - Canceling listener job");
        }
    }

    public void handleUDPEvent(String json, String trackingID) {
        String udpDeviceId = "";
        Bridge bridge = (Bridge) thing;
        for (Thing thing : bridge.getThings()) {
            ThingStatusInfo statusInfo = thing.getStatusInfo();
            switch (statusInfo.getStatus()) {
                case ONLINE:
                    logger.debug("Thing Status: ONLINE - {}", thing.getLabel());

                    YamahaMusiccastHandler handler = (YamahaMusiccastHandler) thing.getHandler();
                    logger.debug("UDP: {} - {} ({} - Tracking: {})", json, handler.getDeviceId(), thing.getLabel(),
                            trackingID);

                    @Nullable
                    UdpMessage targetObject = gson.fromJson(json, UdpMessage.class);
                    udpDeviceId = targetObject.getDeviceId();
                    if (udpDeviceId.equals(handler.getDeviceId())) {
                        handler.processUDPEvent(json, trackingID);
                    }

                    break;
                default:
                    logger.debug("YXC - Thing Status: NOT ONLINE - {} (Tracking: {})", thing.getLabel(), trackingID);
                    break;
            }
        }
    }
}
