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
package org.openhab.binding.carnet.internal.handler;

import static org.openhab.binding.carnet.internal.CarNetBindingConstants.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.IncommensurableException;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PointType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelKind;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.carnet.internal.CarNetDeviceListener;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.CarNetTextResources;
import org.openhab.binding.carnet.internal.CarNetVehicleInformation;
import org.openhab.binding.carnet.internal.api.CarNetApi;
import org.openhab.binding.carnet.internal.api.CarNetApiErrorDTO;
import org.openhab.binding.carnet.internal.api.CarNetApiErrorDTO.CNErrorMessage2Details;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetServiceList;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehiclePosition;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleStatus;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleStatus.CNStoredVehicleDataResponse.CNVehicleData.CNStatusData;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleStatus.CNStoredVehicleDataResponse.CNVehicleData.CNStatusData.CNStatusField;
import org.openhab.binding.carnet.internal.api.CarNetApiResult;
import org.openhab.binding.carnet.internal.config.CarNetVehicleConfiguration;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper;
import org.openhab.binding.carnet.internal.provider.CarNetIChanneldMapper.ChannelIdMapEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CarNetVehicleHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Markus Michels - Initial contribution
 * @author Lorenzo Bernardi - Additional contribution
 *
 */
@NonNullByDefault
public class CarNetVehicleHandler extends BaseThingHandler implements CarNetDeviceListener {
    private final Logger logger = LoggerFactory.getLogger(CarNetVehicleHandler.class);
    private final CarNetTextResources resources;
    private final CarNetIChanneldMapper idMapper;
    private final Map<String, Object> channelData = new HashMap<>();

    private String thingId = "";
    private CarNetApi api;
    private @Nullable CarNetAccountHandler accountHandler;
    private @Nullable ScheduledFuture<?> pollingJob;

    private CarNetVehicleConfiguration config = new CarNetVehicleConfiguration();

    public CarNetVehicleHandler(Thing thing, CarNetApi api, CarNetTextResources resources,
            CarNetIChanneldMapper idMapper) {
        super(thing);
        this.api = api;
        this.resources = resources;
        this.idMapper = idMapper;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing!");
        updateStatus(ThingStatus.UNKNOWN);

        // Register listener and wait for account being ONLINE
        CarNetAccountHandler handler = null;
        Bridge bridge = getBridge();
        if (bridge != null) {
            handler = (CarNetAccountHandler) bridge.getHandler();
        }
        if ((handler == null)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "Account Thing is not initialized!");
            return;
        }
        accountHandler = handler;
        accountHandler.registerListener(this);

        scheduler.schedule(() -> {
            if (accountHandler != null) {
                if (accountHandler.getThing().getStatus() == ThingStatus.ONLINE) {
                    initializeThing();
                }
                setupPollingJob();
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * Brigde status changed
     */
    @Override
    public void stateChanged(ThingStatus status, ThingStatusDetail detail, String message) {
        ThingStatus thingStatus = getThing().getStatus();
        if ((status == ThingStatus.ONLINE) && (thingStatus != ThingStatus.ONLINE)) {
            initializeThing();
        }
        if (thingStatus != status) {
            updateStatus(status, detail, message);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }

        String channelId = channelUID.getIdWithoutGroup();
        String error = "";
        try {
            switch (channelId) {
                case CHANNEL_CONTROL_UPDATE:
                    updateVehicleStatus();
                    updateState(channelUID.getId(), OnOffType.OFF);
                    break;
                case CHANNEL_CONTROL_LOCK:
                    api.lockDoor((OnOffType) command == OnOffType.ON);
                    break;
                case CHANNEL_CONTROL_CLIMA:
                    api.climaControl((OnOffType) command == OnOffType.ON);
                    break;
                case CHANNEL_CONTROL_WINHEAT:
                    api.controlWindowHeating((OnOffType) command == OnOffType.ON);
                    break;
                case CHANNEL_CONTROL_PREHEAT:
                    api.controlPreHeating((OnOffType) command == OnOffType.ON);
                    break;
                default:
                    break;
            }
        } catch (CarNetException e) {
            error = getError(e);
        } catch (RuntimeException e) {
            error = "General Error: " + gs(e.getMessage());
            logger.warn("{}: {}", thingId, error, e);
        }

        if (!error.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
        }
    }

    /**
     * (re-)initialize the thing
     *
     * @return true=successful
     */
    boolean initializeThing() {
        thingId = getThing().getUID().getId();
        channelData.clear(); // clear any cached channels
        config = getConfigAs(CarNetVehicleConfiguration.class);
        Map<String, String> properties = getThing().getProperties();
        String vin = "";
        if (properties.containsKey(PROPERTY_VIN)) {
            vin = properties.get(PROPERTY_VIN);
        }
        if (vin.isEmpty()) {
            logger.info("VIN not set (Thing properties)");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "VIN not set (Thing properties)");
            return false;
        }
        config.vin = vin.toUpperCase();

        boolean successful = true;
        String error = "";
        try {
            updateState(CHANNEL_GROUP_GENERAL + "#" + CHANNEL_GENERAL_VIN, new StringType(vin));
            api.setConfig(config);
            String url = api.getHomeReguionUrl();
            config.homeRegionUrl = url != null ? url : "";
            CarNetServiceList sl = api.getServices();
            config.userId = sl.operationList.userId != null ? sl.operationList.userId : "";
            logger.debug("{}: Active userId = {}, role = {} (securityLevel {}), status = {}", config.vin, config.userId,
                    sl.operationList.role, sl.operationList.securityLevel, sl.operationList.status);
            api.setConfig(config);

            // Try to query status information from vehicle
            Map<String, ChannelIdMapEntry> channels = new HashMap<String, ChannelIdMapEntry>();
            logger.debug("{}: Get Vehicle Status", vin);
            CarNetVehicleStatus status = api.getVehicleStatus();
            for (CNStatusData data : status.storedVehicleDataResponse.vehicleData.data) {
                for (CNStatusField field : data.fields) {
                    try {
                        ChannelIdMapEntry definition = idMapper.find(field.id);
                        if (definition != null) {
                            logger.info("{}: {}={}{} (channel {}#{})", thingId, definition.symbolicName,
                                    gs(field.value), gs(field.unit), definition.groupName, definition.channelName);
                            if (!definition.channelName.isEmpty()) {
                                if (!definition.channelName.startsWith(CHANNEL_GROUP_TIRES)
                                        || !field.value.contains("1")) {
                                    if (!channels.containsKey(definition.id)) {
                                        channels.put(definition.id, definition);
                                    }
                                }
                            }
                        } else {
                            logger.debug("{}: Unknown data field  {}.{}, value={}{}", vin, data.id, field.id,
                                    field.value, gs(field.unit));
                        }
                    } catch (RuntimeException e) {

                    }
                }
            }

            Collection<ChannelIdMapEntry> channelsCol = channels.values();
            createChannels(new ArrayList<ChannelIdMapEntry>(channelsCol));

            // api.getVehicleUsers();

            updateVehicleStatus();

            // Get available services
            // updateClimater();
            String vmi = api.getVehicleManagementInfo();
            String vd = api.getVehicleData();
            String r = api.getVehicleRights();
            String vu = api.getVehicleUsers();
            String ui = api.getUserInfo();
            String ps = api.getUserPairingStatus();
            String t = api.getClimaterTimer();
            String cs = api.getClimaStatus();
            String ch = api.getChargerStatus();
            String h = api.getHistory();
            String std = api.getTripData("shortTerm");
            String ltd = api.getTripData("longTerm");
            String ts = api.getTripStats("shortTerm");
            String d = api.getDestinations();
            String df = api.getMyDestinationsFeed(config.userId);
            String poi = api.getPois();
            String rlu = api.getRluActionHistory();
            String un = api.getUserNews();
            logger.debug(
                    "{}: Additional Data\nHome Registration URL: {}\nVehicle Users:{}\nVehicle Data: {}\nVehicle rights: {}\nHistory:{}\nTimer: {}\nClima Status: {}\nCharger Status: {}\nTrip Data short-term: {}\nTrip Data long-term: {}\nDestinations: {}\nPOIs: {}\nUser Info: {}\nUser Pairing Status: {}\nVehicle Management Info: {}\nRLU Action List: {}\nMyDestinationsFeed: {}\nUser News: {}\nTrip Stats short: {}",
                    config.vin, config.homeRegionUrl, vu, vd, r, h, t, cs, ch, std, ltd, d, poi, ui, ps, vmi, rlu, df,
                    un, ts);
            logger.debug("\n------\n{}: End of Additional Data", config.vin);

        } catch (CarNetException e) {
            CarNetApiErrorDTO res = e.getApiResult().getApiError();
            if (res.description.contains("disabled ")) {
                // Status service in the vehicle is disabled
                String message = "Status service is disabled, check data privacy settings in MMI: " + res;
                logger.debug("{}: {}", vin, message);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
                return false;
            } else {
                successful = false;
                error = getError(e);
            }
        } catch (RuntimeException e) {
            error = "General Error: " + gs(e.getMessage());
            logger.warn("{}: {}", thingId, error, e);
        }

        if (!error.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
        } else {
            updateStatus(ThingStatus.ONLINE);
        }
        return successful;
    }

    /**
     * This routine is called every time the Thing configuration has been changed (e.g. PaperUI)
     */
    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("{}: Thing config updated.", thingId);
        super.handleConfigurationUpdate(configurationParameters);
        initializeThing();
    }

    private String getError(CarNetException e) {
        CarNetApiErrorDTO error = e.getApiResult().getApiError();
        if (!error.isError()) {
            logger.info("{}: API Call failed", thingId);
        } else {
            logger.info("{}: API Call failed: {}", thingId, error);
            String reason = getReason(error);
            if (!reason.isEmpty()) {
                logger.debug("{}: {}", thingId, reason);
            }
        }
        if (error.description.contains(API_STATUS_CLASS_SECURUTY)) {
            // Status service in the vehicle is disabled
            String message = getApiStatus(error.description, API_STATUS_CLASS_SECURUTY);
            logger.debug("{}: {}({})", thingId, message, error.description);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
        }
        if (!error.code.isEmpty()) {
            String message = resources.get(API_STATUS_MSG_PREFIX + "." + error.code);
            logger.debug("{}: {}({} - {})", thingId, message, error.code, error.description);
        }
        CarNetApiResult http = e.getApiResult();
        if (!http.isHttpOk()) {
            logger.debug("{}: {}", thingId, http.response);
        }
        logger.debug("{}: {}", thingId, e.toString());
        return "Unknown Error";
    }

    private String getReason(CarNetApiErrorDTO error) {
        CNErrorMessage2Details details = error.details;
        if (details != null) {
            return gs(details.reason);
        }
        return "";
    }

    private String getApiStatus(String errorMessage, String errorClass) {
        if (errorMessage.contains(errorClass)) {
            // extract the error code like VSR.security.9007
            String key = API_STATUS_MSG_PREFIX + StringUtils
                    .substringBefore(StringUtils.substringAfterLast(errorMessage, API_STATUS_CLASS_SECURUTY + "."), ")")
                    .trim();
            return resources.get(key);
        }
        return "";
    }

    private void createChannels(List<ChannelIdMapEntry> channels) {
        ThingBuilder updatedThing = editThing();
        for (ChannelIdMapEntry channelDef : channels) {
            String channelId = channelDef.channelName;
            String groupId = channelDef.groupName;
            if (groupId.isEmpty()) {
                groupId = CHANNEL_GROUP_STATUS; // default group
            }
            ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, channelId);

            if (getThing().getChannel(groupId + "#" + channelId) == null) {
                // the channel does not exist yet, so let's add it
                String itemType = channelDef.itemType.isEmpty() ? ITEMT_NUMBER : channelDef.itemType;
                logger.debug("{}: Auto-creating channel {}, type {}", thingId, channelId, itemType);
                String label = getChannelAttribute(channelId, "label");
                String description = getChannelAttribute(channelId, "description");
                if (label.isEmpty() || channelDef.itemType.isEmpty()) {
                    label = channelDef.symbolicName;
                }
                Channel channel = ChannelBuilder
                        .create(new ChannelUID(getThing().getUID(), groupId + "#" + channelId), itemType)
                        .withType(channelTypeUID).withLabel(label).withDescription(description)
                        .withKind(ChannelKind.STATE).build();
                updatedThing.withChannel(channel);
            }
        }

        updateThing(updatedThing.build());
    }

    private String getChannelAttribute(String channelId, String attribute) {
        String key = "channel-type.carnet." + channelId + "." + attribute;
        String value = resources.getText(key);
        return !value.equals(key) ? value : "";
    }

    private void updateVehicleStatus() throws CarNetException {
        // Try to query status information from vehicle
        logger.debug("{}: Get Vehicle Status", thingId);
        boolean maintenanceRequired = false; // true if any maintenance is required
        boolean vehicleLocked = true; // aggregates all lock states
        boolean windowsClosed = true; // true if all Windows are closed
        boolean tiresOk = true; // tire if all tire pressures are ok

        CarNetVehicleStatus status = api.getVehicleStatus();
        logger.debug("{}: Vehicle Status:\n{}", config.vin, status);
        for (CNStatusData data : status.storedVehicleDataResponse.vehicleData.data) {
            for (CNStatusField field : data.fields) {
                ChannelIdMapEntry definition = idMapper.find(field.id);
                if (definition != null) {
                    logger.debug("{}: {}={}{} (channel {}#{})", thingId, definition.symbolicName, gs(field.value),
                            gs(field.unit), gs(definition.groupName), gs(definition.channelName));
                    if (!definition.channelName.isEmpty()) {
                        Channel channel = getThing().getChannel(definition.groupName + "#" + definition.channelName);
                        if (channel != null) {
                            logger.debug("Updading channel {} with value {}", channel.getUID(), gs(field.value));
                            switch (definition.itemType) {
                                case ITEMT_SWITCH:
                                    updateSwitchChannel(channel, definition, field);
                                    break;
                                case ITEMT_STRING:
                                    updateState(channel.getUID(), new StringType(gs(field.value)));
                                    break;
                                case ITEMT_NUMBER:
                                default:
                                    updateNumberChannel(channel, definition, field);
                            }
                        } else {
                            logger.debug("Channel {}#{} not found", definition.groupName, definition.channelName);
                        }

                        if ((field.value != null) && !field.value.isEmpty()) {
                            vehicleLocked &= checkLocked(field, definition);
                            maintenanceRequired |= checkMaintenance(field, definition);
                            tiresOk &= checkTires(field, definition);
                            windowsClosed &= checkWindows(field, definition);
                        }
                    }
                } else {
                    logger.debug("{}: Unknown data field  {}.{}, value={} {}", thingId, data.id, field.id, field.value,
                            field.unit);
                }
            }
        }

        // Update aggregated status
        updateState(CHANNEL_GROUP_GENERAL + "#" + CHANNEL_GENERAL_LOCKED, vehicleLocked ? OnOffType.ON : OnOffType.OFF);
        updateState(CHANNEL_GROUP_GENERAL + "#" + CHANNEL_GENERAL_MAINTREQ,
                maintenanceRequired ? OnOffType.ON : OnOffType.OFF);
        updateState(CHANNEL_GROUP_GENERAL + "#" + CHANNEL_GENERAL_TIRESOK, tiresOk ? OnOffType.ON : OnOffType.OFF);
        updateState(CHANNEL_GROUP_GENERAL + "#" + CHANNEL_GENERAL_WINCLOSED,
                windowsClosed ? OnOffType.ON : OnOffType.OFF);

        updateVehicleLocation();
    }

    private boolean updateClimater() {
        return false;
    }

    private boolean checkMaintenance(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("MAINT_ALARM") && !field.value.equals(String.valueOf(1))) {
            logger.debug("{}: Maintenance required: {} has incorrect pressure", thingId, definition.symbolicName);
            return true;
        }
        if (definition.symbolicName.contains("AD_BLUE_RANGE") && (Integer.parseInt(field.value) < 1000)) {
            logger.debug("{}: Maintenance required: Ad Blue at {} (< 1.000km)", thingId, field.value);
            return true;
        }
        return false;
    }

    private boolean checkLocked(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("LOCK")) {
            boolean result = (definition.symbolicName.contains("LOCK2") && field.value.equals(String.valueOf(2)))
                    || (definition.symbolicName.contains("LOCK3") && field.value.equals(String.valueOf(3)));
            if (!result) {
                logger.debug("{}: Vehicle is not completetly locked: {}", thingId, definition.channelName);
                return false;
            }
        }
        return true;
    }

    private boolean checkWindows(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("WINDOWS") && definition.symbolicName.contains("STATE")
                && !field.value.equals(String.valueOf(3))) {
            logger.debug("{}: Window {} is not closed", thingId, definition.channelName);
        }
        return true;
    }

    private boolean checkTires(CNStatusField field, ChannelIdMapEntry definition) {
        if (definition.symbolicName.contains("TIREPRESS") && definition.symbolicName.contains("CURRENT")
                && !field.value.equals(String.valueOf(1))) {
            logger.debug("{}: Tire pressure for {} is not ok", thingId, definition.channelName);
        }
        return true;
    }

    private void updateNumberChannel(Channel channel, ChannelIdMapEntry definition, CNStatusField field) {
        State state = UnDefType.UNDEF;
        String val = gs(field.value);
        if (!val.isEmpty()) {
            if (definition.unit != null) {
                Unit<?> toUnit = definition.unit;
                Unit<?> fromUnit = toUnit;
                ChannelIdMapEntry defFrom = idMapper.updateDefinition(field, definition);
                double value = Double.parseDouble(val);
                if (defFrom.unit != null) {
                    fromUnit = defFrom.unit;
                    if (!fromUnit.equals(toUnit)) {
                        try {
                            BigDecimal bd = new BigDecimal(fromUnit.getConverterToAny(toUnit).convert(value));
                            value = bd.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
                        } catch (UnconvertibleException | IncommensurableException e) {
                            logger.debug("{}: Unable to covert value", thingId);
                        }
                    }
                }
                state = new QuantityType<>(value, toUnit);
            } else {
                state = new DecimalType(val);
            }
        }
        logger.debug("{}: Updating channel {} with {}", thingId, channel.getUID().getId(), state);
        updateState(channel.getUID(), state);
    }

    private void updateSwitchChannel(Channel channel, ChannelIdMapEntry definition, CNStatusField field) {
        int value = Integer.parseInt(gs(field.value));
        boolean on;
        if (definition.symbolicName.toUpperCase().contains("STATE2_")) {
            on = value == 2; // 3=open, 2=closed
        } else if (definition.symbolicName.toUpperCase().contains("STATE3_")
                || definition.symbolicName.toUpperCase().contains("SAFETY_")) {
            on = value == 3; // 2=open, 3=closed
        } else if (definition.symbolicName.toUpperCase().contains("LOCK2_")) {
            // mark a closed lock ON
            on = value == 2; // 2=open, 3=closed
        } else if (definition.symbolicName.toUpperCase().contains("LOCK3_")) {
            // mark a closed lock ON
            on = value == 3; // 3=open, 2=closed
        } else {
            on = value == 1;
        }

        State state = on ? OnOffType.ON : OnOffType.OFF;
        logger.debug("{}: Map value {} to state {} for channe {}, symnolicName{}", thingId, value, state,
                definition.channelName, definition.symbolicName);
        updateState(channel.getUID(), state);
    }

    private void updateVehicleLocation() throws CarNetException {
        try {
            logger.debug("{}: Get Vehicle Position", thingId);
            updateLocation(api.getStoredPosition(), CHANNEL_STORED_POS);

            CarNetVehiclePosition position = updateLocation(api.getVehiclePosition(), CHANNEL_LOCATTION_GEO);
            String time = position.getCarSentTime();
            updateState(CHANNEL_GROUP_LOCATION + "#" + CHANNEL_LOCATTION_TIME, new DateTimeType(time));
            String parkingTime = position.getParkingTime();
            updateState(CHANNEL_GROUP_LOCATION + "#" + CHANNEL_LOCATTION_PARK,
                    parkingTime != null ? new DateTimeType(position.getParkingTime()) : UnDefType.NULL);
        } catch (CarNetException e) {
            updateState(CHANNEL_GROUP_LOCATION + "#" + CHANNEL_STORED_POS, UnDefType.UNDEF);
            updateState(CHANNEL_GROUP_LOCATION + "#" + CHANNEL_LOCATTION_GEO, UnDefType.UNDEF);
            updateState(CHANNEL_GROUP_LOCATION + "#" + CHANNEL_LOCATTION_TIME, UnDefType.UNDEF);
            updateState(CHANNEL_GROUP_LOCATION + "#" + CHANNEL_LOCATTION_PARK, UnDefType.UNDEF);
            if (e.getApiResult().httpCode != HttpStatus.NO_CONTENT_204) { // Ignore No Content = Info not available
                throw e;
            }
        }
    }

    private CarNetVehiclePosition updateLocation(CarNetVehiclePosition position, String channel) {
        PointType location = new PointType(new DecimalType(position.getLattitude()),
                new DecimalType(position.getLongitude()));
        updateState(CHANNEL_GROUP_LOCATION + "#" + channel, location);
        return position;

    }

    /**
     * Sets up a polling job (using the scheduler) with the given interval.
     *
     * @param initialWaitTime The delay before the first refresh. Maybe 0 to immediately
     *            initiate a refresh.
     */
    private void setupPollingJob() {
        cancelPollingJob();
        logger.trace("Setting up polling job with an interval of {} seconds", config.refreshInterval);
        pollingJob = scheduler.scheduleWithFixedDelay(() -> {
            if ((accountHandler != null) && (accountHandler.getThing().getStatus() == ThingStatus.ONLINE)) {
                String error = "";
                try {
                    updateVehicleStatus();
                } catch (CarNetException e) {
                    error = getError(e);
                } catch (RuntimeException e) {
                    error = "General Error: " + gs(e.getMessage());
                    logger.warn("{}: {}", thingId, error, e);
                }

                if (!error.isEmpty()) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error);
                }
            }
        }, 1, config.refreshInterval, TimeUnit.SECONDS);
    }

    /**
     * Cancels the polling job (if one was setup).
     */
    private void cancelPollingJob() {
        if (pollingJob != null) {
            pollingJob.cancel(false);
        }
    }

    private String gs(@Nullable String s) {
        return s != null ? s : "";
    }

    @Override
    public void informationUpdate(@Nullable List<CarNetVehicleInformation> vehicleList) {
    }

    @Override
    public void dispose() {
        cancelPollingJob();
        super.dispose();
    }

}
