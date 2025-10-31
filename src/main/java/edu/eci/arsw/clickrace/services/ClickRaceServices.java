package edu.eci.arsw.clickrace.services;

import edu.eci.arsw.clickrace.model.Race;
import edu.eci.arsw.clickrace.model.RaceParticipant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for managing Click Race
 */
@Service
public class ClickRaceServices {
    
    private Map<Integer, Race> races;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    public ClickRaceServices() {
        this.races = new ConcurrentHashMap<>();
    }
    
    /**
     * Register a player to a race
     */
    public synchronized void registerPlayerToRace(int raceNum, RaceParticipant participant) throws ServicesException {
        Race race = races.computeIfAbsent(raceNum, Race::new);
        
        // Check if the number is already taken
        if (race.getParticipants().containsKey(participant.getNumber())) {
            throw new ServicesException("Player number " + participant.getNumber() + " is already registered");
        }
        
        race.addParticipant(participant);
        
        // Notify all clients about new participant
        messagingTemplate.convertAndSend("/topic/races." + raceNum + ".participants", participant);
        
        // If we have exactly 5 participants, notify clients to start the race
        if (race.getParticipants().size() == 5) {
            messagingTemplate.convertAndSend("/topic/races." + raceNum + ".ready", getRegisteredPlayers(raceNum));
        }
    }
    
    /**
     * Get all registered players for a race
     */
    public List<RaceParticipant> getRegisteredPlayers(int raceNum) throws ServicesException {
        Race race = races.get(raceNum);
        if (race == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(race.getParticipants().values());
    }
    
    /**
     * Update participant position
     */
    public void updateParticipantPosition(int raceNum, int participantNum, int xPosition) throws ServicesException {
        Race race = races.get(raceNum);
        if (race == null) {
            throw new ServicesException("Race " + raceNum + " does not exist");
        }
        
        RaceParticipant participant = race.getParticipant(participantNum);
        if (participant == null) {
            throw new ServicesException("Participant " + participantNum + " is not registered in race " + raceNum);
        }
        
        participant.setxPosition(xPosition);
        
        // Broadcast the position update to all clients
        messagingTemplate.convertAndSend("/topic/races." + raceNum + ".positions", participant);
    }
    
    /**
     * Register a winner for the race (first to cross wins)
     */
    public synchronized void registerWinner(int raceNum, int participantNum) throws ServicesException {
        Race race = races.get(raceNum);
        if (race == null) {
            throw new ServicesException("Race " + raceNum + " does not exist");
        }
        
        // Check if there's already a winner
        if (race.hasWinner()) {
            throw new ServicesException("Race " + raceNum + " already has a winner: " + race.getWinnerNumber());
        }
        
        RaceParticipant participant = race.getParticipant(participantNum);
        if (participant == null) {
            throw new ServicesException("Participant " + participantNum + " is not registered in race " + raceNum);
        }
        
        race.setWinnerNumber(participantNum);
        
        // Broadcast the winner to all clients
        messagingTemplate.convertAndSend("/topic/races." + raceNum + ".winner", participant);
    }
    
    /**
     * Get the winner of a race
     */
    public RaceParticipant getWinner(int raceNum) throws ServicesException {
        Race race = races.get(raceNum);
        if (race == null) {
            throw new ServicesException("Race " + raceNum + " does not exist");
        }
        
        if (!race.hasWinner()) {
            throw new ServicesException("Race " + raceNum + " does not have a winner yet");
        }
        
        return race.getParticipant(race.getWinnerNumber());
    }
}
