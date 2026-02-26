package com.gourav.CodyWar.Service;

import com.gourav.CodyWar.Domain.Dto.SubmissionRequestDto;
import com.gourav.CodyWar.Domain.Dto.SubmissionResponseDto;
import com.gourav.CodyWar.Domain.Entity.*;
import com.gourav.CodyWar.Exception.BattleNotActiveException;
import com.gourav.CodyWar.Exception.ResourceNotFoundException;
import com.gourav.CodyWar.Repository.BattleParticipantRepository;
import com.gourav.CodyWar.Repository.BattleRepository;
import com.gourav.CodyWar.Repository.SubmissionRepository;
import com.gourav.CodyWar.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service handling all code submission logic, including persistence,
 * async judging triggers, and battle status updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final BattleRepository battleRepository;
    private final UserRepository userRepository;
    private final BattleParticipantRepository battleParticipantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates a new submission, validates battle state, and triggers async judging.
     * 
     * @param request User's submission data.
     * @return SubmissionResponseDto in PENDING state.
     */
    @Transactional
    public SubmissionResponseDto createSubmission(SubmissionRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        Battle battle = battleRepository.findById(request.getBattleId())
                .orElseThrow(() -> new ResourceNotFoundException("Battle", request.getBattleId()));

        // Only allow submissions while battle is active
        if (battle.getStatus() != BattleStatus.IN_PROGRESS) {
            throw new BattleNotActiveException(battle.getStatus());
        }

        Submission submission = Submission.builder()
                .battle(battle)
                .user(user)
                .language(request.getLanguage())
                .code(request.getCode())
                .status(SubmissionStatus.PENDING)
                .build();

        Submission saved = submissionRepository.save(submission);
        log.info("Submission {} created by user {} for battle {}", saved.getId(), username, battle.getId());

        // Notify battle participants of new submission entry
        broadcastStatus(saved);

        // Start async judging process
        processSubmissionAsync(saved.getId(), request);

        return toResponseDto(saved);
    }

    /**
     * Asynchronously processes the submission.
     * Updates status to RUNNING and eventually maps judging results to the entity.
     */
    @Async
    @Transactional
    public void processSubmissionAsync(UUID submissionId, SubmissionRequestDto request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));

        submission.setStatus(SubmissionStatus.RUNNING);
        submissionRepository.save(submission);
        broadcastStatus(submission);

        log.info("Processing submission {}: status set to RUNNING", submissionId);

        // TODO: Integrate DockerClientService for real-time code execution.
        // Once execution is complete, use the results to update the submission
        // and check for battle completion if the status is ACCEPTED.
    }

    /**
     * Poll status for a specific submission.
     */
    @Transactional(readOnly = true)
    public SubmissionResponseDto getSubmissionStatus(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        return toResponseDto(submission);
    }

    /**
     * Retrieves all submissions for a battle, ordered by submission time.
     */
    @Transactional(readOnly = true)
    public List<SubmissionResponseDto> getSubmissionsByBattle(UUID battleId) {
        return submissionRepository.findByBattleIdOrderBySubmittedAtDesc(battleId)
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all submissions for a user.
     */
    @Transactional(readOnly = true)
    public List<SubmissionResponseDto> getSubmissionsByUser(UUID userId) {
        return submissionRepository.findByUserIdOrderBySubmittedAtDesc(userId)
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves user submissions within a specific battle context.
     */
    @Transactional(readOnly = true)
    public List<SubmissionResponseDto> getSubmissionsByBattleAndUser(UUID battleId, UUID userId) {
        return submissionRepository.findByBattleIdAndUserId(battleId, userId)
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    // ── Helper Methods ──

    /**
     * Broadcasts the submission status update to all battle participants via
     * WebSocket.
     */
    private void broadcastStatus(Submission submission) {
        SubmissionResponseDto response = toResponseDto(submission);
        String destination = "/topic/battle/" + submission.getBattle().getId();
        messagingTemplate.convertAndSend(destination, response);
    }

    /**
     * Maps Submission entity to Response DTO.
     */
    private SubmissionResponseDto toResponseDto(Submission submission) {
        return SubmissionResponseDto.builder()
                .id(submission.getId())
                .battleId(submission.getBattle().getId())
                .userId(submission.getUser().getId())
                .username(submission.getUser().getUsername())
                .language(submission.getLanguage())
                .status(submission.getStatus())
                .testCasesPassed(submission.getTestCasesPassed() != null ? submission.getTestCasesPassed() : 0)
                .totalTestCases(submission.getTotalTestCases() != null ? submission.getTotalTestCases() : 0)
                .executionTimeMs(
                        submission.getExecutionTimeMs() != null ? submission.getExecutionTimeMs().longValue() : null)
                .memoryUsedKb(submission.getMemoryUsedKb() != null ? submission.getMemoryUsedKb().longValue() : null)
                .errorMessage(submission.getErrorMessage())
                .submittedAt(submission.getSubmittedAt())
                .judgedAt(submission.getJudgedAt())
                .build();
    }
}