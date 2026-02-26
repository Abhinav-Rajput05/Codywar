package com.gourav.CodyWar.Controller;

import com.gourav.CodyWar.Domain.Dto.SubmissionRequestDto;
import com.gourav.CodyWar.Domain.Dto.SubmissionResponseDto;
import com.gourav.CodyWar.Service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * Submit code for a battle.
     * Creates a PENDING submission and triggers async judging.
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<SubmissionResponseDto> createSubmission(
            @Valid @RequestBody SubmissionRequestDto request) {

        SubmissionResponseDto response = submissionService.createSubmission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Poll the status of a specific submission (used for real-time updates).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<SubmissionResponseDto> getSubmissionStatus(
            @PathVariable UUID id) {

        SubmissionResponseDto response = submissionService.getSubmissionStatus(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all submissions for a battle (leaderboard / history).
     */
    @GetMapping("/battle/{battleId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<SubmissionResponseDto>> getSubmissionsByBattle(
            @PathVariable UUID battleId) {

        List<SubmissionResponseDto> submissions = submissionService.getSubmissionsByBattle(battleId);
        return ResponseEntity.ok(submissions);
    }

    /**
     * Get all submissions by a specific user (profile / history page).
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<SubmissionResponseDto>> getSubmissionsByUser(
            @PathVariable UUID userId) {

        List<SubmissionResponseDto> submissions = submissionService.getSubmissionsByUser(userId);
        return ResponseEntity.ok(submissions);
    }

    /**
     * Get a user's submissions within a specific battle.
     */
    @GetMapping("/battle/{battleId}/user/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<SubmissionResponseDto>> getSubmissionsByBattleAndUser(
            @PathVariable UUID battleId,
            @PathVariable UUID userId) {

        List<SubmissionResponseDto> submissions = submissionService.getSubmissionsByBattleAndUser(battleId, userId);
        return ResponseEntity.ok(submissions);
    }
}