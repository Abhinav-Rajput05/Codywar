package com.gourav.CodyWar.Service;

import com.gourav.CodyWar.Domain.Dto.ProblemRequestDto;
import com.gourav.CodyWar.Domain.Dto.ProblemResponseDto;
import com.gourav.CodyWar.Domain.Dto.TestCaseDto;
import com.gourav.CodyWar.Domain.Entity.Difficulty;
import com.gourav.CodyWar.Domain.Entity.Problem;
import com.gourav.CodyWar.Domain.Entity.TestCase;
import com.gourav.CodyWar.Repository.ProblemRepository;
import com.gourav.CodyWar.Repository.TestCaseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    /**
     * Create a new problem, optionally with embedded test cases.
     */
    public ProblemResponseDto createProblem(ProblemRequestDto requestDto) {
        log.info("Creating new problem with title: {}", requestDto.getTitle());

        // Validate difficulty is provided
        if (requestDto.getDifficulty() == null || requestDto.getDifficulty().isBlank()) {
            throw new IllegalArgumentException("Difficulty is required");
        }

        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(requestDto.getDifficulty().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid difficulty: " + requestDto.getDifficulty() + 
                ". Valid values are: EASY, MEDIUM, HARD");
        }

        Problem problem = Problem.builder()
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .difficulty(difficulty)
                .constraints(requestDto.getConstraints())
                .exampleInput(requestDto.getExamples())
                .exampleOutput(requestDto.getExamples())
                .timeLimitSeconds(5)
                .memoryLimitMb(256)
                .build();

        Problem savedProblem = problemRepository.save(problem);
        log.info("Problem created successfully with ID: {}", savedProblem.getId());

        // Persist test cases if provided
        if (requestDto.getTestCases() != null && !requestDto.getTestCases().isEmpty()) {
            saveTestCases(savedProblem, requestDto.getTestCases());
            log.info("Saved {} test cases for problem ID: {}", requestDto.getTestCases().size(), savedProblem.getId());
        }

        return mapToResponseDto(savedProblem);
    }

    /**
     * Get problem by ID (returns only non-hidden test cases).
     */
    @Transactional(readOnly = true)
    public ProblemResponseDto getProblemById(UUID id) {
        log.info("Fetching problem with ID: {}", id);

        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Problem not found with ID: {}", id);
                    return new EntityNotFoundException("Problem not found with ID: " + id);
                });

        return mapToResponseDto(problem);
    }

    /**
     * Get all problems.
     */
    @Transactional(readOnly = true)
    public List<ProblemResponseDto> getAllProblems() {
        log.info("Fetching all problems");

        List<Problem> problems = problemRepository.findAll();
        log.info("Total problems found: {}", problems.size());

        if (problems.isEmpty()) {
            return List.of();
        }

        // Collect all problem IDs
        List<UUID> problemIds = problems.stream()
                .map(Problem::getId)
                .collect(Collectors.toList());

        // Fetch all visible test cases for all problems in a single query
        List<TestCase> allVisibleTestCases = testCaseRepository.findVisibleTestCasesByProblemIdIn(problemIds);

        // Group test cases by problem ID
        Map<UUID, List<TestCaseDto>> testCasesByProblemId = allVisibleTestCases.stream()
                .collect(Collectors.groupingBy(
                        tc -> tc.getProblem().getId(),
                        Collectors.mapping(
                                tc -> TestCaseDto.builder()
                                        .id(tc.getId())
                                        .input(tc.getInput())
                                        .expectedOutput(tc.getExpectedOutput())
                                        .isHidden(tc.isHidden())
                                        .orderIndex(tc.getOrderIndex())
                                        .build(),
                                Collectors.toList())));

        // Build response DTOs
        return problems.stream()
                .map(problem -> {
                    ProblemResponseDto dto = mapToResponseDtoWithoutTestCases(problem);
                    dto.setTestCases(testCasesByProblemId.getOrDefault(problem.getId(), List.of()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Map Problem entity to ProblemResponseDto without test cases.
     * Used by getAllProblems to avoid duplicate test case queries.
     */
    private ProblemResponseDto mapToResponseDtoWithoutTestCases(Problem problem) {
        return ProblemResponseDto.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .problemStatement(problem.getDescription())
                .constraints(problem.getConstraints())
                .exampleInput(problem.getExampleInput())
                .exampleOutput(problem.getExampleOutput())
                .examples(problem.getExampleInput())
                .timeLimitSeconds(problem.getTimeLimitSeconds())
                .memoryLimitMb(problem.getMemoryLimitMb())
                .createdAt(convertInstantToLocalDateTime(problem.getCreatedAt()))
                .updatedAt(convertInstantToLocalDateTime(problem.getCreatedAt()))
                .createdBy(null)
                .totalSubmissions(0L)
                .acceptedSubmissions(0L)
                .acceptanceRate(0.0)
                .testCases(List.of())
                .build();
    }

    /**
     * Update existing problem, replacing all test cases with the new set.
     */
    public ProblemResponseDto updateProblem(UUID id, ProblemRequestDto requestDto) {
        log.info("Updating problem with ID: {}", id);

        Problem existingProblem = problemRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Problem not found with ID: {}", id);
                    return new EntityNotFoundException("Problem not found with ID: " + id);
                });

        // Validate difficulty
        if (requestDto.getDifficulty() == null || requestDto.getDifficulty().isBlank()) {
            throw new IllegalArgumentException("Difficulty is required");
        }

        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(requestDto.getDifficulty().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid difficulty: " + requestDto.getDifficulty() + 
                ". Valid values are: EASY, MEDIUM, HARD");
        }

        existingProblem.setTitle(requestDto.getTitle());
        existingProblem.setDescription(requestDto.getDescription());
        existingProblem.setDifficulty(difficulty);
        existingProblem.setConstraints(requestDto.getConstraints());
        existingProblem.setExampleInput(requestDto.getExamples());
        existingProblem.setExampleOutput(requestDto.getExamples());

        // Update test cases if provided - clear existing and add new ones
        if (requestDto.getTestCases() != null) {
            // Clear existing test cases (orphanRemoval will handle deletion)
            existingProblem.getTestCases().clear();
            
            // Add new test cases
            for (int i = 0; i < requestDto.getTestCases().size(); i++) {
                TestCaseDto dto = requestDto.getTestCases().get(i);
                TestCase testCase = TestCase.builder()
                        .problem(existingProblem)
                        .input(dto.getInput())
                        .expectedOutput(dto.getExpectedOutput())
                        .isHidden(dto.isHidden())
                        .orderIndex(i)
                        .build();
                existingProblem.getTestCases().add(testCase);
            }
            log.info("Updated {} test cases for problem ID: {}", requestDto.getTestCases().size(), id);
        }

        Problem updatedProblem = problemRepository.save(existingProblem);
        log.info("Problem updated successfully with ID: {}", id);

        return mapToResponseDto(updatedProblem);
    }

    /**
     * Delete problem by ID.
     */
    public void deleteProblem(UUID id) {
        log.info("Deleting problem with ID: {}", id);

        if (!problemRepository.existsById(id)) {
            log.error("Problem not found with ID: {}", id);
            throw new EntityNotFoundException("Problem not found with ID: " + id);
        }

        problemRepository.deleteById(id);
        log.info("Problem deleted successfully with ID: {}", id);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Persist a list of TestCaseDto entries as TestCase entities linked to the given problem.
     * orderIndex is assigned incrementally starting from 0.
     */
    private void saveTestCases(Problem problem, List<TestCaseDto> testCaseDtos) {
        List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < testCaseDtos.size(); i++) {
            TestCaseDto dto = testCaseDtos.get(i);
            TestCase testCase = TestCase.builder()
                    .problem(problem)
                    .input(dto.getInput())
                    .expectedOutput(dto.getExpectedOutput())
                    .isHidden(dto.isHidden())
                    .orderIndex(i)
                    .build();
            testCases.add(testCase);
        }
        testCaseRepository.saveAll(testCases);
    }

    /**
     * Map Problem entity to ProblemResponseDto.
     * Only non-hidden test cases are included (visible to regular users).
     */
    private ProblemResponseDto mapToResponseDto(Problem problem) {
        // Fetch visible test cases for this problem
        List<TestCase> visibleTestCases =
                testCaseRepository.findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(problem.getId());

        List<TestCaseDto> testCaseDtos = visibleTestCases.stream()
                .map(tc -> TestCaseDto.builder()
                        .id(tc.getId())
                        .input(tc.getInput())
                        .expectedOutput(tc.getExpectedOutput())
                        .isHidden(tc.isHidden())
                        .orderIndex(tc.getOrderIndex())
                        .build())
                .collect(Collectors.toList());

        return ProblemResponseDto.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .problemStatement(problem.getDescription())
                .constraints(problem.getConstraints())
                .exampleInput(problem.getExampleInput())
                .exampleOutput(problem.getExampleOutput())
                .examples(problem.getExampleInput())
                .timeLimitSeconds(problem.getTimeLimitSeconds())
                .memoryLimitMb(problem.getMemoryLimitMb())
                .createdAt(convertInstantToLocalDateTime(problem.getCreatedAt()))
                .updatedAt(convertInstantToLocalDateTime(problem.getCreatedAt()))
                .createdBy(null)
                .totalSubmissions(0L)
                .acceptedSubmissions(0L)
                .acceptanceRate(0.0)
                .testCases(testCaseDtos)
                .build();
    }

    /**
     * Convert Instant to LocalDateTime using the system default time zone.
     */
    private LocalDateTime convertInstantToLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                : null;
    }
}
