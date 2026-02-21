package com.trulo.trulomeetuptracker.repository;

import com.trulo.trulomeetuptracker.model.Meetup;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetupRepository extends MongoRepository<Meetup, String> {
    Optional<Meetup> findByInviteCode(String inviteCode);

    List<Meetup> findByCreator(String creatorId);

    List<Meetup> findByParticipants_User(String userId);
}
