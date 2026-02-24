package com.gourav.CodyWar.Service;

import com.gourav.CodyWar.Domain.Dto.*;
import com.gourav.CodyWar.Domain.Entity.*;
import com.gourav.CodyWar.Repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BattleService {

    private final BattleRepository battleRepository;
    private final BattleParticipantRepository participantRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    // Redis key prefixes
    private static final String BATTLE_STATE_PREFIX = "battle:state:";
    private static final String MATCHMAKING_QUEUE_KEY = "matchmaking:queue";
    private static final String USER_ACTIVE_BATTLE_PREFIX = "user:battle:";
    private static final String ROOM_CODE_PREFIX = "room:";

    @Value("${battle.default.duration-seconds:1800}")
    private int defaultDurationSeconds;

    @Value("${battle.default.max-participants:2}")
    private int defaultMaxParticipants;

    @Value("${battle.matchmaking.queue-timeout-seconds:300}")
    private int queueTimeoutSeconds;

    // ==================== BATTLE CREATION ====================

    @Transactional
    public BattleResponseDto createBattle(UUID userId, CreateBattleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if user is already in an active battle
        checkUserNotInActiveBattle(userId);

        // Get or assign a random problem
        Problem problem = getProblemForBattle(request.getProblemId());

        // Generate unique room code for private battles
        String roomCode = request.isPrivate() ? generateUniqueRoomCode() : null;

        // Create battle entity
        Battle battle = Battle.builder()
                .roomCode(roomCode)
                .problem(problem)
                .status(BattleStatus.WAITING)
                .maxParticipants(request.getMaxParticipants())
                .durationSeconds(request.getDurationSeconds())
                .isPrivate(request.isPrivate())
                .build();

        battle = battleRepository.save(battle);

        // Add creator as first participant
        BattleParticipant participant = BattleParticipant.builder()
                .battle(battle)
                .user(user)
                .isReady(false)
                .build();

        participantRepository.save(participant);
        battle.getParticipants().add(participant);

        // Store battle state in Redis for real-time access
        BattleState battleState = createBattleState(battle, participant, user);
        saveBattleStateToRedis(battleState);

        // Track user's active battle
        trackUserActiveBattle(userId, battle.getId());

        log.info("Battle created: {} by user: {}", battle.getId(), userId);

        return mapToBattleResponseDto(battle, battleState);
    }

    // ==================== JOIN BATTLE ====================

    @Transactional
    public BattleResponseDto joinBattleByRoomCode(UUID userId, String roomCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        checkUserNotInActiveBattle(userId);

        Battle battle = battleRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Battle not found with room code: " + roomCode));

        return joinBattle(user, battle);
    }

    @Transactional
    public BattleResponseDto joinBattleById(UUID userId, UUID battleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        checkUserNotInActiveBattle(userId);

        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("Battle not found"));

        return joinBattle(user, battle);
    }

    private BattleResponseDto joinBattle(User user, Battle battle) {
        // Validate battle state
        if (battle.getStatus() != BattleStatus.WAITING) {
            throw new IllegalStateException("Battle is not accepting new players");
        }

        if (battle.getParticipants().size() >= battle.getMaxParticipants()) {
            throw new IllegalStateException("Battle is full");
        }

        // Check if user is already in this battle
        if (participantRepository.existsByBattleIdAndUserId(battle.getId(), user.getId())) {
            throw new IllegalStateException("User is already in this battle");
        }

        // Add participant
        BattleParticipant participant = BattleParticipant.builder()
                .battle(battle)
                .user(user)
                .isReady(false)
                .build();

        participantRepository.save(participant);
        battle.getParticipants().add(participant);

        // Update Redis state
        BattleState battleState = getBattleStateFromRedis(battle.getId());
        if (battleState != null) {
            BattleState.ParticipantState participantState = BattleState.ParticipantState.builder()
                    .oduserId(user.getId())
                    .username(user.getUsername())
                    .isReady(false)
                    .hasSubmitted(false)
                    .score(0)
                    .joinedAt(Instant.now())
                    .build();
            battleState.getParticipants().add(participantState);
            saveBattleStateToRedis(battleState);
        }

        // Track user's active battle
        trackUserActiveBattle(user.getId(), battle.getId());

        // Broadcast player joined event
        broadcastBattleEvent(battle.getId(), BattleEvent.builder()
                .type(BattleEvent.EventType.PLAYER_JOINED)
                .battleId(battle.getId())
                .roomCode(battle.getRoomCode())
                .payload(Map.of(
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "currentParticipants", battle.getParticipants().size()
                ))
                .build());

        log.info("User {} joined battle {}", user.getId(), battle.getId());

        return mapToBattleResponseDto(battle, battleState);
    }

    // ==================== MATCHMAKING ====================

    @Transactional
    public BattleResponseDto findOrQueueMatch(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        checkUserNotInActiveBattle(userId);

        // First, try to find an available public battle
        List<Battle> availableBattles = battleRepository.findAvailablePublicBattles(BattleStatus.WAITING);
        
        for (Battle battle : availableBattles) {
            try {
                return joinBattle(user, battle);
            } catch (Exception e) {
                // Battle might have filled up, try next one
                log.debug("Could not join battle {}: {}", battle.getId(), e.getMessage());
            }
        }

        // No available battle, check matchmaking queue
        MatchmakingEntry matchedPlayer = findMatchInQueue(user);
        
        if (matchedPlayer != null) {
            // Remove matched player from queue
            removeFromMatchmakingQueue(matchedPlayer.getOduserId());
            
            // Create a new battle with both players
            return createMatchedBattle(user, matchedPlayer);
        }

        // No match found, add to queue
        addToMatchmakingQueue(user);
        
        // Return null to indicate user is queued
        return null;
    }

    private void addToMatchmakingQueue(User user) {
        MatchmakingEntry entry = MatchmakingEntry.builder()
                .oduserId(user.getId())
                .username(user.getUsername())
                .ratingScore(user.getRatingScore())
                .queuedAt(Instant.now())
                .build();

        redisTemplate.opsForList().rightPush(MATCHMAKING_QUEUE_KEY, entry);
        redisTemplate.expire(MATCHMAKING_QUEUE_KEY, Duration.ofSeconds(queueTimeoutSeconds));
        
        log.info("User {} added to matchmaking queue", user.getId());
    }

    private MatchmakingEntry findMatchInQueue(User user) {
        List<Object> queue = redisTemplate.opsForList().range(MATCHMAKING_QUEUE_KEY, 0, -1);
        
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        // Simple matching: find first player within rating range
        int ratingThreshold = 200;
        
        for (Object obj : queue) {
            if (obj instanceof MatchmakingEntry entry) {
                if (!entry.getOduserId().equals(user.getId()) &&
                    Math.abs(entry.getRatingScore() - user.getRatingScore()) <= ratingThreshold) {
                    return entry;
                }
            }
        }
        
        return null;
    }

    private void removeFromMatchmakingQueue(UUID userId) {
        List<Object> queue = redisTemplate.opsForList().range(MATCHMAKING_QUEUE_KEY, 0, -1);
        
        if (queue != null) {
            for (Object obj : queue) {
                if (obj instanceof MatchmakingEntry entry && entry.getOduserId().equals(userId)) {
                    redisTemplate.opsForList().remove(MATCHMAKING_QUEUE_KEY, 1, obj);
                    break;
                }
            }
        }
    }

    public void cancelMatchmaking(UUID userId) {
        removeFromMatchmakingQueue(userId);
        log.info("User {} removed from matchmaking queue", userId);
    }

    @Transactional
    protected BattleResponseDto createMatchedBattle(User user1, MatchmakingEntry user2Entry) {
        User user2 = userRepository.findById(user2Entry.getOduserId())
                .orElseThrow(() -> new IllegalArgumentException("Matched user not found"));

        // Get random problem
        Problem problem = getRandomProblem();

        // Create battle
        Battle battle = Battle.builder()
                .problem(problem)
                .status(BattleStatus.WAITING)
                .maxParticipants(2)
                .durationSeconds(defaultDurationSeconds)
                .isPrivate(false)
                .build();

        battle = battleRepository.save(battle);

        // Add both participants
        for (User user : List.of(user1, user2)) {
            BattleParticipant participant = BattleParticipant.builder()
                    .battle(battle)
                    .user(user)
                    .isReady(false)
                    .build();
            participantRepository.save(participant);
            battle.getParticipants().add(participant);
            trackUserActiveBattle(user.getId(), battle.getId());
        }

        // Create and save battle state to Redis
        BattleState battleState = createBattleStateFromBattle(battle);
        saveBattleStateToRedis(battleState);

        log.info("Matched battle created: {} between {} and {}", battle.getId(), user1.getId(), user2.getId());

        return mapToBattleResponseDto(battle, battleState);
    }

    // ==================== PLAYER READY ====================

    @Transactional
    public BattleResponseDto setPlayerReady(UUID userId, UUID battleId, boolean ready) {
        BattleParticipant participant = participantRepository.findByBattleIdAndUserId(battleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));

        Battle battle = participant.getBattle();
        
        if (battle.getStatus() != BattleStatus.WAITING) {
            throw new IllegalStateException("Cannot change ready status - battle is not in waiting state");
        }

        participant.setReady(ready);
        participantRepository.save(participant);

        // Update Redis state
        BattleState battleState = getBattleStateFromRedis(battleId);
        if (battleState != null) {
            battleState.getParticipants().stream()
                    .filter(p -> p.getOduserId().equals(userId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));
            saveBattleStateToRedis(battleState);

            // Broadcast ready event
            broadcastBattleEvent(battleId, BattleEvent.builder()
                    .type(BattleEvent.EventType.PLAYER_READY)
                    .battleId(battleId)
                    .roomCode(battle.getRoomCode())
                    .payload(Map.of(
                            "userId", userId,
                            "ready", ready
                    ))
                    .build());

            // Check if all players are ready to start
            if (battleState.allParticipantsReady()) {
                startBattle(battleId);
            }
        }

        log.info("User {} set ready status to {} for battle {}", userId, ready, battleId);

        return mapToBattleResponseDto(battle, battleState);
    }

    // ==================== START BATTLE ====================

    @Transactional
    public void startBattle(UUID battleId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("Battle not found"));

        if (battle.getStatus() != BattleStatus.WAITING) {
            throw new IllegalStateException("Battle cannot be started - not in waiting state");
        }

        if (battle.getParticipants().size() < 2) {
            throw new IllegalStateException("Not enough participants to start battle");
        }

        // Update battle status
        battle.setStatus(BattleStatus.IN_PROGRESS);
        battle.setStartedAt(Instant.now());
        battleRepository.save(battle);

        // Update Redis state
        BattleState battleState = getBattleStateFromRedis(battleId);
        if (battleState != null) {
            battleState.setStatus(BattleStatus.IN_PROGRESS);
            battleState.setStartedAt(Instant.now());
            saveBattleStateToRedis(battleState);
        }

        // Broadcast battle starting event
        broadcastBattleEvent(battleId, BattleEvent.builder()
                .type(BattleEvent.EventType.BATTLE_STARTING)
                .battleId(battleId)
                .roomCode(battle.getRoomCode())
                .payload(Map.of("countdown", 5))  // 5 second countdown
                .build());

        // Schedule timer start after countdown
        // In production, use a proper scheduler
        broadcastBattleEvent(battleId, BattleEvent.builder()
                .type(BattleEvent.EventType.TIMER_START)
                .battleId(battleId)
                .roomCode(battle.getRoomCode())
                .payload(Map.of(
                        "durationSeconds", battle.getDurationSeconds(),
                        "startedAt", battle.getStartedAt()
                ))
                .build());

        log.info("Battle {} started", battleId);
    }

    // ==================== LEAVE BATTLE ====================

    @Transactional
    public void leaveBattle(UUID userId, UUID battleId) {
        BattleParticipant participant = participantRepository.findByBattleIdAndUserId(battleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));

        Battle battle = participant.getBattle();

        // Can only leave if battle hasn't started
        if (battle.getStatus() == BattleStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot leave battle in progress - forfeit instead");
        }

        // Remove participant
        battle.getParticipants().remove(participant);
        participantRepository.delete(participant);

        // Clear user's active battle tracking
        clearUserActiveBattle(userId);

        // Update Redis state
        BattleState battleState = getBattleStateFromRedis(battleId);
        if (battleState != null) {
            battleState.getParticipants().removeIf(p -> p.getOduserId().equals(userId));
            
            // If no participants left, cancel the battle
            if (battleState.getParticipants().isEmpty()) {
                cancelBattle(battleId);
                return;
            }
            
            saveBattleStateToRedis(battleState);
        }

        // Broadcast player left event
        broadcastBattleEvent(battleId, BattleEvent.builder()
                .type(BattleEvent.EventType.PLAYER_LEFT)
                .battleId(battleId)
                .roomCode(battle.getRoomCode())
                .payload(Map.of("userId", userId))
                .build());

        log.info("User {} left battle {}", userId, battleId);
    }

    @Transactional
    public void cancelBattle(UUID battleId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("Battle not found"));

        battle.setStatus(BattleStatus.CANCELLED);
        battle.setFinishedAt(Instant.now());
        battleRepository.save(battle);

        // Clear all participants' active battle tracking
        battle.getParticipants().forEach(p -> clearUserActiveBattle(p.getUser().getId()));

        // Remove from Redis
        removeBattleStateFromRedis(battleId);

        // Broadcast cancellation
        broadcastBattleEvent(battleId, BattleEvent.builder()
                .type(BattleEvent.EventType.BATTLE_CANCELLED)
                .battleId(battleId)
                .roomCode(battle.getRoomCode())
                .payload(Map.of("reason", "Battle cancelled"))
                .build());

        log.info("Battle {} cancelled", battleId);
    }

    // ==================== END BATTLE ====================

    @Transactional
    public void endBattle(UUID battleId, UUID winnerId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("Battle not found"));

        if (winnerId != null) {
            User winner = userRepository.findById(winnerId)
                    .orElseThrow(() -> new IllegalArgumentException("Winner not found"));
            battle.setWinner(winner);
            
            // Update winner stats
            winner.setBattlesWon(winner.getBattlesWon() + 1);
            userRepository.save(winner);
        }

        battle.setStatus(BattleStatus.COMPLETED);
        battle.setFinishedAt(Instant.now());
        battleRepository.save(battle);

        // Update all participants' stats
        battle.getParticipants().forEach(p -> {
            User user = p.getUser();
            user.setBattlesPlayed(user.getBattlesPlayed() + 1);
            userRepository.save(user);
            clearUserActiveBattle(user.getId());
        });

        // Update Redis state
        BattleState battleState = getBattleStateFromRedis(battleId);
        if (battleState != null) {
            battleState.setStatus(BattleStatus.COMPLETED);
            battleState.setWinnerId(winnerId);
            battleState.setFinishedAt(Instant.now());
            saveBattleStateToRedis(battleState);
        }

        // Broadcast winner announcement
        broadcastBattleEvent(battleId, BattleEvent.builder()
                .type(BattleEvent.EventType.WINNER_ANNOUNCEMENT)
                .battleId(battleId)
                .roomCode(battle.getRoomCode())
                .payload(Map.of(
                        "winnerId", winnerId != null ? winnerId : "draw",
                        "battleStatus", BattleStatus.COMPLETED
                ))
                .build());

        log.info("Battle {} ended. Winner: {}", battleId, winnerId);
    }

    // ==================== GET BATTLE INFO ====================

    public BattleResponseDto getBattle(UUID battleId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("Battle not found"));

        BattleState battleState = getBattleStateFromRedis(battleId);
        
        return mapToBattleResponseDto(battle, battleState);
    }

    public BattleState getBattleState(UUID battleId) {
        return getBattleStateFromRedis(battleId);
    }

    public List<BattleResponseDto> getUserBattles(UUID userId) {
        List<Battle> battles = battleRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return battles.stream()
                .map(b -> mapToBattleResponseDto(b, getBattleStateFromRedis(b.getId())))
                .collect(Collectors.toList());
    }

    public List<BattleResponseDto> getActiveBattles() {
        List<Battle> battles = battleRepository.findByStatusIn(
                List.of(BattleStatus.WAITING, BattleStatus.IN_PROGRESS));
        return battles.stream()
                .filter(b -> !b.isPrivate())
                .map(b -> mapToBattleResponseDto(b, getBattleStateFromRedis(b.getId())))
                .collect(Collectors.toList());
    }

    // ==================== TIMER UPDATE (Scheduled) ====================

    @Scheduled(fixedRate = 1000)  // Every second
    public void updateBattleTimers() {
        Set<String> battleKeys = redisTemplate.keys(BATTLE_STATE_PREFIX + "*");
        
        if (battleKeys == null) return;

        for (String key : battleKeys) {
            try {
                Object obj = redisTemplate.opsForValue().get(key);
                if (obj instanceof BattleState battleState) {
                    if (battleState.getStatus() == BattleStatus.IN_PROGRESS) {
                        long remaining = battleState.getRemainingTimeSeconds();
                        
                        if (remaining <= 0) {
                            // Time's up - end the battle
                            determineWinnerAndEndBattle(battleState.getBattleId());
                        } else if (remaining % 60 == 0 || remaining <= 10) {
                            // Broadcast timer update every minute or in last 10 seconds
                            broadcastBattleEvent(battleState.getBattleId(), BattleEvent.builder()
                                    .type(BattleEvent.EventType.TIMER_UPDATE)
                                    .battleId(battleState.getBattleId())
                                    .roomCode(battleState.getRoomCode())
                                    .payload(Map.of("remainingSeconds", remaining))
                                    .build());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error updating timer for battle: {}", key, e);
            }
        }
    }

    private void determineWinnerAndEndBattle(UUID battleId) {
        BattleState battleState = getBattleStateFromRedis(battleId);
        if (battleState == null) return;

        // Find winner based on score (most test cases passed / fastest time)
        UUID winnerId = battleState.getParticipants().stream()
                .filter(BattleState.ParticipantState::isHasSubmitted)
                .max(Comparator.comparingInt(BattleState.ParticipantState::getScore))
                .map(BattleState.ParticipantState::getOduserId)
                .orElse(null);

        endBattle(battleId, winnerId);
    }

    // ==================== HELPER METHODS ====================

    private void checkUserNotInActiveBattle(UUID userId) {
        String key = USER_ACTIVE_BATTLE_PREFIX + userId;
        Object activeBattleId = redisTemplate.opsForValue().get(key);
        
        if (activeBattleId != null) {
            throw new IllegalStateException("User is already in an active battle");
        }
    }

    private void trackUserActiveBattle(UUID userId, UUID battleId) {
        String key = USER_ACTIVE_BATTLE_PREFIX + userId;
        redisTemplate.opsForValue().set(key, battleId.toString(), 3, TimeUnit.HOURS);
    }

    private void clearUserActiveBattle(UUID userId) {
        String key = USER_ACTIVE_BATTLE_PREFIX + userId;
        redisTemplate.delete(key);
    }

    private String generateUniqueRoomCode() {
        String roomCode;
        do {
            roomCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (battleRepository.existsByRoomCode(roomCode));
        return roomCode;
    }

    private Problem getProblemForBattle(UUID problemId) {
        if (problemId != null) {
            return problemRepository.findById(problemId)
                    .orElseThrow(() -> new IllegalArgumentException("Problem not found"));
        }
        return getRandomProblem();
    }

    private Problem getRandomProblem() {
        List<Problem> problems = problemRepository.findAll();
        if (problems.isEmpty()) {
            throw new IllegalStateException("No problems available for battle");
        }
        return problems.get(new Random().nextInt(problems.size()));
    }

    // ==================== REDIS OPERATIONS ====================

    private void saveBattleStateToRedis(BattleState battleState) {
        String key = BATTLE_STATE_PREFIX + battleState.getBattleId();
        redisTemplate.opsForValue().set(key, battleState, 4, TimeUnit.HOURS);
    }

    private BattleState getBattleStateFromRedis(UUID battleId) {
        String key = BATTLE_STATE_PREFIX + battleId;
        Object obj = redisTemplate.opsForValue().get(key);
        return obj instanceof BattleState ? (BattleState) obj : null;
    }

    private void removeBattleStateFromRedis(UUID battleId) {
        String key = BATTLE_STATE_PREFIX + battleId;
        redisTemplate.delete(key);
    }

    // ==================== WEBSOCKET BROADCAST ====================

    private void broadcastBattleEvent(UUID battleId, BattleEvent event) {
        String destination = "/topic/battle/" + battleId;
        messagingTemplate.convertAndSend(destination, event);
    }

    // ==================== MAPPERS ====================

    private BattleState createBattleState(Battle battle, BattleParticipant participant, User user) {
        BattleState.ParticipantState participantState = BattleState.ParticipantState.builder()
                .oduserId(user.getId())
                .username(user.getUsername())
                .isReady(false)
                .hasSubmitted(false)
                .score(0)
                .joinedAt(Instant.now())
                .build();

        Set<BattleState.ParticipantState> participants = new HashSet<>();
        participants.add(participantState);

        return BattleState.builder()
                .battleId(battle.getId())
                .roomCode(battle.getRoomCode())
                .problemId(battle.getProblem().getId())
                .status(battle.getStatus())
                .maxParticipants(battle.getMaxParticipants())
                .durationSeconds(battle.getDurationSeconds())
                .isPrivate(battle.isPrivate())
                .participants(participants)
                .createdAt(battle.getCreatedAt())
                .build();
    }

    private BattleState createBattleStateFromBattle(Battle battle) {
        Set<BattleState.ParticipantState> participantStates = battle.getParticipants().stream()
                .map(p -> BattleState.ParticipantState.builder()
                        .oduserId(p.getUser().getId())
                        .username(p.getUser().getUsername())
                        .isReady(p.isReady())
                        .hasSubmitted(p.isHasSubmitted())
                        .score(p.getScore())
                        .joinedAt(p.getJoinedAt())
                        .build())
                .collect(Collectors.toSet());

        return BattleState.builder()
                .battleId(battle.getId())
                .roomCode(battle.getRoomCode())
                .problemId(battle.getProblem().getId())
                .status(battle.getStatus())
                .maxParticipants(battle.getMaxParticipants())
                .durationSeconds(battle.getDurationSeconds())
                .isPrivate(battle.isPrivate())
                .participants(participantStates)
                .winnerId(battle.getWinner() != null ? battle.getWinner().getId() : null)
                .createdAt(battle.getCreatedAt())
                .startedAt(battle.getStartedAt())
                .finishedAt(battle.getFinishedAt())
                .build();
    }

    private BattleResponseDto mapToBattleResponseDto(Battle battle, BattleState battleState) {
        List<BattleResponseDto.ParticipantDto> participants = battle.getParticipants().stream()
                .map(p -> BattleResponseDto.ParticipantDto.builder()
                        .id(p.getId())
                        .oduserId(p.getUser().getId())
                        .username(p.getUser().getUsername())
                        .isReady(p.isReady())
                        .hasSubmitted(p.isHasSubmitted())
                        .score(p.getScore())
                        .joinedAt(p.getJoinedAt())
                        .build())
                .collect(Collectors.toList());

        Problem problem = battle.getProblem();
        ProblemResponseDto problemDto = ProblemResponseDto.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .constraints(problem.getConstraints())
                .exampleInput(problem.getExampleInput())
                .exampleOutput(problem.getExampleOutput())
                .difficulty(problem.getDifficulty())
                .timeLimitSeconds(problem.getTimeLimitSeconds())
                .memoryLimitMb(problem.getMemoryLimitMb())
                .build();

        UserDto winnerDto = battle.getWinner() != null ? UserDto.builder()
                .id(battle.getWinner().getId())
                .username(battle.getWinner().getUsername())
                .build() : null;

        Long remainingTime = battleState != null ? battleState.getRemainingTimeSeconds() : (long) battle.getDurationSeconds();

        return BattleResponseDto.builder()
                .id(battle.getId())
                .roomCode(battle.getRoomCode())
                .status(battle.getStatus())
                .maxParticipants(battle.getMaxParticipants())
                .currentParticipants(battle.getParticipants().size())
                .durationSeconds(battle.getDurationSeconds())
                .isPrivate(battle.isPrivate())
                .problem(problemDto)
                .participants(participants)
                .winner(winnerDto)
                .createdAt(battle.getCreatedAt())
                .startedAt(battle.getStartedAt())
                .finishedAt(battle.getFinishedAt())
                .remainingTimeSeconds(remainingTime)
                .build();
    }
}
