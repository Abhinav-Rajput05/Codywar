package com.gourav.CodyWar.Exception;

import com.gourav.CodyWar.Domain.Entity.BattleStatus;

/**
 * Thrown when a user tries to submit code to a battle
 * that is not in IN_PROGRESS state.
 * Mapped to HTTP 409 CONFLICT by GlobalExceptionHandler.
 */
public class BattleNotActiveException extends RuntimeException {

    private final BattleStatus currentStatus;

    public BattleNotActiveException(BattleStatus currentStatus) {
        super(String.format("Cannot submit — battle is %s, must be IN_PROGRESS", currentStatus));
        this.currentStatus = currentStatus;
    }

    public BattleStatus getCurrentStatus() {
        return currentStatus;
    }
}
