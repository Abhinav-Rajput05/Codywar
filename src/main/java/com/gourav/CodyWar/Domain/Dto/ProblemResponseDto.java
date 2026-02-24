package com.gourav.CodyWar.Domain.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.gourav.CodyWar.Domain.Entity.Difficulty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemResponseDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("difficulty")
    private Difficulty difficulty;

    @JsonProperty("problemStatement")
    private String problemStatement;

    @JsonProperty("constraints")
    private String constraints;

    @JsonProperty("exampleInput")
    private String exampleInput;

    @JsonProperty("exampleOutput")
    private String exampleOutput;

    @JsonProperty("examples")
    private String examples;

    @JsonProperty("timeLimitSeconds")
    private Integer timeLimitSeconds;

    @JsonProperty("memoryLimitMb")
    private Integer memoryLimitMb;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("totalSubmissions")
    private Long totalSubmissions;

    @JsonProperty("acceptedSubmissions")
    private Long acceptedSubmissions;

    @JsonProperty("acceptanceRate")
    private Double acceptanceRate;

    /**
     * Non-hidden (sample) test cases visible to users.
     * Hidden test cases are excluded from this list for regular users.
     */
    @JsonProperty("testCases")
    private List<TestCaseDto> testCases;
}