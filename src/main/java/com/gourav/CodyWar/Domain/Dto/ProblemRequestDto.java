package com.gourav.CodyWar.Domain.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemRequestDto {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    private String description;

    @NotNull(message = "Difficulty level is required")
    private String difficulty;

    @NotBlank(message = "Problem statement is required")
    @Size(min = 20, message = "Problem statement must be at least 20 characters")
    private String problemStatement;

    private String constraints;

    private String examples;

    /**
     * Optional list of test cases to be created with the problem.
     * Each entry represents one test case (input, expectedOutput, isHidden, orderIndex).
     */
    @Valid
    private List<TestCaseDto> testCases;
}
