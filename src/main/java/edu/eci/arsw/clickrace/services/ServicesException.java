package edu.eci.arsw.clickrace.services;

/**
 * Exception for service layer
 */
public class ServicesException extends Exception {
    
    public ServicesException(String message) {
        super(message);
    }
    
    public ServicesException(String message, Throwable cause) {
        super(message, cause);
    }
}
