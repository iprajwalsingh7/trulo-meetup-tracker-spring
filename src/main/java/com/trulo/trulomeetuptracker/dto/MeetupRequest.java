package com.trulo.trulomeetuptracker.dto;

import com.trulo.trulomeetuptracker.model.Meetup;
import lombok.Data;

import java.util.Date;

@Data
public class MeetupRequest {
    private String title;
    private String description;
    private Meetup.Location location;
    private Date scheduledTime;
    private int duration = 120;
    private boolean isPrivate = false;
    private Meetup.Settings settings = new Meetup.Settings();
}
