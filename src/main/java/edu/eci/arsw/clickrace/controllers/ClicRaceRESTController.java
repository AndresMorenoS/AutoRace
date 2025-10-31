/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.eci.arsw.clickrace.controllers;

import edu.eci.arsw.clickrace.model.RaceParticipant;
import edu.eci.arsw.clickrace.services.ClickRaceServices;
import edu.eci.arsw.clickrace.services.ServicesException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Click Race game
 * Provides endpoints for:
 * - Participant registration and retrieval
 * - Position updates
 * - Winner registration and retrieval
 * 
 * @author hcadavid
 */
@RestController
@RequestMapping(value = "/races")
public class ClicRaceRESTController {

    // Service layer for business logic
    @Autowired
    ClickRaceServices services;
    
    
    /**
     * GET endpoint to retrieve all participants in a race
     * 
     * @param racenum The race number as a string path variable
     * @return ResponseEntity with list of participants and HTTP 202 ACCEPTED status
     *         or error message with appropriate HTTP status
     */
    @RequestMapping(path = "/{racenum}/participants",method = RequestMethod.GET)
    public ResponseEntity<?> getRaceParticipantsNums(@PathVariable(name = "racenum") String racenum) {
        
        try {
            return new ResponseEntity<>(services.getRegisteredPlayers(Integer.parseInt(racenum)),HttpStatus.ACCEPTED);
        } catch (ServicesException ex) {
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>(ex.getLocalizedMessage(),HttpStatus.NOT_FOUND);
        } catch (NumberFormatException ex){
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>("/{racenum}/ must be an integer value.",HttpStatus.BAD_REQUEST);
        }
    }
    

    /**
     * PUT endpoint to register a new participant in a race
     * When 5 participants are registered, all clients are automatically notified to start
     * 
     * @param racenum The race number as a string path variable
     * @param rp The RaceParticipant object to register (from request body)
     * @return ResponseEntity with HTTP 201 CREATED status on success
     *         or error message with HTTP 400 BAD_REQUEST if participant already exists
     */
    @RequestMapping(path = "/{racenum}/participants",method = RequestMethod.PUT)
    public ResponseEntity<?> addParticipantNum(@PathVariable(name = "racenum") String racenum,@RequestBody RaceParticipant rp) {
        try {
            services.registerPlayerToRace(Integer.parseInt(racenum), rp);
                    return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (ServicesException ex) {
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>(ex.getLocalizedMessage(),HttpStatus.BAD_REQUEST);
        } catch (NumberFormatException ex){
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>("/{racenum}/ must be an integer value.",HttpStatus.BAD_REQUEST);
        }

    }

    /**
     * POST endpoint to update a participant's position
     * Called every time a player clicks "MOVE MY CAR!" button
     * Broadcasts the new position to all connected clients via WebSocket
     * 
     * @param racenum The race number
     * @param participantnum The participant number
     * @param rp RaceParticipant object containing the new position
     * @return ResponseEntity with HTTP 200 OK on success or error message
     */
    @RequestMapping(path = "/{racenum}/participants/{participantnum}/position",method = RequestMethod.POST)
    public ResponseEntity<?> updateParticipantPosition(@PathVariable(name = "racenum") String racenum,
                                                        @PathVariable(name = "participantnum") String participantnum,
                                                        @RequestBody RaceParticipant rp) {
        try {
            services.updateParticipantPosition(Integer.parseInt(racenum), Integer.parseInt(participantnum), rp.getxPosition());
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (ServicesException ex) {
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>(ex.getLocalizedMessage(),HttpStatus.BAD_REQUEST);
        } catch (NumberFormatException ex){
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>("Parameters must be integer values.",HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * GET endpoint to retrieve the winner of a race
     * 
     * @param racenum The race number
     * @return ResponseEntity with the winner participant and HTTP 200 OK
     *         or HTTP 404 NOT_FOUND if no winner has been declared yet
     */
    @RequestMapping(path = "/{racenum}/winner",method = RequestMethod.GET)
    public ResponseEntity<?> getWinner(@PathVariable(name = "racenum") String racenum) {
        try {
            return new ResponseEntity<>(services.getWinner(Integer.parseInt(racenum)),HttpStatus.OK);
        } catch (ServicesException ex) {
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>(ex.getLocalizedMessage(),HttpStatus.NOT_FOUND);
        } catch (NumberFormatException ex){
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>("/{racenum}/ must be an integer value.",HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * POST endpoint to register a winner for a race
     * Called automatically when a participant crosses the finish line (X >= 640)
     * Only the first participant to call this endpoint becomes the winner
     * 
     * @param racenum The race number
     * @param rp RaceParticipant attempting to register as winner
     * @return ResponseEntity with HTTP 201 CREATED on success (first to cross)
     *         or HTTP 409 CONFLICT if a winner already exists (subsequent attempts)
     */
    @RequestMapping(path = "/{racenum}/winner",method = RequestMethod.POST)
    public ResponseEntity<?> registerWinner(@PathVariable(name = "racenum") String racenum,
                                            @RequestBody RaceParticipant rp) {
        try {
            services.registerWinner(Integer.parseInt(racenum), rp.getNumber());
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (ServicesException ex) {
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>(ex.getLocalizedMessage(),HttpStatus.CONFLICT);
        } catch (NumberFormatException ex){
            Logger.getLogger(ClicRaceRESTController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<>("/{racenum}/ must be an integer value.",HttpStatus.BAD_REQUEST);
        }
    }

}