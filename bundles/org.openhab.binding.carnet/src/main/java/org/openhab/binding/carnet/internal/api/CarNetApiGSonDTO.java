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

import java.util.ArrayList;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link CarNetApiGSonDTO} defines helper classes for the Json to GSon mapping
 *
 * @author Markus Michels - Initial contribution
 */
public class CarNetApiGSonDTO {

    public static class CNApiToken {
        /*
         * {
         * "access_token": "64217P2jTNpckrCed7o8b5OJWAvTOGuWz8dperT4zY6KkDzREv67",
         * "token_type":"AudiAuth",
         * "expires_in":3600
         * }
         */

        @SerializedName("token_type")
        public String authType;
        @SerializedName("access_token")
        public String accessToken;
        @SerializedName("id_token")
        public String idToken;
        @SerializedName("refresh_token")
        public String refreshToken;
        @SerializedName("securityToken")
        public String securityToken;

        @SerializedName("expires_in")
        public Integer validity;

        public String scope;
    }

    public static class CarNetSecurityPinAuthInfo {
        /*
         * {
         * "securityPinAuthInfo":{
         * "securityToken":
         * "CP8RgXJMs+ctnhROMFWmaplkknKFgMChqTuwiFd0bgqS9XySPwt22a8qP0S1HZbH8Qd3bjq/LkmSxZqz+XVUZzqbmsMv/BiMA07UwXPKtGbF9XiZPS0GKAsNdTHIRzfy2F+rC5w75Fc1X1/DCVBmyEb3iAhHLSZIKJOpFzoSLLJIiYzk4EHJ/2o1Kq8l4vea2b9Nx3pkgk0YGrPDRWlUdUflhXdQhubM28pfkc2tcddDZC1tbjA8Pe/GkSC7rHyyRs1v7o1QzporTl1L8xJPbAoBQDv1jBr06d9a1qf6UPu2Lj+OODWE+qAXfvqueWSFyW8cpyZxIQ+2zCBJCGRDCJ6oK7xOQGtV18lMCRrcl4B2tGvvvkT7jQ899FIr/2blu71KFvXWIgFIOW0sa47PV1P0apHl4wcJr87iwcPNNvAr8SJaxIEl55hvKhcfHQ6ouUJriDN2kqodwgAg79qGwHJoabpkeKJuA6Tq1Gyw82UM7DDD3qalahpR6DTL8bY9YcsHkAywqWqISIBcXepiEaaaOXtTFyID7fiAaruQv1oJwFSwPyUIae5G/UNzkAeaghBWpPis73ZKUxiEOXXHpA==",
         * "securityPinTransmission":{
         * "hashProcedureVersion":2,
         * "challenge":"FF5CB5D49FC09393743FB10B33EFBCF0152BEE11774922E7656819498BA1C07C"},
         * "remainingTries":3}
         * }
         */
        public static class CNSecurityPinAuthInfo {
            public class CNSecurityPinTransmission {
                public Integer hashProcedureVersion;
                public String challenge;
                public Integer remainingTries;
            }

            public String securityToken;
            public CNSecurityPinTransmission securityPinTransmission;
        }

        public CNSecurityPinAuthInfo securityPinAuthInfo;
    }

    public static class CarNetSecurityPinAuthentication {
        /*
         * "securityPinAuthentication": {
         * "securityPin": {
         * "challenge": challenge,
         * "securityPinHash": securityPinHash,
         * },
         * "securityToken": secToken,
         * }
         */
        public static class CNSecuritxPinAuth {
            public class CNSecurityPin {
                public String challenge;
                public String securityPinHash;
            }

            public CNSecurityPin securityPin = new CNSecurityPin();
            public String securityToken = "";
        }

        public CNSecuritxPinAuth securityPinAuthentication = new CNSecuritxPinAuth();
    }

    public static class CarNetVehicleList {
        /*
         * {"userVehicles":{"vehicle": ["WAUZZZF21LN046449"]}}
         */
        public static class CNVehicles {
            public ArrayList<String> vehicle;
        }

        public CNVehicles userVehicles;
    }

    public static class CarNetHomeRegion {
        /*
         * {
         * "homeRegion":{
         * "baseUri":{"systemId":"ICTO-10487", "content":"https://mal-1a.prd.ece.vwg-connect.com/api"}
         * }
         * }
         */
        public class CNHomeRegion {
            public class CNBaseUri {
                public String systemId;
                public String content;
            }

            CNBaseUri baseUri;
        }

        public CNHomeRegion homeRegion;
    }

    public static class CarNetVehicleDetails {
        /*
         * {
         * "carportData":{"systemId":"msg","requestId":"MSG-ivwb2347-1582070213967-85252-ADE",
         * "brand":"Audi","country":"DE","vin":"WAUZZZF21LN046449","modelCode":"4A5BGA",
         * "modelName":"A6 Avant qTDI3.0 V6210 A8",
         * "modelYear":2020,"color":"LX7J","countryCode":"DE","engine":"DDV","mmi":"7UG","transmission":"SQN"}
         * }
         */
        public static class CNVehicleDetails {
            public String systemId;
            public String requestId;
            public String brand;
            public String country;
            public String vin;
            public String modelCode;
            public String modelName;
            public String modelYear;
            public String color;
            public String countryCode;
            public String engine;
            public String mmi;
            public String transmission;
        }

        public CNVehicleDetails carportData;
    }

    public static class CarNetVehicleStatus {

        public static class CNStoredVehicleDataResponse {
            public static class CNVehicleData {
                public static class CNStatusData {
                    /*
                     * "id":"0x0101010001",
                     * "field": [
                     * {
                     * "id":"0x0101010001",
                     * "tsCarSentUtc":"2020-02-20T19:05:18Z",
                     * "tsCarSent":"2020-02-20T20:05:45",
                     * "tsCarCaptured":"2020-02-20T20:05:45","
                     * tsTssReceivedUtc":"2020-02-20T19:09:57Z",
                     * "milCarCaptured":3944,
                     * "milCarSent":3944,
                     * "value":"echo"
                     * }
                     * ]
                     */
                    public static class CNStatusField {
                        public String id;
                        public String tsCarSentUtc;
                        public String tsCarSent;
                        public String tsCarCaptured;
                        public String tsTssReceivedUtc;
                        public Integer milCarCaptured;
                        public Integer milCarSent;
                        public String value;
                        public String unit;
                    }

                    public String id;
                    @SerializedName("field")
                    public ArrayList<CNStatusField> fields;
                }

                public ArrayList<CNStatusData> data;
            }

            public String vin;
            public CNVehicleData vehicleData;
        }

        @SerializedName("StoredVehicleDataResponse")
        public CNStoredVehicleDataResponse storedVehicleDataResponse;
    }

    public static class CarNetVehiclePosition {
        /*
         * {
         * "findCarResponse":
         * { "Position":{"timestampCarSent":"0002-11-28T00:00:00","timestampTssReceived":"2020-02-21T20:11:10Z",
         * "carCoordinate":{"latitude":49529343,"longitude":-1568820},
         * "timestampCarSentUTC":"2020-02-21T20:11:00Z","timestampCarCaptured":"0002-11-28T00:00:00"},
         * "parkingTimeUTC":"2020-02-21T20:08:52Z"}
         * }
         */
        public static class CNFindCarResponse {
            public static class CNPosition {
                public static class CNCarCoordinates {
                    public Integer latitude;
                    public Integer longitude;
                }

                public CNCarCoordinates carCoordinate;
                public String timestampCarSent;
                public String timestampTssReceived;
            }

            @SerializedName("Position")
            public CNPosition carPosition;
            public String parkingTimeUTC;
        }

        private CNFindCarResponse findCarResponse;

        public double getLattitude() {
            return findCarResponse.carPosition.carCoordinate.latitude / 1000000.0;
        }

        public double getLongitude() {
            return findCarResponse.carPosition.carCoordinate.longitude / 1000000.0;
        }

        public String getCarSentTime() {
            return findCarResponse.carPosition.timestampTssReceived;
        }

        public String getParkingTime() {
            return findCarResponse.parkingTimeUTC;
        }
    }

    public static class CarNetDestinations {
        public String destinations;
    }

    public static class CarNetHistory {
        public String destinations;
    }

    public static class CarNetActionResponse {
        public class CNRluActionResponse {
            // {"rluActionResponse":{"requestId":29543257,"vin":"11111111111"}}
            String requestId;
            String vin;
        }

        CNRluActionResponse response;

        CNRluActionResponse rluActionResponse;
    }

    public static class CarNetRluHistory {
        public class CNRluLockEntry {
            // "valid":true,
            // "locked":true,
            // "open":false,
            // "safe":false
            public boolean valid;
            public boolean locked;
            public boolean open;
            public Boolean safe = false; // maybe empty
        }

        public class CNRluLockStatus {
            CNRluLockEntry driverDoor;
            CNRluLockEntry coDriverDoor;
            CNRluLockEntry driverRearDoor;
            CNRluLockEntry coDriverRearDoor;
            CNRluLockEntry frontLid;
            CNRluLockEntry boot;
            CNRluLockEntry flap;
        }

        public class CarNetRluLockAction {
            // "operation":"lock",
            // "timestamp":"2020-07-27T08:15:03Z",
            // "channel":"app",
            // "rluResult":"1",
            // "lockStatus":{list of items
            public String lock;
            public String timestamp;
            public String channel;
            public String rluResult;
            CNRluLockStatus lockStatus;
        }

        public class CarNetRluLockActionList {
            // "action":[CarNetRluLockActionList]
            public ArrayList<CarNetRluLockAction> action;

        }

        // "vin":"XXXXXXXXXX",
        // "steeringWheelSide":"left",
        // "doorModel":"four",
        // "actions":{}
        public String vin;
        public String steeringWheelSide;
        public String doorModel;
        public CarNetRluLockActionList actions;
    }

    public static class CNServiceList {
        // "vin":"XXXXXXXXXX",
        // "channelClient":"APP",
        // "userId":"YkvDWM0RUch5GHQjTzNyyYO3N841",
        // "role":"PRIMARY_USER",
        // "securityLevel":"HG_2c",
        // "status":"ENABLED",
        // "vehicleLifeCycleStatus":{
        // "content":"DELIVERED",
        // "garage":false
        // },
        // "serviceInfo[];"
        // "privacyGroupStates":{
        // "privacyGroupState":[]
        // }
        public String vin;
        public String channelClient;
        public String userId;
        public String role;
        public String securityLevel;
        public String status;
    }

    public static class CarNetServiceList {
        public CNServiceList operationList;
    }
}
