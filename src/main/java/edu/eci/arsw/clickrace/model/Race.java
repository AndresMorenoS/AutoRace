package edu.eci.arsw.clickrace.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Model class for a race
 */
public class Race {
    private int raceNumber;
    private Map<Integer, RaceParticipant> participants;
    private Integer winnerNumber;
    
    public Race(int raceNumber) {
        this.raceNumber = raceNumber;
        this.participants = new ConcurrentHashMap<>();
        this.winnerNumber = null;
    }
    
    public int getRaceNumber() {
        return raceNumber;
    }
    
    public void setRaceNumber(int raceNumber) {
        this.raceNumber = raceNumber;
    }
    
    public Map<Integer, RaceParticipant> getParticipants() {
        return participants;
    }
    
    public void setParticipants(Map<Integer, RaceParticipant> participants) {
        this.participants = participants;
    }
    
    public Integer getWinnerNumber() {
        return winnerNumber;
    }
    
    public void setWinnerNumber(Integer winnerNumber) {
        this.winnerNumber = winnerNumber;
    }
    
    public void addParticipant(RaceParticipant participant) {
        participants.put(participant.getNumber(), participant);
    }
    
    public RaceParticipant getParticipant(int number) {
        return participants.get(number);
    }
    
    public boolean hasWinner() {
        return winnerNumber != null;
    }
}
