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
 * Service for managing Click Race game logic
 * Handles participant registration, position updates, and winner determination
 * Uses thread-safe operations to prevent race conditions
 */
@Service
public class ClickRaceServices {
    
    // Thread-safe map to store all races by race number
    private Map<Integer, Race> races;
    
    // WebSocket messaging template for broadcasting updates to all connected clients
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * Constructor initializes the races map with concurrent access support
     */
    public ClickRaceServices() {
        this.races = new ConcurrentHashMap<>();
    }
    
    /**
     * Register a player to a race
     * CRITICAL SECTION: Synchronized to prevent duplicate registrations
     * 
     * @param raceNum The race number
     * @param participant The participant to register
     * @throws ServicesException if the participant number is already taken
     */
    public synchronized void registerPlayerToRace(int raceNum, RaceParticipant participant) throws ServicesException {
        // Get or create the race if it doesn't exist
        Race race = races.computeIfAbsent(raceNum, Race::new);
        
        // Check if the number is already taken to prevent duplicates
        if (race.getParticipants().containsKey(participant.getNumber())) {
            throw new ServicesException("Player number " + participant.getNumber() + " is already registered");
        }
        
        // Add the participant to the race
        race.addParticipant(participant);
        
        // Broadcast the new participant to all connected clients via WebSocket
        messagingTemplate.convertAndSend("/topic/races." + raceNum + ".participants", participant);
        
        // If we have exactly 5 participants, notify all clients to start the race
        // This enables the "MOVE MY CAR!" button in all clients automatically
        if (race.getParticipants().size() == 5) {
            messagingTemplate.convertAndSend("/topic/races." + raceNum + ".ready", getRegisteredPlayers(raceNum));
        }
    }
    
    /**
     * Get all registered players for a race
     * 
     * @param raceNum The race number
     * @return List of all registered participants
     * @throws ServicesException if there's an error retrieving participants
     */
    public List<RaceParticipant> getRegisteredPlayers(int raceNum) throws ServicesException {
        Race race = races.get(raceNum);
        if (race == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(race.getParticipants().values());
    }
    
    /**
     * Update participant position in the race
     * Broadcasts the new position to all connected clients for real-time updates
     * 
     * @param raceNum The race number
     * @param participantNum The participant number
     * @param xPosition The new X position of the participant
     * @throws ServicesException if the race or participant doesn't exist
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
        
        // Update the participant's position
        participant.setxPosition(xPosition);
        
        // Broadcast the position update to all clients via WebSocket
        // This allows all players to see each other's movements in real-time
        messagingTemplate.convertAndSend("/topic/races." + raceNum + ".positions", participant);
    }
    
    /**
     * Register a winner for the race (first to cross the finish line wins)
     * CRITICAL SECTION: Synchronized to ensure only one winner can be registered
     * This prevents race condition where multiple participants cross simultaneously
     * 
     * @param raceNum The race number
     * @param participantNum The participant number attempting to register as winner
     * @throws ServicesException if race doesn't exist, participant not found, or winner already exists
     */
    public synchronized void registerWinner(int raceNum, int participantNum) throws ServicesException {
        Race race = races.get(raceNum);
        if (race == null) {
            throw new ServicesException("Race " + raceNum + " does not exist");
        }
        
        // Check if there's already a winner - only the first to cross wins!
        // This is the critical section that prevents multiple winners
        if (race.hasWinner()) {
            throw new ServicesException("Race " + raceNum + " already has a winner: " + race.getWinnerNumber());
        }
        
        RaceParticipant participant = race.getParticipant(participantNum);
        if (participant == null) {
            throw new ServicesException("Participant " + participantNum + " is not registered in race " + raceNum);
        }
        
        // Register this participant as the winner
        race.setWinnerNumber(participantNum);
        
        // Broadcast the winner to all connected clients
        // This triggers the winner announcement in all browsers
        messagingTemplate.convertAndSend("/topic/races." + raceNum + ".winner", participant);
    }
    
    /**
     * Get the winner of a race
     * 
     * @param raceNum The race number
     * @return The participant who won the race
     * @throws ServicesException if race doesn't exist or no winner has been declared yet
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
