package com.trulo.trulomeetuptracker.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocketHandler {

    private final SocketIOServer server;

    // Map: userId -> ActiveUserData
    private final Map<String, ActiveUserData> activeUsers = new ConcurrentHashMap<>();
    // Map: meetupId -> Set of userIds
    private final Map<String, Set<String>> meetupRooms = new ConcurrentHashMap<>();

    @Autowired
    public SocketHandler(SocketIOServer server) {
        this.server = server;
    }

    private String getUserId(SocketIOClient client) {
        List<String> userIdList = client.getHandshakeData().getUrlParams().get("userId");
        if (userIdList != null && !userIdList.isEmpty()) {
            return userIdList.get(0);
        }
        return null; // Should not happen due to AuthorizationListener
    }

    @OnConnect
    public void onConnect(SocketIOClient client) {
        String userId = getUserId(client);
        System.out.println("User connected: " + userId);
        client.joinRoom("user_" + userId);
    }

    @OnEvent("join_meetup")
    public void onJoinMeetup(SocketIOClient client, String meetupId) {
        String userId = getUserId(client);
        client.joinRoom("meetup_" + meetupId);

        ActiveUserData userData = new ActiveUserData();
        userData.setSocketId(client.getSessionId().toString());
        userData.setMeetupId(meetupId);
        userData.setLastSeen(new Date());
        activeUsers.put(userId, userData);

        meetupRooms.computeIfAbsent(meetupId, k -> Collections.synchronizedSet(new HashSet<>())).add(userId);

        System.out.println("User " + userId + " joined meetup " + meetupId);

        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("timestamp", new Date());

        // Broadcast to other users in the room
        server.getRoomOperations("meetup_" + meetupId)
                .getClients()
                .forEach(c -> {
                    if (!c.getSessionId().equals(client.getSessionId())) {
                        c.sendEvent("user_joined", notification);
                    }
                });
    }

    @OnEvent("location_update")
    public void onLocationUpdate(SocketIOClient client, LocationUpdateData data) {
        String userId = getUserId(client);
        String meetupId = data.getMeetupId();

        if (activeUsers.containsKey(userId)) {
            ActiveUserData userData = activeUsers.get(userId);
            userData.setLocation(new Location(data.getLatitude(), data.getLongitude()));
            userData.setLastSeen(new Date());
            activeUsers.put(userId, userData);
        }

        Map<String, Object> broadcastData = new HashMap<>();
        broadcastData.put("userId", userId);
        broadcastData.put("latitude", data.getLatitude());
        broadcastData.put("longitude", data.getLongitude());
        broadcastData.put("timestamp", new Date());

        server.getRoomOperations("meetup_" + meetupId)
                .getClients()
                .forEach(c -> {
                    if (!c.getSessionId().equals(client.getSessionId())) {
                        c.sendEvent("location_updated", broadcastData);
                    }
                });
    }

    @OnEvent("leave_meetup")
    public void onLeaveMeetup(SocketIOClient client, String meetupId) {
        String userId = getUserId(client);
        handleLeave(client, userId, meetupId);
    }

    @OnEvent("get_active_users")
    public void onGetActiveUsers(SocketIOClient client, String meetupId) {
        Set<String> meetupUsers = meetupRooms.getOrDefault(meetupId, new HashSet<>());
        List<Map<String, Object>> activeUsersData = new ArrayList<>();

        for (String uid : meetupUsers) {
            if (activeUsers.containsKey(uid)) {
                ActiveUserData userData = activeUsers.get(uid);
                Map<String, Object> entry = new HashMap<>();
                entry.put("userId", uid);
                entry.put("location", userData.getLocation());
                entry.put("lastSeen", userData.getLastSeen());
                activeUsersData.add(entry);
            }
        }

        client.sendEvent("active_users", activeUsersData);
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String userId = getUserId(client);
        System.out.println("User disconnected: " + userId);

        if (userId != null && activeUsers.containsKey(userId)) {
            ActiveUserData userData = activeUsers.get(userId);
            String meetupId = userData.getMeetupId();

            if (meetupId != null) {
                handleLeave(client, userId, meetupId);
            }
            activeUsers.remove(userId);
        }
    }

    private void handleLeave(SocketIOClient client, String userId, String meetupId) {
        client.leaveRoom("meetup_" + meetupId);

        Set<String> usersInRoom = meetupRooms.get(meetupId);
        if (usersInRoom != null) {
            usersInRoom.remove(userId);
            if (usersInRoom.isEmpty()) {
                meetupRooms.remove(meetupId);
            }
        }

        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("timestamp", new Date());

        server.getRoomOperations("meetup_" + meetupId)
                .getClients()
                .forEach(c -> {
                    if (!c.getSessionId().equals(client.getSessionId())) {
                        c.sendEvent("user_left", notification);
                    }
                });

        System.out.println("User " + userId + " left meetup " + meetupId);
    }

    // --- DTOs ---

    public static class ActiveUserData {
        private String socketId;
        private String meetupId;
        private Location location;
        private Date lastSeen;

        public String getSocketId() {
            return socketId;
        }

        public void setSocketId(String socketId) {
            this.socketId = socketId;
        }

        public String getMeetupId() {
            return meetupId;
        }

        public void setMeetupId(String meetupId) {
            this.meetupId = meetupId;
        }

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

        public Date getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(Date lastSeen) {
            this.lastSeen = lastSeen;
        }
    }

    public static class LocationUpdateData {
        private Double latitude;
        private Double longitude;
        private String meetupId;

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }

        public String getMeetupId() {
            return meetupId;
        }

        public void setMeetupId(String meetupId) {
            this.meetupId = meetupId;
        }
    }

    public static class Location {
        private Double latitude;
        private Double longitude;

        public Location(Double latitude, Double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }
    }
}
