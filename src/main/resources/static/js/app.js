// Global variables for game state
var stompClient = null;          // WebSocket STOMP client for real-time communication
var myCarNumber = null;          // The car number of this player
var raceNumber = 25;             // Fixed race number for this game
var participants = {};           // Map of all participants by their car number
var carPositions = {};           // Map of current positions of all cars
var isRaceReady = false;         // Flag indicating if race has started (5 participants)
var winnerDeclared = false;      // Flag to prevent multiple winner registrations

/**
 * Initialize the application when DOM is ready
 */
$(document).ready(function() {
    // Establish WebSocket connection for real-time updates
    connectWebSocket();
    
    // Bind click event to registration button
    $("#register-btn").click(function() {
        registerPlayer();
    });
    
    // Bind click event to move button
    $("#move-btn").click(function() {
        moveMycar();
    });
});

/**
 * Connect to WebSocket server and subscribe to race topics
 * This enables real-time updates for participants, positions, and winners
 */
function connectWebSocket() {
    // Create SockJS connection
    var socket = new SockJS('/clickrace-websocket');
    stompClient = Stomp.over(socket);
    
    // Connect to the WebSocket server
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        
        // Subscribe to participant registration updates
        // Triggered when any player joins the race
        stompClient.subscribe('/topic/races.' + raceNumber + '.participants', function (message) {
            var participant = JSON.parse(message.body);
            addParticipant(participant);
        });
        
        // Subscribe to race ready notification
        // Triggered when exactly 5 participants have registered
        stompClient.subscribe('/topic/races.' + raceNumber + '.ready', function (message) {
            var allParticipants = JSON.parse(message.body);
            console.log("Race is ready with 5 participants!");
            loadAllParticipants(allParticipants);
            enableGame();  // Enable the "MOVE MY CAR!" button
        });
        
        // Subscribe to position updates
        // Triggered whenever any player moves their car
        stompClient.subscribe('/topic/races.' + raceNumber + '.positions', function (message) {
            var participant = JSON.parse(message.body);
            updateCarPosition(participant);
        });
        
        // Subscribe to winner announcements
        // Triggered when a participant crosses the finish line first
        stompClient.subscribe('/topic/races.' + raceNumber + '.winner', function (message) {
            var winner = JSON.parse(message.body);
            announceWinner(winner);
        });
    });
}

/**
 * Register the current player in the race
 * Validates car number and sends registration request to server
 */
function registerPlayer() {
    var playerNumber = parseInt($("#player-number").val());
    
    // Validate the car number
    if (!playerNumber || playerNumber < 1) {
        showStatus("Please enter a valid car number", "error");
        return;
    }
    
    // Send registration request to REST API
    $.ajax({
        url: '/races/' + raceNumber + '/participants',
        type: 'PUT',
        contentType: 'application/json',
        data: JSON.stringify({
            number: playerNumber,
            xPosition: 0
        }),
        success: function(response) {
            // Registration successful
            myCarNumber = playerNumber;
            $("#my-car-number").text(myCarNumber);
            
            // Hide registration form and show game interface
            $("#registration-section").hide();
            $("#game-section").show();
            showStatus("Registration successful! Waiting for 5 participants...", "success");
            
            // Load any participants that are already registered
            loadCurrentParticipants();
        },
        error: function(xhr) {
            // Registration failed (e.g., number already taken)
            showStatus("Error: " + xhr.responseText, "error");
        }
    });
}

function loadCurrentParticipants() {
    $.ajax({
        url: '/races/' + raceNumber + '/participants',
        type: 'GET',
        success: function(participantList) {
            console.log("Loaded " + participantList.length + " participants");
            participantList.forEach(function(participant) {
                addParticipant(participant);
            });
            
            // If already 5 participants, enable the game
            if (participantList.length >= 5) {
                enableGame();
            }
        },
        error: function(xhr) {
            console.error("Error loading participants: " + xhr.responseText);
        }
    });
}

function addParticipant(participant) {
    if (!participants[participant.number]) {
        participants[participant.number] = participant;
        createCarElement(participant);
        updateParticipantCount();
    }
}

function loadAllParticipants(participantList) {
    participantList.forEach(function(participant) {
        addParticipant(participant);
    });
}

function createCarElement(participant) {
    var carDiv = $("<div>")
        .attr("id", "car-" + participant.number)
        .addClass("car")
        .text(participant.number)
        .css({
            left: participant.xPosition + "px",
            top: (Object.keys(participants).length * 60 + 20) + "px"
        });
    
    if (participant.number === myCarNumber) {
        carDiv.addClass("my-car");
    }
    
    $("#race-track").append(carDiv);
}

function updateParticipantCount() {
    $("#participant-count").text(Object.keys(participants).length);
}

/**
 * Enable the game when 5 participants have joined
 * This is called automatically via WebSocket when the race is ready
 */
function enableGame() {
    if (!isRaceReady) {
        isRaceReady = true;
        // Enable the "MOVE MY CAR!" button - this was disabled initially
        $("#move-btn").prop("disabled", false);
        showStatus("Race is ready! Click MOVE MY CAR! to race!", "success");
    }
}

/**
 * Move the player's car forward by 10 pixels
 * Called when player clicks "MOVE MY CAR!" button
 */
function moveMycar() {
    // Don't move if not registered or winner already declared
    if (!myCarNumber || winnerDeclared) {
        return;
    }
    
    var currentPosition = carPositions[myCarNumber] || 0;
    var newPosition = currentPosition + 10;  // Move 10 pixels per click
    
    // Update local position immediately for better responsiveness
    carPositions[myCarNumber] = newPosition;
    $("#car-" + myCarNumber).css("left", newPosition + "px");
    
    // Send position update to server, which broadcasts to all clients
    $.ajax({
        url: '/races/' + raceNumber + '/participants/' + myCarNumber + '/position',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            number: myCarNumber,
            xPosition: newPosition
        }),
        error: function(xhr) {
            console.error("Error updating position: " + xhr.responseText);
        }
    });
    
    // Check if we crossed the finish line (640 pixels or more)
    // If so, attempt to register as winner
    if (newPosition >= 640 && !winnerDeclared) {
        registerAsWinner();
    }
}

function updateCarPosition(participant) {
    carPositions[participant.number] = participant.xPosition;
    $("#car-" + participant.number).css("left", participant.xPosition + "px");
}

/**
 * Attempt to register this player as the winner
 * Only the first player to cross the finish line will succeed
 * Server enforces this with synchronized methods - others get HTTP 409 CONFLICT
 */
function registerAsWinner() {
    winnerDeclared = true;  // Prevent multiple registration attempts
    
    // Try to register as winner at the server
    $.ajax({
        url: '/races/' + raceNumber + '/winner',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            number: myCarNumber,
            xPosition: carPositions[myCarNumber]
        }),
        success: function() {
            console.log("Successfully registered as winner!");
        },
        error: function(xhr) {
            // Failed - someone else was first
            console.log("Failed to register as winner: " + xhr.responseText);
            winnerDeclared = false; // Reset flag if we weren't first
        }
    });
}

/**
 * Announce the winner to all clients
 * Called via WebSocket when server broadcasts the winner
 * This ensures ALL clients see the winner simultaneously
 * 
 * @param winner The RaceParticipant who won the race
 */
function announceWinner(winner) {
    winnerDeclared = true;
    
    // Display winner message
    $("#winner-message").text("🏆 Winner: Car #" + winner.number + " 🏆");
    
    // Disable the move button for all players
    $("#move-btn").prop("disabled", true);
    
    // Highlight the winner's car in gold with glow effect
    $("#car-" + winner.number).css({
        "background-color": "#FFD700",  // Gold
        "border-color": "#FFA500",      // Orange
        "box-shadow": "0 0 20px #FFD700" // Glow
    });
}

function showStatus(message, type) {
    var statusDiv = $("#status-message");
    statusDiv.text(message);
    statusDiv.removeClass("success error");
    statusDiv.addClass(type);
}
