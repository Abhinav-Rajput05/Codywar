package com.gourav.CodyWar.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gourav.CodyWar.Domain.Dto.*;
import com.gourav.CodyWar.Domain.Entity.*;
import com.gourav.CodyWar.Security.CustomUserDetails;
import com.gourav.CodyWar.Service.BattleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BattleController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security for testing
@DisplayName("Battle Controller Integration Tests")
class BattleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BattleService battleService;

    private CustomUserDetails testUserDetails;
    private User testUser;
    private BattleResponseDto battleResponse;
    private CreateBattleRequest createBattleRequest;
    private JoinBattleRequest joinBattleRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("testUser")
                .email("test@example.com")
                .ratingScore(1500)
                .role(Role.PLAYER)
                .build();

        testUserDetails = new CustomUserDetails(testUser);

        Problem problem = Problem.builder()
                .id(UUID.randomUUID())
                .title("Two Sum")
                .description("Find two numbers")
                .difficulty(Difficulty.EASY)
                .timeLimitSeconds(2)
                .memoryLimitMb(256)
                .build();

        ProblemResponseDto problemDto = ProblemResponseDto.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .timeLimitSeconds(problem.getTimeLimitSeconds())
                .memoryLimitMb(problem.getMemoryLimitMb())
                .build();

        battleResponse = BattleResponseDto.builder()
                .id(UUID.randomUUID())
                .roomCode("TEST1234")
                .status(BattleStatus.WAITING)
                .maxParticipants(2)
                .currentParticipants(1)
                .durationSeconds(1800)
                .isPrivate(false)
                .problem(problemDto)
                .participants(new ArrayList<>())
                .createdAt(Instant.now())
                .remainingTimeSeconds(1800L)
                .build();

        createBattleRequest = CreateBattleRequest.builder()
                .problemId(problem.getId())
                .isPrivate(false)
                .maxParticipants(2)
                .durationSeconds(1800)
                .build();

        joinBattleRequest = JoinBattleRequest.builder()
                .roomCode("TEST1234")
                .build();
    }

    // ==================== CREATE BATTLE TESTS ====================

    @Test
    @DisplayName("POST /api/battles - Should create battle successfully")
    void createBattle_Success() throws Exception {
        // Arrange
        when(battleService.createBattle(eq(testUser.getId()), any(CreateBattleRequest.class)))
                .thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(post("/api/battles")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBattleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Battle created successfully"))
                .andExpect(jsonPath("$.data.id").value(battleResponse.getId().toString()))
                .andExpect(jsonPath("$.data.roomCode").value("TEST1234"))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.maxParticipants").value(2))
                .andExpect(jsonPath("$.data.currentParticipants").value(1));

        verify(battleService).createBattle(eq(testUser.getId()), any(CreateBattleRequest.class));
    }

    @Test
    @DisplayName("POST /api/battles - Should validate request body")
    void createBattle_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Arrange
        CreateBattleRequest invalidRequest = CreateBattleRequest.builder()
                .maxParticipants(1) // Below minimum
                .durationSeconds(100) // Below minimum
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/battles")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ==================== JOIN BATTLE TESTS ====================

    @Test
    @DisplayName("POST /api/battles/join - Should join battle by room code successfully")
    void joinBattleByRoomCode_Success() throws Exception {
        // Arrange
        when(battleService.joinBattleByRoomCode(testUser.getId(), "TEST1234"))
                .thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(post("/api/battles/join")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinBattleRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Joined battle successfully"))
                .andExpect(jsonPath("$.data.roomCode").value("TEST1234"));

        verify(battleService).joinBattleByRoomCode(testUser.getId(), "TEST1234");
    }

    @Test
    @DisplayName("POST /api/battles/{battleId}/join - Should join battle by ID successfully")
    void joinBattleById_Success() throws Exception {
        // Arrange
        UUID battleId = battleResponse.getId();
        when(battleService.joinBattleById(testUser.getId(), battleId))
                .thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(post("/api/battles/{battleId}/join", battleId)
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(battleId.toString()));

        verify(battleService).joinBattleById(testUser.getId(), battleId);
    }

    @Test
    @DisplayName("POST /api/battles/join - Should validate room code is not blank")
    void joinBattleByRoomCode_BlankRoomCode_ReturnsBadRequest() throws Exception {
        // Arrange
        JoinBattleRequest invalidRequest = JoinBattleRequest.builder()
                .roomCode("")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/battles/join")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ==================== MATCHMAKING TESTS ====================

    @Test
    @DisplayName("POST /api/battles/matchmaking - Should find match successfully")
    void findMatch_MatchFound_ReturnsMatch() throws Exception {
        // Arrange
        when(battleService.findOrQueueMatch(testUser.getId()))
                .thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(post("/api/battles/matchmaking")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Match found!"))
                .andExpect(jsonPath("$.data.id").value(battleResponse.getId().toString()));

        verify(battleService).findOrQueueMatch(testUser.getId());
    }

    @Test
    @DisplayName("POST /api/battles/matchmaking - Should queue user when no match found")
    void findMatch_NoMatchFound_QueuesUser() throws Exception {
        // Arrange
        when(battleService.findOrQueueMatch(testUser.getId()))
                .thenReturn(null);

        // Act & Assert
        mockMvc.perform(post("/api/battles/matchmaking")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Added to matchmaking queue. Waiting for opponent..."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(battleService).findOrQueueMatch(testUser.getId());
    }

    @Test
    @DisplayName("DELETE /api/battles/matchmaking - Should cancel matchmaking successfully")
    void cancelMatchmaking_Success() throws Exception {
        // Arrange
        doNothing().when(battleService).cancelMatchmaking(testUser.getId());

        // Act & Assert
        mockMvc.perform(delete("/api/battles/matchmaking")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Removed from matchmaking queue"));

        verify(battleService).cancelMatchmaking(testUser.getId());
    }

    // ==================== PLAYER READY TESTS ====================

    @Test
    @DisplayName("POST /api/battles/{battleId}/ready - Should set player ready successfully")
    void setReady_Success() throws Exception {
        // Arrange
        UUID battleId = battleResponse.getId();
        when(battleService.setPlayerReady(testUser.getId(), battleId, true))
                .thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(post("/api/battles/{battleId}/ready", battleId)
                        .with(user(testUserDetails))
                        .param("ready", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Marked as ready"));

        verify(battleService).setPlayerReady(testUser.getId(), battleId, true);
    }

    @Test
    @DisplayName("POST /api/battles/{battleId}/ready - Should set player not ready")
    void setNotReady_Success() throws Exception {
        // Arrange
        UUID battleId = battleResponse.getId();
        when(battleService.setPlayerReady(testUser.getId(), battleId, false))
                .thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(post("/api/battles/{battleId}/ready", battleId)
                        .with(user(testUserDetails))
                        .param("ready", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Marked as not ready"));

        verify(battleService).setPlayerReady(testUser.getId(), battleId, false);
    }

    @Test
    @DisplayName("POST /api/battles/{battleId}/ready - Should default to ready=true")
    void setReady_DefaultValue_Success() throws Exception {
        // Arrange
        UUID battleId = battleResponse.getId();
        when(battleService.setPlayerReady(testUser.getId(), battleId, true))
                .thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(post("/api/battles/{battleId}/ready", battleId)
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(battleService).setPlayerReady(testUser.getId(), battleId, true);
    }

    // ==================== LEAVE BATTLE TESTS ====================

    @Test
    @DisplayName("POST /api/battles/{battleId}/leave - Should leave battle successfully")
    void leaveBattle_Success() throws Exception {
        // Arrange
        UUID battleId = battleResponse.getId();
        doNothing().when(battleService).leaveBattle(testUser.getId(), battleId);

        // Act & Assert
        mockMvc.perform(post("/api/battles/{battleId}/leave", battleId)
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Left battle successfully"));

        verify(battleService).leaveBattle(testUser.getId(), battleId);
    }

    // ==================== GET BATTLE TESTS ====================

    @Test
    @DisplayName("GET /api/battles/{battleId} - Should get battle details successfully")
    void getBattle_Success() throws Exception {
        // Arrange
        UUID battleId = battleResponse.getId();
        when(battleService.getBattle(battleId)).thenReturn(battleResponse);

        // Act & Assert
        mockMvc.perform(get("/api/battles/{battleId}", battleId)
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(battleId.toString()))
                .andExpect(jsonPath("$.data.roomCode").value("TEST1234"));

        verify(battleService).getBattle(battleId);
    }

    @Test
    @DisplayName("GET /api/battles/{battleId}/state - Should get battle state successfully")
    void getBattleState_Success() throws Exception {
        // Arrange
        UUID battleId = battleResponse.getId();
        BattleState battleState = BattleState.builder()
                .battleId(battleId)
                .roomCode("TEST1234")
                .status(BattleStatus.WAITING)
                .maxParticipants(2)
                .durationSeconds(1800)
                .participants(new HashSet<>())
                .build();

        when(battleService.getBattleState(battleId)).thenReturn(battleState);

        // Act & Assert
        mockMvc.perform(get("/api/battles/{battleId}/state", battleId)
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.battleId").value(battleId.toString()))
                .andExpect(jsonPath("$.data.roomCode").value("TEST1234"))
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        verify(battleService).getBattleState(battleId);
    }

    @Test
    @DisplayName("GET /api/battles/my-battles - Should get user's battle history")
    void getMyBattles_Success() throws Exception {
        // Arrange
        List<BattleResponseDto> battles = Arrays.asList(battleResponse);
        when(battleService.getUserBattles(testUser.getId())).thenReturn(battles);

        // Act & Assert
        mockMvc.perform(get("/api/battles/my-battles")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(battleResponse.getId().toString()));

        verify(battleService).getUserBattles(testUser.getId());
    }

    @Test
    @DisplayName("GET /api/battles/active - Should get active battles")
    void getActiveBattles_Success() throws Exception {
        // Arrange
        List<BattleResponseDto> battles = Arrays.asList(battleResponse);
        when(battleService.getActiveBattles()).thenReturn(battles);

        // Act & Assert
        mockMvc.perform(get("/api/battles/active")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("WAITING"));

        verify(battleService).getActiveBattles();
    }

    @Test
    @DisplayName("GET /api/battles/active - Should return empty list when no active battles")
    void getActiveBattles_NoBattles_ReturnsEmptyList() throws Exception {
        // Arrange
        when(battleService.getActiveBattles()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/battles/active")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(battleService).getActiveBattles();
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @DisplayName("Should handle service exceptions gracefully")
    void handleServiceException() throws Exception {
        // Arrange
        when(battleService.createBattle(eq(testUser.getId()), any(CreateBattleRequest.class)))
                .thenThrow(new IllegalStateException("User is already in an active battle"));

        // Act & Assert
        mockMvc.perform(post("/api/battles")
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBattleRequest)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }
}
