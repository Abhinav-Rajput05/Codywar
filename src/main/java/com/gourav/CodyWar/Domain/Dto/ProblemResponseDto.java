package com.gourav.CodyWar.Domain.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
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
    private String difficulty;

    @JsonProperty("problemStatement")
    private String problemStatement;

    @JsonProperty("constraints")
    private String constraints;

    @JsonProperty("examples")
    private String examples;

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
}