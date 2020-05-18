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
package org.openhab.binding.carnet.internal.api;

import static org.openhab.binding.carnet.internal.api.CarNetApiConstants.*;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.carnet.internal.CarNetException;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetApiErrorMessage;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetApiToken;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetDestinations;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetHistory;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleDetails;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleList;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehiclePosition;
import org.openhab.binding.carnet.internal.api.CarNetApiGSonDTO.CarNetVehicleStatus;
import org.openhab.binding.carnet.internal.config.CarNetAccountConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link CarNetApi} implements the http based API access to CarNet
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetApi {
    private final Logger logger = LoggerFactory.getLogger(CarNetApi.class);
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private CarNetAccountConfiguration config = new CarNetAccountConfiguration();

    private CarNetAccessToken apiToken = new CarNetAccessToken();
    private CarNetAccessToken idToken = new CarNetAccessToken();
    private CarNetAccessToken securityToken = new CarNetAccessToken();

    public CarNetApi(@Nullable HttpClient httpClient) {
        logger.debug("Initializing CarNet API");
        Validate.notNull(httpClient);
        this.httpClient = httpClient;
    }

    public void setConfig(CarNetAccountConfiguration config) {
        logger.debug("Setting up CarNet API for brand {} ({}), user {}", config.brand, config.country, config.user);
        this.config = config;
    }

    public void initialize() throws CarNetException {
        Validate.notNull(config, "API initialize: Configuration not available");
        refreshTokens();
    }

    public void refreshTokens() throws CarNetException {
        createAccessToken();
        createIdToken();
        createSecurityToken();
    }

    private CarNetAccessToken createAccessToken() throws CarNetException {
        if (!apiToken.isExpired()) {
            return apiToken;
        }

        logger.debug("Requesting new access token");
        Map<String, String> headers = new TreeMap<String, String>();
        Map<String, String> data = new TreeMap<>();
        data.put("grant_type", "password");
        data.put("username", config.user);
        data.put("password", config.password);
        String json = httpPost(CNAPI_URI_GET_TOKEN, headers, data, "", false);
        // process token
        CarNetApiToken token = gson.fromJson(json, CarNetApiToken.class);
        if ((token.accessToken == null) || token.accessToken.isEmpty()) {
            throw new CarNetException("Authentication failed: Unable to get access token!");
        }
        apiToken = new CarNetAccessToken(token);
        return apiToken;
    }

    private CarNetAccessToken createIdToken() throws CarNetException {
        if (!idToken.isExpired()) {
            return idToken;
        }

        // Get Audi idToken
        Map<String, String> headers = new TreeMap<>();
        Map<String, String> data = new TreeMap<>();
        data.put("client_id", "mmiconnect_android");
        data.put("scope",
                "openid profile email mbb offline_access mbbuserid myaudi selfservice:read selfservice:write");
        data.put("response_type", "token id_token");
        data.put("grant_type", "password");
        data.put("username", config.user);
        data.put("password", config.password);
        String json = httpPost(CNAPI_URL_GET_AUDI_TOKEN, headers, data, "", false);
        CarNetApiToken token = gson.fromJson(json, CarNetApiToken.class);
        if ((token.idToken == null) || token.idToken.isEmpty()) {
            throw new CarNetException("Authentication failed: Unable to get access token!");
        }
        idToken = new CarNetAccessToken(token);
        return idToken;
    }

    private CarNetAccessToken createSecurityToken() throws CarNetException {
        if (!securityToken.isExpired()) {
            return securityToken;
        }
        createIdToken();

        // "User-Agent": "okhttp/3.7.0",
        // "X-App-Version": "3.14.0",
        // "X-App-Name": "myAudi",
        // "X-Client-Id": "77869e21-e30a-4a92-b016-48ab7d3db1d8",
        // "Host": "mbboauth-1d.prd.ece.vwg-connect.com",
        Map<String, String> headers = new TreeMap<>();
        headers.put(HttpHeader.USER_AGENT.toString(), "okhttp/3.7.0");
        headers.put(CNAPI_HEADER_VERS, "3.14.0");
        headers.put(CNAPI_HEADER_APP, CNAPI_HEADER_APP_MYAUDI);
        headers.put(CNAPI_HEADER_CLIENTID, "77869e21-e30a-4a92-b016-48ab7d3db1d8");
        headers.put(CNAPI_HEADER_HOST, "mbboauth-1d.prd.ece.vwg-connect.com");

        Map<String, String> data = new TreeMap<>();
        data.put("grant_type", "id_token");
        data.put("token", idToken.idToken);
        data.put("scope", "sc2:fal");

        // process token
        String json = httpPost(CNAPI_URL_GET_SEC_TOKEN, headers, data, "", true);
        CarNetApiToken token = gson.fromJson(json, CarNetApiToken.class);
        if ((token.accessToken == null) || token.accessToken.isEmpty()) {
            throw new CarNetException("Authentication failed: Unable to get access token!");
        }
        securityToken = new CarNetAccessToken(token);
        return securityToken;
    }

    private String buildPostData(Map<String, String> dataMap, boolean json) {
        String data = "";
        for (Map.Entry<String, String> e : dataMap.entrySet()) {
            data = data + (data.isEmpty() ? "" : json ? ", " : "&");
            if (!json) {
                data = data + e.getKey() + "=" + e.getValue();
            } else {
                data = data + "\"" + e.getKey() + "\" : \"" + e.getValue() + "\"";
            }
        }
        return json ? "{ " + data + " }" : data;
    }

    public CarNetVehicleList getVehicles() throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        String json = httpGet(CNAPI_URI_VEHICLE_LIST, headers, "");
        CarNetVehicleList vehiceList = gson.fromJson(json, CarNetVehicleList.class);
        Validate.notNull(vehiceList, "Unable to get vehicle list!");
        return vehiceList;
    }

    public CarNetVehicleDetails getVehicleDetails(String vin) throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        String json = httpGet(CNAPI_URI_VEHICLE_DETAILS, headers, vin);
        CarNetVehicleDetails details = gson.fromJson(json, CarNetVehicleDetails.class);
        Validate.notNull(details, "Unable to get vehicle details!");
        return details;
    }

    public CarNetVehicleStatus getVehicleStatus(String vin) throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        String json = httpGet(CNAPI_URI_VEHICLE_STATUS, headers, vin);
        CarNetVehicleStatus status = gson.fromJson(json, CarNetVehicleStatus.class);
        Validate.notNull(status, "Unable to get vehicle details!");
        return status;
    }

    public CarNetVehiclePosition getVehiclePosition(String vin) throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        String json = httpGet(CNAPI_URI_VEHICLE_POSITION, headers, vin);
        CarNetVehiclePosition position = gson.fromJson(json, CarNetVehiclePosition.class);
        Validate.notNull(position, "Unable to get vehicle position!");
        return position;
    }

    public CarNetDestinations getDestinations(String vin) throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        String json = httpGet(CNAPI_URI_DESTINATIONS, headers, vin);
        CarNetDestinations destinations = gson.fromJson(json, CarNetDestinations.class);
        Validate.notNull(destinations, "Unable to get vehicle destinations!");
        return destinations;
    }

    public CarNetHistory getHistory(String vin) throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        String json = httpGet(CNAPI_URI_HISTORY, headers, vin);
        CarNetHistory history = gson.fromJson(json, CarNetHistory.class);
        Validate.notNull(history, "Unable to get vehicle history!");
        return history;
    }

    public void lockDoor(String vin, boolean lock) throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        createSecurityToken();
        // String secToken = getSecurityToken(vin, "rlu_v1/operations/" + (lock ? "LOCK" : "UNLOCK"));
        // String json = httpGet(lock ? CNAPI_URI_DOOR_LOCK : CNAPI_URI_DOOR_UNLOCK, null, headers, vin);

    }

    private Map<String, String> fillAppHeaders() throws CarNetException {
        createAccessToken();
        Validate.notNull(apiToken, "Token must not be null!");

        Map<String, String> headers = new TreeMap<String, String>();
        String auth = MessageFormat.format("{0} {1} {2}", apiToken.authType, CNAPI_AUTH_AUDI_VERS,
                apiToken.accessToken);
        headers.put(HttpHeader.USER_AGENT.toString(), CNAPI_HEADER_USER_AGENT);
        headers.put(CNAPI_HEADER_APP, CNAPI_HEADER_APP_EREMOTE);
        headers.put(CNAPI_HEADER_VERS, CNAPI_HEADER_VERS_VALUE);
        headers.put(HttpHeader.AUTHORIZATION.toString(), auth);
        headers.put(HttpHeader.ACCEPT.toString(), CNAPI_ACCEPTT_JSON);
        return headers;
    }

    private Map<String, String> fillAppHeaders(String securityToken) throws CarNetException {
        Map<String, String> headers = fillAppHeaders();
        headers.put("x-mbbSecToken", securityToken);
        return headers;
    }

    /**
     * Sends a HTTP GET request using the synchronous client
     *
     * @param path Path of the requested resource
     * @return response
     */
    @Nullable
    public String httpGet(String uri, Map<String, String> headers, String vin) throws CarNetException {
        return request(HttpMethod.GET, uri, "", headers, "", vin);
    }

    /**
     * Sends a HTTP POST request using the synchronous client
     *
     * @param path Path of the requested resource
     * @return response
     */
    @Nullable
    public String httpPost(String uri, String parms, Map<String, String> headers, String data, String vin)
            throws CarNetException {
        return request(HttpMethod.POST, uri, parms, headers, data, vin);
    }

    @Nullable
    public String httpPost(String uri, Map<String, String> headers, String data, String vin) throws CarNetException {
        return request(HttpMethod.POST, uri, "", headers, data, vin);
    }

    @Nullable
    public String httpPost(String uri, Map<String, String> headers, Map<String, String> data, String vin, boolean json)
            throws CarNetException {
        return request(HttpMethod.POST, uri, "", headers, buildPostData(data, json), vin);
    }

    @SuppressWarnings("null")
    @Nullable
    private String request(HttpMethod method, String uri, String parms, Map<String, String> headers,
            @Nullable String data, String vin) throws CarNetException {
        Request request = null;
        String url = "";
        try {
            url = getBrandUrl(uri, parms, vin);
            CarNetApiResult apiResult = new CarNetApiResult(method.toString(), url);
            request = httpClient.newRequest(url).method(method).timeout(CNAPI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    Validate.notNull(h.getKey());
                    String value = h.getValue();
                    if ((value != null) && !value.isEmpty()) {
                        request.header(h.getKey(), h.getValue());
                    }
                }
            }
            if ((data != null) && !data.isEmpty()) {
                boolean json = data.startsWith("{");
                request.header(HttpHeader.CONTENT_TYPE.toString(),
                        json ? CNAPI_ACCEPTT_JSON : CNAPI_CONTENTT_FORM_URLENC);
                request.content(new StringContentProvider(data, StandardCharsets.UTF_8));
            }

            // Do request and get response
            logger.debug("HTTP {} {}, data='{}', headers={}", method, url, data, headers);
            ContentResponse contentResponse = request.send();
            apiResult = new CarNetApiResult(contentResponse);
            String response = contentResponse.getContentAsString().replaceAll("\t", "").replaceAll("\r\n", "").trim();
            Validate.notNull(response);

            // validate response, API errors are reported as Json
            logger.trace("HTTP Response: {}", response);
            if (response.contains("\"error\":")) {
                CarNetApiErrorMessage error = gson.fromJson(response, CarNetApiErrorMessage.class);
                throw new CarNetException(
                        "Authentication failed (" + error.code + ", " + error.error + "): " + error.description);
            }
            if (contentResponse.getStatus() != HttpStatus.OK_200) {
                throw new CarNetException("API Call failed", apiResult);
            }
            if (response.isEmpty()) {
                throw new CarNetException("Invalid result received from API, maybe URL problem", apiResult);
            }
            return response;
        } catch (ExecutionException | InterruptedException | TimeoutException | MalformedURLException e) {
            CarNetApiResult apiResult = new CarNetApiResult(request, e);
            throw new CarNetException("API call failed!", apiResult, e);
        } catch (Exception e) {
            CarNetApiResult apiResult = new CarNetApiResult(request, e);
            throw new CarNetException("Unable to process API result!", apiResult, e);
        }
    }

    public @Nullable CarNetAccessToken getToken() {
        return apiToken;
    }

    /**
     * Constructs an URL from the stored information, a specified path and a specified argument string
     *
     */
    private String getBrandUrl(String uriTemplate, String args, String vin) throws MalformedURLException {
        String path = MessageFormat.format(uriTemplate, config.brand, config.country, vin);
        if (!uriTemplate.contains("://")) { // no a full URL
            return getUrl(path.isEmpty() ? path : path + (!args.isEmpty() ? "?" + args : ""));
        } else {
            return path + (!args.isEmpty() ? "?" + args : "");
        }
    }

    /**
     * Constructs an URL from the stored information and a specified path
     *
     * @param path Path to include in URL
     * @return URL
     */
    private String getUrl(String path) throws MalformedURLException {
        return getBaseUrl() + "/" + path;
    }

    private String getBaseUrl() throws MalformedURLException {
        if (config.brand.equalsIgnoreCase(CNAPI_BRAND_AUDI)) {
            return CNAPI_BASE_URL_AUDI;
        }
        if (config.brand.equalsIgnoreCase(CNAPI_BRAND_VW)) {
            return CNAPI_BASE_URL_VW;
        }
        // if (config.brand.equalsIgnoreCase(CNAPI_BRAND_SKODA)) {
        // return CNAPI_BASE_URL_SKODA;
        // }
        throw new MalformedURLException("Unknown brand for base URL");
    }
}
