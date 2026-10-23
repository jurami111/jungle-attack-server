package cr.ac.tec.jungleattack.server.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameConstantsTest {
    @Test
    void gameHasSevenSpeciesAndTheExpectedLimits() {
        assertEquals(7, AnimalSpecies.values().length);
        assertEquals(20, GameConstants.MAX_PLAYER_ENERGY);
        assertEquals(10, GameConstants.MAX_COFFEE_CUPS);
        assertEquals(30, GameConstants.ENCOUNTER_RADIUS);
        assertEquals(30, GameConstants.FLAG_RADIUS);
        assertEquals(1000, GameConstants.ATTACK_COOLDOWN_MS);
        assertNotEquals(GameConstants.PLAYER1_FLAG_COLOR, GameConstants.PLAYER2_FLAG_COLOR);
    }
}
