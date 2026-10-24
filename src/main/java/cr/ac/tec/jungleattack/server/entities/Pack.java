package cr.ac.tec.jungleattack.server.entities;

import cr.ac.tec.jungleattack.server.config.AnimalSpecies;
import java.util.ArrayList;
import java.util.List;

public class Pack {

    private final AnimalSpecies species;          // Tipo de animal (usando el Enum)
    private int damageCapacity;      // Daño compartido por todo el paquete
    private final List<Animal> members;     // Lista de los animales del paquete
    private int botsKilled;
    private int animalsEliminated;
    private int cupsConsumed;

    public Pack(AnimalSpecies species, int damageCapacity) {
        this.species = species;
        this.damageCapacity = damageCapacity;
        this.members = new ArrayList<>();
    }

    public void setDamageCapacity(int bonus) {
        this.damageCapacity += bonus;
    }

    public void addMember(Animal member) {
        this.members.add(member);
    }

    public List<Animal> getMembers() {
        return List.copyOf(members);
    }

    public AnimalSpecies getSpecies() {
        return species;
    }

    public int getDamageCapacity() {
        return damageCapacity;
    }

    public int registerBotKill() {
        botsKilled++;
        return botsKilled;
    }

    public void registerAnimalEliminated() {
        animalsEliminated++;
    }

    public void registerCupConsumed() {
        cupsConsumed++;
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