package com.gourav.CodyWar.Service;

import com.gourav.CodyWar.Domain.Dto.ProblemRequestDto;
import com.gourav.CodyWar.Domain.Dto.ProblemResponseDto;
import com.gourav.CodyWar.Domain.Entity.Problem;
import com.gourav.CodyWar.Domain.Entity.Difficulty;
import com.gourav.CodyWar.Repository.ProblemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemService {

    private final ProblemRepository problemRepository;

    /**
     * Create a new problem
     */
    public ProblemResponseDto createProblem(ProblemRequestDto requestDto) {
        log.info("Creating new problem with title: {}", requestDto.getTitle());

        Problem problem = Problem.builder()
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .difficulty(Difficulty.valueOf(requestDto.getDifficulty().toUpperCase()))
                .constraints(requestDto.getConstraints())
                .exampleInput(requestDto.getExamples())
                .exampleOutput(requestDto.getExamples())
                .build();

        Problem savedProblem = problemRepository.save(problem);
        log.info("Problem created successfully with ID: {}", savedProblem.getId());

        return mapToResponseDto(savedProblem);
    }

    /**
     * Get problem by ID
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
     * Get all problems
     */
    @Transactional(readOnly = true)
    public List<ProblemResponseDto> getAllProblems() {
        log.info("Fetching all problems");

        List<Problem> problems = problemRepository.findAll();
        log.info("Total problems found: {}", problems.size());

        return problems.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Update existing problem
     */
    public ProblemResponseDto updateProblem(UUID id, ProblemRequestDto requestDto) {
        log.info("Updating problem with ID: {}", id);

        Problem existingProblem = problemRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Problem not found with ID: {}", id);
                    return new RuntimeException("Problem not found with ID: " + id);
                });

        existingProblem.setTitle(requestDto.getTitle());
        existingProblem.setDescription(requestDto.getDescription());
        existingProblem.setDifficulty(Difficulty.valueOf(requestDto.getDifficulty().toUpperCase()));
        existingProblem.setConstraints(requestDto.getConstraints());
        existingProblem.setExampleInput(requestDto.getExamples());
        existingProblem.setExampleOutput(requestDto.getExamples());

        Problem updatedProblem = problemRepository.save(existingProblem);
        log.info("Problem updated successfully with ID: {}", id);

        return mapToResponseDto(updatedProblem);
    }

    /**
     * Delete problem by ID
     */
    public void deleteProblem(UUID id) {
        log.info("Deleting problem with ID: {}", id);

        if (!problemRepository.existsById(id)) {
            log.error("Problem not found with ID: {}", id);
            throw new RuntimeException("Problem not found with ID: " + id);
        }

        problemRepository.deleteById(id);
        log.info("Problem deleted successfully with ID: {}", id);
    }

    /**
     * Map Problem entity to ProblemResponseDto
     */
    private ProblemResponseDto mapToResponseDto(Problem problem) {
        return ProblemResponseDto.builder()
                .id(UUID.fromString(problem.getId().toString()))
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty().name())
                .problemStatement(problem.getDescription())
                .constraints(problem.getConstraints())
                .examples(problem.getExampleInput())
                .createdAt(convertInstantToLocalDateTime(problem.getCreatedAt()))
                .updatedAt(convertInstantToLocalDateTime(problem.getCreatedAt()))
                .createdBy(null)
                .totalSubmissions(0L)
                .acceptedSubmissions(0L)
                .acceptanceRate(0.0)
                .build();
    }

    /**
     * Convert Instant to LocalDateTime
     */
    private LocalDateTime convertInstantToLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                : null;
    }
}