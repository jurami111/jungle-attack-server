package cr.ac.tec.jungleattack.server.game;

import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import java.util.List;

public final class MatchSummary {

    public static final class PackSummary {
        private final AnimalSpecies species;
        private final int botsKilled;
        private final int animalsEliminated;
        private final int cupsConsumed;

        public PackSummary(AnimalSpecies species, int botsKilled, int animalsEliminated, int cupsConsumed) {
            this.species = species;
            this.botsKilled = botsKilled;
            this.animalsEliminated = animalsEliminated;
            this.cupsConsumed = cupsConsumed;
        }

        public AnimalSpecies getSpecies() {
            return species;
        }

        public int getBotsKilled() {
            return botsKilled;
        }

        public int getAnimalsEliminated() {
            return animalsEliminated;
        }

        public int getCupsConsumed() {
            return cupsConsumed;
        }
    }

    private final AnimalSpecies winnerSpecies;
    private final long durationSeconds;
    private final List<PackSummary> packs;

    public MatchSummary(AnimalSpecies winnerSpecies, long durationSeconds, List<PackSummary> packs) {
        this.winnerSpecies = winnerSpecies;
        this.durationSeconds = durationSeconds;
        this.packs = List.copyOf(packs);
    }

    public AnimalSpecies getWinnerSpecies() {
        return winnerSpecies;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public List<PackSummary> getPacks() {
        return packs;
    }
}
