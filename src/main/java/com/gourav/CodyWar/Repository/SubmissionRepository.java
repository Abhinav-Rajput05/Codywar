package com.gourav.CodyWar.Repository;

import com.gourav.CodyWar.Domain.Entity.Submission;
import com.gourav.CodyWar.Domain.Entity.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /**
     * Fetches all submissions by a specific user for a specific battle.
     */
    List<Submission> findByBattleIdAndUserId(UUID battleId, UUID userId);

    /**
     * All submissions in a battle, newest first.
     * Used to display the submission history in the battle room.
     */
    List<Submission> findByBattleIdOrderBySubmittedAtDesc(UUID battleId);

    /**
     * Finds the first ACCEPTED submission in a battle (for winner determination).
     * Returns the earliest accepted submission by submission time.
     */
    Optional<Submission> findFirstByBattleIdAndStatusOrderBySubmittedAtAsc(UUID battleId, SubmissionStatus status);

    /**
     * Counts submissions by a user in a battle.
     * Used to enforce a maximum submission limit per user per battle.
     */
    long countByBattleIdAndUserId(UUID battleId, UUID userId);

    /**
     * A user's full submission history, newest first.
     * Used for the user profile / history page.
     */
    List<Submission> findByUserIdOrderBySubmittedAtDesc(UUID userId);
}
