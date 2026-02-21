package com.trulo.trulomeetuptracker.controller;

import com.trulo.trulomeetuptracker.dto.MeetupRequest;
import com.trulo.trulomeetuptracker.model.Meetup;
import com.trulo.trulomeetuptracker.model.User;
import com.trulo.trulomeetuptracker.repository.MeetupRepository;
import com.trulo.trulomeetuptracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meetups")
public class MeetupController {

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createMeetup(@RequestBody MeetupRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Meetup meetup = Meetup.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .creator(currentUser.getId())
                .location(request.getLocation())
                .scheduledTime(request.getScheduledTime())
                .duration(request.getDuration())
                .isPrivate(request.isPrivate())
                .settings(request.getSettings() != null ? request.getSettings() : new Meetup.Settings())
                .build();

        if (!meetup.isPrivate()) {
            meetup.setInviteCode(generateInviteCode());
        }

        Meetup.Participant currentParticipant = new Meetup.Participant(currentUser.getId(), "accepted", new Date());
        meetup.getParticipants().add(currentParticipant);

        meetupRepository.save(meetup);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Meetup created successfully");
        response.put("meetup", populateMeetup(meetup));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<?> getMeetups(@RequestParam(required = false) String status) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<Meetup> creatorMeetups = meetupRepository.findByCreator(currentUser.getId());
        List<Meetup> participantMeetups = meetupRepository.findByParticipants_User(currentUser.getId());

        Set<Meetup> allMeetups = new HashSet<>();
        allMeetups.addAll(creatorMeetups);
        allMeetups.addAll(participantMeetups);

        List<Map<String, Object>> sortedMeetups = allMeetups.stream()
                .filter(m -> status == null || status.equals(m.getStatus()))
                .sorted((m1, m2) -> {
                    if (m1.getScheduledTime() == null)
                        return 1;
                    if (m2.getScheduledTime() == null)
                        return -1;
                    return m2.getScheduledTime().compareTo(m1.getScheduledTime()); // descending
                })
                .map(this::populateMeetup)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("meetups", sortedMeetups);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMeetup(@PathVariable String id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Meetup meetup = meetupRepository.findById(id).orElse(null);

        if (meetup == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Meetup not found"));
        }

        boolean isParticipant = meetup.getParticipants().stream()
                .anyMatch(p -> p.getUser().equals(currentUser.getId()));
        boolean isCreator = meetup.getCreator().equals(currentUser.getId());

        if (!isParticipant && !isCreator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
        }

        return ResponseEntity.ok(Map.of("meetup", populateMeetup(meetup)));
    }

    @PostMapping("/join/{inviteCode}")
    public ResponseEntity<?> joinMeetup(@PathVariable String inviteCode) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Meetup meetup = meetupRepository.findByInviteCode(inviteCode).orElse(null);

        if (meetup == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Invalid invite code"));
        }

        Optional<Meetup.Participant> existingOpt = meetup.getParticipants().stream()
                .filter(p -> p.getUser().equals(currentUser.getId()))
                .findFirst();

        if (existingOpt.isPresent()) {
            Meetup.Participant existing = existingOpt.get();
            if ("accepted".equals(existing.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "You are already part of this meetup"));
            } else {
                existing.setStatus("accepted");
            }
        } else {
            meetup.getParticipants().add(new Meetup.Participant(currentUser.getId(), "accepted", new Date()));
        }

        meetupRepository.save(meetup);

        return ResponseEntity.ok(Map.of(
                "message", "Successfully joined meetup",
                "meetup", populateMeetup(meetup)));
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<?> inviteUsers(@PathVariable String id, @RequestBody Map<String, List<String>> body) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Meetup meetup = meetupRepository.findById(id).orElse(null);

        if (meetup == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Meetup not found"));
        }

        if (!meetup.getCreator().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only creator can invite users"));
        }

        List<String> userIds = body.getOrDefault("userIds", new ArrayList<>());
        List<String> newParticipants = new ArrayList<>();

        for (String userId : userIds) {
            boolean exists = meetup.getParticipants().stream().anyMatch(p -> p.getUser().equals(userId));
            if (!exists) {
                meetup.getParticipants().add(new Meetup.Participant(userId, "invited", new Date()));
                newParticipants.add(userId);
            }
        }

        if (!newParticipants.isEmpty()) {
            meetupRepository.save(meetup);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Invited " + newParticipants.size() + " users to meetup",
                "meetup", populateMeetup(meetup)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Meetup meetup = meetupRepository.findById(id).orElse(null);

        if (meetup == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Meetup not found"));
        }

        if (!meetup.getCreator().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only creator can update meetup status"));
        }

        meetup.setStatus(body.get("status"));
        meetupRepository.save(meetup);

        return ResponseEntity.ok(Map.of(
                "message", "Meetup status updated",
                "meetup", populateMeetup(meetup)));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<?> leaveMeetup(@PathVariable String id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Meetup meetup = meetupRepository.findById(id).orElse(null);

        if (meetup == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Meetup not found"));
        }

        if (meetup.getCreator().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Creator cannot leave meetup. Delete it instead."));
        }

        meetup.getParticipants().removeIf(p -> p.getUser().equals(currentUser.getId()));
        meetupRepository.save(meetup);

        return ResponseEntity.ok(Map.of("message", "Left meetup successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMeetup(@PathVariable String id) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Meetup meetup = meetupRepository.findById(id).orElse(null);

        if (meetup == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Meetup not found"));
        }

        if (!meetup.getCreator().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only creator can delete meetup"));
        }

        meetupRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Meetup deleted successfully"));
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private Map<String, Object> populateMeetup(Meetup meetup) {
        Map<String, Object> populated = new HashMap<>();
        populated.put("_id", meetup.getId());
        populated.put("title", meetup.getTitle());
        populated.put("description", meetup.getDescription());
        populated.put("location", meetup.getLocation());
        populated.put("scheduledTime", meetup.getScheduledTime());
        populated.put("duration", meetup.getDuration());
        populated.put("status", meetup.getStatus());
        populated.put("isPrivate", meetup.isPrivate());
        populated.put("inviteCode", meetup.getInviteCode());
        populated.put("settings", meetup.getSettings());
        populated.put("createdAt", meetup.getCreatedAt());

        // Populate Creator
        User creator = userRepository.findById(meetup.getCreator()).orElse(null);
        if (creator != null) {
            Map<String, String> cMap = new HashMap<>();
            cMap.put("_id", creator.getId());
            cMap.put("username", creator.getUsername());
            cMap.put("displayName", creator.getDisplayName());
            cMap.put("avatar", creator.getAvatar());
            populated.put("creator", cMap);
        } else {
            populated.put("creator", meetup.getCreator()); // Fallback to ID
        }

        // Populate Participants
        List<Map<String, Object>> parts = new ArrayList<>();
        for (Meetup.Participant p : meetup.getParticipants()) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("status", p.getStatus());
            pMap.put("joinedAt", p.getJoinedAt());

            User u = userRepository.findById(p.getUser()).orElse(null);
            if (u != null) {
                Map<String, String> uMap = new HashMap<>();
                uMap.put("_id", u.getId());
                uMap.put("username", u.getUsername());
                uMap.put("displayName", u.getDisplayName());
                uMap.put("avatar", u.getAvatar());
                pMap.put("user", uMap);
            } else {
                pMap.put("user", p.getUser()); // Fallback to ID
            }
            parts.add(pMap);
        }
        populated.put("participants", parts);

        return populated;
    }
}
