package com.gourav.CodyWar.Service;

import com.gourav.CodyWar.Domain.Dto.*;
import com.gourav.CodyWar.Domain.Entity.*;
import com.gourav.CodyWar.Repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Battle Service Tests")
class BattleServiceTest {

    @Mock
    private BattleRepository battleRepository;

    @Mock
    private BattleParticipantRepository participantRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BattleService battleService;

    private User testUser1;
    private User testUser2;
    private Battle testBattle;
    private Problem testProblem;
    private BattleState testBattleState;
    private BattleParticipant testParticipant;

    @BeforeEach
    void setUp() {
        // Set configuration values using reflection
        ReflectionTestUtils.setField(battleService, "defaultDurationSeconds", 1800);
        ReflectionTestUtils.setField(battleService, "defaultMaxParticipants", 2);
        ReflectionTestUtils.setField(battleService, "queueTimeoutSeconds", 300);

        // Create test user 1
        testUser1 = User.builder()
                .id(UUID.randomUUID())
                .username("testUser1")
                .email("test1@example.com")
                .ratingScore(1500)
                .battlesPlayed(10)
                .battlesWon(5)
                .build();

        // Create test user 2
        testUser2 = User.builder()
                .id(UUID.randomUUID())
                .username("testUser2")
                .email("test2@example.com")
                .ratingScore(1550)
                .battlesPlayed(8)
                .battlesWon(4)
                .build();

        // Create test problem
        testProblem = Problem.builder()
                .id(UUID.randomUUID())
                .title("Two Sum")
                .description("Find two numbers that add up to target")
                .difficulty(Difficulty.MEDIUM)
                .timeLimitSeconds(2)
                .memoryLimitMb(256)
                .constraints("1 <= nums.length <= 10^4")
                .exampleInput("[2,7,11,15], target = 9")
                .exampleOutput("[0,1]")
                .createdAt(Instant.now())
                .build();

        // Create test battle
        testBattle = Battle.builder()
                .id(UUID.randomUUID())
                .roomCode("TEST1234")
                .problem(testProblem)
                .status(BattleStatus.WAITING)
                .maxParticipants(2)
                .durationSeconds(1800)
                .isPrivate(false)
                .participants(new HashSet<>())
                .submissions(new HashSet<>())
                .createdAt(Instant.now())
                .build();

        // Create test participant
        testParticipant = BattleParticipant.builder()
                .id(UUID.randomUUID())
                .battle(testBattle)
                .user(testUser1)
                .isReady(false)
                .hasSubmitted(false)
                .score(0)
                .joinedAt(Instant.now())
                .build();

        // Create test battle state
        BattleState.ParticipantState participantState = BattleState.ParticipantState.builder()
                .oduserId(testUser1.getId())
                .username(testUser1.getUsername())
                .isReady(false)
                .hasSubmitted(false)
                .score(0)
                .joinedAt(Instant.now())
                .build();

        testBattleState = BattleState.builder()
                .battleId(testBattle.getId())
                .roomCode(testBattle.getRoomCode())
                .problemId(testProblem.getId())
                .status(BattleStatus.WAITING)
                .maxParticipants(2)
                .durationSeconds(1800)
                .isPrivate(false)
                .participants(new HashSet<>(Collections.singletonList(participantState)))
                .createdAt(Instant.now())
                .build();
    }

    // ==================== CREATE BATTLE TESTS ====================

    @Test
    @DisplayName("Should create a public battle successfully")
    void createBattle_PublicBattle_Success() {
        // Arrange
        CreateBattleRequest request = CreateBattleRequest.builder()
                .problemId(testProblem.getId())
                .isPrivate(false)
                .maxParticipants(2)
                .durationSeconds(1800)
                .build();

        when(userRepository.findById(testUser1.getId())).thenReturn(Optional.of(testUser1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser1.getId())).thenReturn(null);
        when(problemRepository.findById(testProblem.getId())).thenReturn(Optional.of(testProblem));
        when(battleRepository.save(any(Battle.class))).thenReturn(testBattle);
        when(participantRepository.save(any(BattleParticipant.class))).thenReturn(testParticipant);

        // Act
        BattleResponseDto result = battleService.createBattle(testUser1.getId(), request);

        // Assert
        assertNotNull(result);
        assertEquals(testBattle.getId(), result.getId());
        verify(battleRepository).save(any(Battle.class));
        verify(participantRepository).save(any(BattleParticipant.class));
        verify(valueOperations).set(eq("battle:state:" + testBattle.getId()), any(BattleState.class), anyLong(), any());
        verify(valueOperations).set(eq("user:battle:" + testUser1.getId()), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Should create a private battle with room code")
    void createBattle_PrivateBattle_Success() {
        // Arrange
        CreateBattleRequest request = CreateBattleRequest.builder()
                .problemId(testProblem.getId())
                .isPrivate(true)
                .maxParticipants(2)
                .durationSeconds(1800)
                .build();

        testBattle.setPrivate(true);
        testBattle.setRoomCode("PRIV1234");

        when(userRepository.findById(testUser1.getId())).thenReturn(Optional.of(testUser1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser1.getId())).thenReturn(null);
        when(problemRepository.findById(testProblem.getId())).thenReturn(Optional.of(testProblem));
        when(battleRepository.existsByRoomCode(anyString())).thenReturn(false);
        when(battleRepository.save(any(Battle.class))).thenReturn(testBattle);
        when(participantRepository.save(any(BattleParticipant.class))).thenReturn(testParticipant);

        // Act
        BattleResponseDto result = battleService.createBattle(testUser1.getId(), request);

        // Assert
        assertNotNull(result);
        assertTrue(result.isPrivate());
        assertNotNull(result.getRoomCode());
        verify(battleRepository).save(any(Battle.class));
    }

    @Test
    @DisplayName("Should throw exception when user not found during battle creation")
    void createBattle_UserNotFound_ThrowsException() {
        // Arrange
        CreateBattleRequest request = CreateBattleRequest.builder()
                .isPrivate(false)
                .maxParticipants(2)
                .durationSeconds(1800)
                .build();

        when(userRepository.findById(testUser1.getId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
                () -> battleService.createBattle(testUser1.getId(), request));
    }

    @Test
    @DisplayName("Should throw exception when user is already in active battle")
    void createBattle_UserAlreadyInBattle_ThrowsException() {
        // Arrange
        CreateBattleRequest request = CreateBattleRequest.builder()
                .isPrivate(false)
                .maxParticipants(2)
                .durationSeconds(1800)
                .build();

        when(userRepository.findById(testUser1.getId())).thenReturn(Optional.of(testUser1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser1.getId())).thenReturn(UUID.randomUUID().toString());

        // Act & Assert
        assertThrows(IllegalStateException.class, 
                () -> battleService.createBattle(testUser1.getId(), request));
    }

    // ==================== JOIN BATTLE TESTS ====================

    @Test
    @DisplayName("Should join battle by room code successfully")
    void joinBattleByRoomCode_Success() {
        // Arrange
        when(userRepository.findById(testUser2.getId())).thenReturn(Optional.of(testUser2));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser2.getId())).thenReturn(null);
        when(battleRepository.findByRoomCode("TEST1234")).thenReturn(Optional.of(testBattle));
        when(participantRepository.existsByBattleIdAndUserId(testBattle.getId(), testUser2.getId())).thenReturn(false);
        when(participantRepository.save(any(BattleParticipant.class))).thenReturn(testParticipant);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        BattleResponseDto result = battleService.joinBattleByRoomCode(testUser2.getId(), "TEST1234");

        // Assert
        assertNotNull(result);
        assertEquals(testBattle.getId(), result.getId());
        verify(participantRepository).save(any(BattleParticipant.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/battle/" + testBattle.getId()), any(BattleEvent.class));
    }

    @Test
    @DisplayName("Should join battle by ID successfully")
    void joinBattleById_Success() {
        // Arrange
        when(userRepository.findById(testUser2.getId())).thenReturn(Optional.of(testUser2));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser2.getId())).thenReturn(null);
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));
        when(participantRepository.existsByBattleIdAndUserId(testBattle.getId(), testUser2.getId())).thenReturn(false);
        when(participantRepository.save(any(BattleParticipant.class))).thenReturn(testParticipant);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        BattleResponseDto result = battleService.joinBattleById(testUser2.getId(), testBattle.getId());

        // Assert
        assertNotNull(result);
        assertEquals(testBattle.getId(), result.getId());
    }

    @Test
    @DisplayName("Should throw exception when joining battle that is not in WAITING status")
    void joinBattle_NotWaitingStatus_ThrowsException() {
        // Arrange
        testBattle.setStatus(BattleStatus.IN_PROGRESS);
        when(userRepository.findById(testUser2.getId())).thenReturn(Optional.of(testUser2));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser2.getId())).thenReturn(null);
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> battleService.joinBattleById(testUser2.getId(), testBattle.getId()));
        assertEquals("Battle is not accepting new players", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when joining a full battle")
    void joinBattle_BattleFull_ThrowsException() {
        // Arrange
        BattleParticipant participant1 = BattleParticipant.builder()
                .id(UUID.randomUUID())
                .battle(testBattle)
                .user(testUser1)
                .build();
        BattleParticipant participant2 = BattleParticipant.builder()
                .id(UUID.randomUUID())
                .battle(testBattle)
                .user(testUser2)
                .build();
        
        testBattle.getParticipants().add(participant1);
        testBattle.getParticipants().add(participant2);

        User testUser3 = User.builder().id(UUID.randomUUID()).username("testUser3").build();

        when(userRepository.findById(testUser3.getId())).thenReturn(Optional.of(testUser3));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser3.getId())).thenReturn(null);
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> battleService.joinBattleById(testUser3.getId(), testBattle.getId()));
        assertEquals("Battle is full", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when user is already in the battle")
    void joinBattle_UserAlreadyInBattle_ThrowsException() {
        // Arrange
        when(userRepository.findById(testUser1.getId())).thenReturn(Optional.of(testUser1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser1.getId())).thenReturn(null);
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));
        when(participantRepository.existsByBattleIdAndUserId(testBattle.getId(), testUser1.getId())).thenReturn(true);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> battleService.joinBattleById(testUser1.getId(), testBattle.getId()));
        assertEquals("User is already in this battle", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when room code not found")
    void joinBattleByRoomCode_InvalidRoomCode_ThrowsException() {
        // Arrange
        when(userRepository.findById(testUser1.getId())).thenReturn(Optional.of(testUser1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser1.getId())).thenReturn(null);
        when(battleRepository.findByRoomCode("INVALID")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> battleService.joinBattleByRoomCode(testUser1.getId(), "INVALID"));
        assertEquals("Battle not found with room code: INVALID", exception.getMessage());
    }

    // ==================== MATCHMAKING TESTS ====================

    @Test
    @DisplayName("Should queue user when no match found")
    void findOrQueueMatch_NoMatchAvailable_QueuesUser() {
        // Arrange
        when(userRepository.findById(testUser1.getId())).thenReturn(Optional.of(testUser1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:battle:" + testUser1.getId())).thenReturn(null);
        when(battleRepository.findAvailablePublicBattles(BattleStatus.WAITING)).thenReturn(Collections.emptyList());
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(eq("matchmaking:queue"), eq(0L), eq(-1L))).thenReturn(Collections.emptyList());

        // Act
        BattleResponseDto result = battleService.findOrQueueMatch(testUser1.getId());

        // Assert
        assertNull(result);
        verify(listOperations).rightPush(eq("matchmaking:queue"), any(MatchmakingEntry.class));
    }

    @Test
    @DisplayName("Should cancel matchmaking successfully")
    void cancelMatchmaking_Success() {
        // Arrange
        MatchmakingEntry entry = MatchmakingEntry.builder()
                .oduserId(testUser1.getId())
                .username(testUser1.getUsername())
                .ratingScore(testUser1.getRatingScore())
                .queuedAt(Instant.now())
                .build();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(eq("matchmaking:queue"), eq(0L), eq(-1L)))
                .thenReturn(Collections.singletonList(entry));

        // Act
        battleService.cancelMatchmaking(testUser1.getId());

        // Assert
        verify(listOperations).remove(eq("matchmaking:queue"), eq(1L), eq(entry));
    }

    // ==================== PLAYER READY TESTS ====================

    @Test
    @DisplayName("Should set player ready status successfully")
    void setPlayerReady_Success() {
        // Arrange
        testBattle.getParticipants().add(testParticipant);
        
        when(participantRepository.findByBattleIdAndUserId(testBattle.getId(), testUser1.getId()))
                .thenReturn(Optional.of(testParticipant));
        when(participantRepository.save(any(BattleParticipant.class))).thenReturn(testParticipant);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        BattleResponseDto result = battleService.setPlayerReady(testUser1.getId(), testBattle.getId(), true);

        // Assert
        assertNotNull(result);
        verify(participantRepository).save(argThat(participant -> participant.isReady()));
        verify(messagingTemplate).convertAndSend(eq("/topic/battle/" + testBattle.getId()), any(BattleEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when setting ready on non-waiting battle")
    void setPlayerReady_BattleNotWaiting_ThrowsException() {
        // Arrange
        testBattle.setStatus(BattleStatus.IN_PROGRESS);
        testBattle.getParticipants().add(testParticipant);
        
        when(participantRepository.findByBattleIdAndUserId(testBattle.getId(), testUser1.getId()))
                .thenReturn(Optional.of(testParticipant));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> battleService.setPlayerReady(testUser1.getId(), testBattle.getId(), true));
        assertTrue(exception.getMessage().contains("not in waiting state"));
    }

    @Test
    @DisplayName("Should throw exception when participant not found")
    void setPlayerReady_ParticipantNotFound_ThrowsException() {
        // Arrange
        when(participantRepository.findByBattleIdAndUserId(testBattle.getId(), testUser1.getId()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> battleService.setPlayerReady(testUser1.getId(), testBattle.getId(), true));
    }

    // ==================== START BATTLE TESTS ====================

    @Test
    @DisplayName("Should start battle successfully")
    void startBattle_Success() {
        // Arrange
        testBattle.getParticipants().add(testParticipant);
        BattleParticipant participant2 = BattleParticipant.builder()
                .id(UUID.randomUUID())
                .battle(testBattle)
                .user(testUser2)
                .isReady(true)
                .build();
        testBattle.getParticipants().add(participant2);

        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));
        when(battleRepository.save(any(Battle.class))).thenReturn(testBattle);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        battleService.startBattle(testBattle.getId());

        // Assert
        verify(battleRepository).save(argThat(battle -> 
                battle.getStatus() == BattleStatus.IN_PROGRESS && battle.getStartedAt() != null
        ));
        verify(messagingTemplate, atLeast(2)).convertAndSend(
                eq("/topic/battle/" + testBattle.getId()), 
                any(BattleEvent.class)
        );
    }

    @Test
    @DisplayName("Should throw exception when starting battle with not enough participants")
    void startBattle_NotEnoughParticipants_ThrowsException() {
        // Arrange
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> battleService.startBattle(testBattle.getId()));
        assertEquals("Not enough participants to start battle", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when starting battle not in waiting state")
    void startBattle_NotInWaitingState_ThrowsException() {
        // Arrange
        testBattle.setStatus(BattleStatus.IN_PROGRESS);
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> battleService.startBattle(testBattle.getId()));
        assertEquals("Battle cannot be started - not in waiting state", exception.getMessage());
    }

    // ==================== LEAVE BATTLE TESTS ====================

    @Test
    @DisplayName("Should leave battle successfully")
    void leaveBattle_Success() {
        // Arrange
        testBattle.getParticipants().add(testParticipant);
        
        when(participantRepository.findByBattleIdAndUserId(testBattle.getId(), testUser1.getId()))
                .thenReturn(Optional.of(testParticipant));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        // Act
        battleService.leaveBattle(testUser1.getId(), testBattle.getId());

        // Assert
        verify(participantRepository).delete(testParticipant);
        verify(redisTemplate).delete("user:battle:" + testUser1.getId());
        verify(messagingTemplate).convertAndSend(eq("/topic/battle/" + testBattle.getId()), any(BattleEvent.class));
    }@Test

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        battleService.endBattle(testBattle.getId(), testUser1.getId());

        // Assert
        verify(battleRepository).save(argThat(battle -> 
                battle.getStatus() == BattleStatus.COMPLETED && 
                battle.getWinner() != null &&
                battle.getFinishedAt() != null
        ));
        verify(userRepository).save(argThat(user -> 
                user.getId().equals(testUser1.getId()) && user.getBattlesWon() == 6
        ));
        verify(messagingTemplate).convertAndSend(eq("/topic/battle/" + testBattle.getId()), any(BattleEvent.class));
    }

    @Test
    @DisplayName("Should end battle without winner (draw)")
    void endBattle_WithoutWinner_Success() {
        // Arrange
        testBattle.getParticipants().add(testParticipant);

        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));
        when(battleRepository.save(any(Battle.class))).thenReturn(testBattle);
        when(userRepository.save(any(User.class))).thenReturn(testUser1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        battleService.endBattle(testBattle.getId(), null);

        // Assert
        verify(battleRepository).save(argThat(battle -> 
                battle.getStatus() == BattleStatus.COMPLETED && battle.getWinner() == null
        ));
    }

    // ==================== GET BATTLE TESTS ====================

    @Test
    @DisplayName("Should get battle details successfully")
    void getBattle_Success() {
        // Arrange
        testBattle.getParticipants().add(testParticipant);
        
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        BattleResponseDto result = battleService.getBattle(testBattle.getId());

        // Assert
        assertNotNull(result);
        assertEquals(testBattle.getId(), result.getId());
        assertEquals(testBattle.getRoomCode(), result.getRoomCode());
        assertEquals(testBattle.getStatus(), result.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when getting non-existent battle")
    void getBattle_NotFound_ThrowsException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(battleRepository.findById(invalidId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> battleService.getBattle(invalidId));
    }

    @Test
    @DisplayName("Should get battle state from Redis")
    void getBattleState_Success() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("battle:state:" + testBattle.getId())).thenReturn(testBattleState);

        // Act
        BattleState result = battleService.getBattleState(testBattle.getId());

        // Assert
        assertNotNull(result);
        assertEquals(testBattleState.getBattleId(), result.getBattleId());
        assertEquals(testBattleState.getRoomCode(), result.getRoomCode());
    }

    @Test
    @DisplayName("Should get user battles successfully")
    void getUserBattles_Success() {
        // Arrange
        List<Battle> battles = Arrays.asList(testBattle);
        when(battleRepository.findByUserIdOrderByCreatedAtDesc(testUser1.getId())).thenReturn(battles);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(testBattleState);

        // Act
        List<BattleResponseDto> result = battleService.getUserBattles(testUser1.getId());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should get active battles successfully")
    void getActiveBattles_Success() {
        // Arrange
        List<Battle> battles = Arrays.asList(testBattle);
        when(battleRepository.findByStatusIn(anyList())).thenReturn(battles);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(testBattleState);

        // Act
        List<BattleResponseDto> result = battleService.getActiveBattles();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ==================== CANCEL BATTLE TESTS ====================

    @Test
    @DisplayName("Should cancel battle successfully")
    void cancelBattle_Success() {
        // Arrange
        testBattle.getParticipants().add(testParticipant);
        
        when(battleRepository.findById(testBattle.getId())).thenReturn(Optional.of(testBattle));
        when(battleRepository.save(any(Battle.class))).thenReturn(testBattle);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        battleService.cancelBattle(testBattle.getId());

        // Assert
        verify(battleRepository).save(argThat(battle -> 
                battle.getStatus() == BattleStatus.CANCELLED && battle.getFinishedAt() != null
        ));
        verify(redisTemplate).delete("user:battle:" + testUser1.getId());
        verify(redisTemplate).delete("battle:state:" + testBattle.getId());
        verify(messagingTemplate).convertAndSend(eq("/topic/battle/" + testBattle.getId()), any(BattleEvent.class));
    }
}
