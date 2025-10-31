package edu.eci.arsw.clickrace.model;

/**
 * Model class for a race participant
 */
public class RaceParticipant {
    private int number;
    private int xPosition;
    
    public RaceParticipant() {
    }
    
    public RaceParticipant(int number) {
        this.number = number;
        this.xPosition = 0;
    }
    
    public RaceParticipant(int number, int xPosition) {
        this.number = number;
        this.xPosition = xPosition;
    }
    
    public int getNumber() {
        return number;
    }
    
    public void setNumber(int number) {
        this.number = number;
    }
    
    public int getxPosition() {
        return xPosition;
    }
    
    public void setxPosition(int xPosition) {
        this.xPosition = xPosition;
    }
    
    @Override
    public String toString() {
        return "RaceParticipant{" + "number=" + number + ", xPosition=" + xPosition + '}';
    }
}
