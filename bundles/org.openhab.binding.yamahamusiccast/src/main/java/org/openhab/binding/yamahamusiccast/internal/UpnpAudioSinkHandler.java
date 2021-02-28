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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioHTTPServer;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.audio.FixedLengthAudioStream;
import org.openhab.core.audio.URLAudioStream;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.openhab.core.audio.UnsupportedAudioStreamException;
import org.openhab.core.io.transport.upnp.UpnpIOParticipant;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * * The {@link UpnpAudioSinkHandler} is a base class for ThingHandlers for devices which support UPnP playback. It
 * implements the AudioSink interface.
 * This will allow to register the derived ThingHandler to be registered as a AudioSink in the framework.
 *
 * @author Paul Frank - Initial contribution
 */
public abstract class UpnpAudioSinkHandler extends BaseThingHandler implements AudioSink, UpnpIOParticipant {

    private static final Set<AudioFormat> SUPPORTED_FORMATS = new HashSet<>();
    private static final Set<Class<? extends AudioStream>> SUPPORTED_STREAMS = new HashSet<>();

    static {
        SUPPORTED_FORMATS.add(AudioFormat.MP3);
        SUPPORTED_FORMATS.add(AudioFormat.WAV);

        SUPPORTED_STREAMS.add(AudioStream.class);
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private AudioHTTPServer audioHTTPServer;
    private String callbackUrl;
    private UpnpIOService service;

    public UpnpAudioSinkHandler(Thing thing, UpnpIOService upnpIOService, AudioHTTPServer audioHTTPServer,
            String callbackUrl) {
        super(thing);
        this.audioHTTPServer = audioHTTPServer;
        this.callbackUrl = callbackUrl;
        if (upnpIOService != null) {
            this.service = upnpIOService;
        }
    }

    protected void handlePlayUri(Command command) {
        if (command != null && command instanceof StringType) {
            String url = command.toString();
            if (url.startsWith("http")) {
                try {
                    // playMedia(command.toString());

                    AudioStream audioStream = new URLAudioStream(url);
                    logger.debug("Audio stream url: {}", url);
                    try {
                        process(audioStream);
                        // } catch (IllegalStateException e) {
                        // logger.warn("Cannot play URI ({})", e.getMessage());
                    } catch (UnsupportedAudioFormatException e) {
                        logger.warn("Unsupported audio format: {}", e.toString());
                    } catch (UnsupportedAudioStreamException e) {
                        logger.warn("Unsupported audio stream: {}", e.toString());
                    }
                } catch (AudioException e) {
                    logger.warn("Unhandled audio exception: {}", e.toString());
                }
            } else {
                logger.debug("Stop currently playing stream.");
                stop();
            }
        }
    }

    private void playMedia(String url) {
        logger.debug("Calling stop");
        stop();
        // removeAllTracksFromQueue();

        if (!url.startsWith("x-") && (!url.startsWith("http"))) {
            url = "x-file-cifs:" + url;
        }

        String uriMetaData = String.format(
                "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item><upnp:class>object.item.audioItem.musicTrack</upnp:class><res duration=\"0:00:01.000\" size=\"1\">%s</res></item></DIDL-Lite>",
                url);
        logger.debug("Calling set uri");
        setCurrentURI(url, uriMetaData);

        logger.debug("Calling play");
        play();
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public Set<Class<? extends AudioStream>> getSupportedStreams() {
        return SUPPORTED_STREAMS;
    }

    private void stop() {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", "0");

        Map<String, String> result = service.invokeAction(this, "AVTransport", "Stop", inputs);

        for (String variable : result.keySet()) {
            this.onValueReceived(variable, result.get(variable), "AVTransport");
        }
    }

    private void play() {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", "0");
        inputs.put("Speed", "1");
        Map<String, String> result = service.invokeAction(this, "AVTransport", "Play", inputs);

        for (String variable : result.keySet()) {
            this.onValueReceived(variable, result.get(variable), "AVTransport");
        }
    }

    private void setCurrentURI(String uri, String uriMetaData) {
        if (uri != null && uriMetaData != null) {
            Map<String, String> inputs = new HashMap<>();
            logger.debug("Setting current uri to [{}] with metadata [{}]", uri, uriMetaData);

            try {
                inputs.put("InstanceID", "0");
                inputs.put("CurrentURI", uri);
                inputs.put("CurrentURIMetaData", uriMetaData);
            } catch (NumberFormatException ex) {
                logger.error("Action Invalid Value Format Exception {}", ex.getMessage());
            }

            Map<String, String> result = service.invokeAction(this, "AVTransport", "SetAVTransportURI", inputs);

            for (String variable : result.keySet()) {
                this.onValueReceived(variable, result.get(variable), "AVTransport");
            }
        }
    }

    @Override
    public String getId() {
        return getThing().getUID().toString();
    }

    @Override
    public String getLabel(Locale locale) {
        return getThing().getLabel();
    }

    @Override
    public void process(AudioStream audioStream)
            throws UnsupportedAudioFormatException, UnsupportedAudioStreamException {
        String url = null;
        logger.debug("Processing audio stream");
        if (audioStream == null) {
            // in case the audioStream is null, this should be interpreted as a request to end any currently playing
            // stream.
            logger.debug("Stop currently playing stream.");
            stop();
        } else {
            if (callbackUrl != null) {
                String relativeUrl;
                if (audioStream instanceof FixedLengthAudioStream) {
                    // we serve it on our own HTTP server
                    relativeUrl = audioHTTPServer.serve((FixedLengthAudioStream) audioStream, 30);
                    logger.debug("stream is FixedLengthAudioStream");
                } else {
                    relativeUrl = audioHTTPServer.serve(audioStream);
                    logger.debug("stream is AudioStream");
                }
                url = callbackUrl + relativeUrl;
                logger.debug("Callback URL is {}", url);
            } else {
                logger.warn("We do not have any callback url, so yamaha speaker cannot play the audio stream!");
                return;
            }
        }
        playMedia(url);
    }

    @Override
    public String getUDN() {
        return (String) this.getConfig().get("udn");
    }

    @Override
    public void onValueReceived(String variable, String value, String service) {
        logger.debug("received variable [{}] with value [{}] from service [{}]", variable, value, service);
    }

    @Override
    public void onServiceSubscribed(String service, boolean succeeded) {
    }

    @Override
    public void onStatusChanged(boolean status) {
    }
}
