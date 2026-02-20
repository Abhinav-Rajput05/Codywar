package com.gourav.CodyWar.Controller;

import com.gourav.CodyWar.Domain.Dto.ProblemRequestDto;
import com.gourav.CodyWar.Domain.Dto.ProblemResponseDto;
import com.gourav.CodyWar.Service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
@Validated
public class ProblemController {

    private final ProblemService problemService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemResponseDto> createProblem(
            @Valid @RequestBody ProblemRequestDto requestDto) {
        log.info("Creating new problem with title: {}", requestDto.getTitle());
        ProblemResponseDto createdProblem = problemService.createProblem(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProblem);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemResponseDto> getProblemById(
            @PathVariable UUID id) {
        log.info("Fetching problem with ID: {}", id);
        ProblemResponseDto problem = problemService.getProblemById(id);
        return ResponseEntity.ok(problem);
    }

    @GetMapping
    public ResponseEntity<List<ProblemResponseDto>> getAllProblems() {
        log.info("Fetching all problems");
        List<ProblemResponseDto> problems = problemService.getAllProblems();
        return ResponseEntity.ok(problems);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemResponseDto> updateProblem(
            @PathVariable UUID id,
            @Valid @RequestBody ProblemRequestDto requestDto) {
        log.info("Updating problem with ID: {}", id);
        ProblemResponseDto updatedProblem = problemService.updateProblem(id, requestDto);
        return ResponseEntity.ok(updatedProblem);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteProblem(
            @PathVariable UUID id) {
        log.info("Deleting problem with ID: {}", id);
        problemService.deleteProblem(id);
        return ResponseEntity.ok("Problem deleted successfully");
    }
}