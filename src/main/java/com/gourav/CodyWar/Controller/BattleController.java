package com.gourav.CodyWar.Controller;

import com.gourav.CodyWar.Domain.Dto.*;
import com.gourav.CodyWar.Security.CustomUserDetails;
import com.gourav.CodyWar.Service.BattleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/battles")
@RequiredArgsConstructor
@Slf4j
public class BattleController {

    private final BattleService battleService;

    /**
     * Create a new battle (private or public).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BattleResponseDto>> createBattle(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateBattleRequest request) {
        
        BattleResponseDto battle = battleService.createBattle(userDetails.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.success(battle, "Battle created successfully"));
    }

    /**
     * Join a battle by room code (for private battles).
     */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<BattleResponseDto>> joinBattleByRoomCode(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody JoinBattleRequest request) {
        
        BattleResponseDto battle = battleService.joinBattleByRoomCode(
                userDetails.getUser().getId(), request.getRoomCode());
        return ResponseEntity.ok(ApiResponse.success(battle, "Joined battle successfully"));
    }

    /**
     * Join a battle by ID (for public battles).
     */
    @PostMapping("/{battleId}/join")
    public ResponseEntity<ApiResponse<BattleResponseDto>> joinBattleById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID battleId) {
        
        BattleResponseDto battle = battleService.joinBattleById(userDetails.getUser().getId(), battleId);
        return ResponseEntity.ok(ApiResponse.success(battle, "Joined battle successfully"));
    }

    /**
     * Find a match or queue for matchmaking.
     * Returns the matched battle or null if queued for matching.
     */
    @PostMapping("/matchmaking")
    public ResponseEntity<ApiResponse<BattleResponseDto>> findMatch(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        BattleResponseDto battle = battleService.findOrQueueMatch(userDetails.getUser().getId());
        
        if (battle != null) {
            return ResponseEntity.ok(ApiResponse.success(battle, "Match found!"));
        } else {
            return ResponseEntity.ok(ApiResponse.success(null, "Added to matchmaking queue. Waiting for opponent..."));
        }
    }

    /**
     * Cancel matchmaking queue.
     */
    @DeleteMapping("/matchmaking")
    public ResponseEntity<ApiResponse<Void>> cancelMatchmaking(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        battleService.cancelMatchmaking(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Removed from matchmaking queue"));
    }

    /**
     * Set player ready status.
     */
    @PostMapping("/{battleId}/ready")
    public ResponseEntity<ApiResponse<BattleResponseDto>> setReady(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID battleId,
            @RequestParam(defaultValue = "true") boolean ready) {
        
        BattleResponseDto battle = battleService.setPlayerReady(
                userDetails.getUser().getId(), battleId, ready);
        return ResponseEntity.ok(ApiResponse.success(battle, ready ? "Marked as ready" : "Marked as not ready"));
    }

    /**
     * Leave a battle (only allowed before battle starts).
     */
    @PostMapping("/{battleId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveBattle(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID battleId) {
        
        battleService.leaveBattle(userDetails.getUser().getId(), battleId);
        return ResponseEntity.ok(ApiResponse.success(null, "Left battle successfully"));
    }

    /**
     * Get battle details by ID.
     */
    @GetMapping("/{battleId}")
    public ResponseEntity<ApiResponse<BattleResponseDto>> getBattle(@PathVariable UUID battleId) {
        BattleResponseDto battle = battleService.getBattle(battleId);
        return ResponseEntity.ok(ApiResponse.success(battle));
    }

    /**
     * Get real-time battle state from Redis.
     */
    @GetMapping("/{battleId}/state")
    public ResponseEntity<ApiResponse<BattleState>> getBattleState(@PathVariable UUID battleId) {
        BattleState state = battleService.getBattleState(battleId);
        return ResponseEntity.ok(ApiResponse.success(state));
    }

    /**
     * Get user's battle history.
     */
    @GetMapping("/my-battles")
    public ResponseEntity<ApiResponse<List<BattleResponseDto>>> getMyBattles(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        List<BattleResponseDto> battles = battleService.getUserBattles(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(battles));
    }

    /**
     * Get list of active public battles that can be joined.
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<BattleResponseDto>>> getActiveBattles() {
        List<BattleResponseDto> battles = battleService.getActiveBattles();
        return ResponseEntity.ok(ApiResponse.success(battles));
    }
}
