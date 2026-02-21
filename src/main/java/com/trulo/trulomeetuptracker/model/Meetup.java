package com.trulo.trulomeetuptracker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "meetups")
public class Meetup {

    @Id
    @JsonProperty("_id")
    private String id;

    private String title;
    private String description;

    // Store the creator's User ID
    private String creator;

    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    private Location location;

    private Date scheduledTime;

    @Builder.Default
    private Integer duration = 120; // in minutes

    @Builder.Default
    private String status = "scheduled"; // scheduled, active, completed, cancelled

    @Builder.Default
    private boolean isPrivate = false;

    @Indexed(unique = true, sparse = true)
    private String inviteCode;

    @Builder.Default
    private Settings settings = new Settings();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // Nested Classes for Sub-Documents
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        private String user; // User ID
        private String status = "invited"; // invited, accepted, declined
        private Date joinedAt = new Date();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String name;
        private String address;
        private Coordinates coordinates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinates {
        private Double latitude;
        private Double longitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Settings {
        private boolean allowLocationSharing = true;
        private boolean autoStartTracking = false;
        private boolean notifyOnArrival = true;
    }
}
