var stompClient = null;
var myCarNumber = null;
var raceNumber = 25;
var participants = {};
var carPositions = {};
var isRaceReady = false;
var winnerDeclared = false;

$(document).ready(function() {
    connectWebSocket();
    
    $("#register-btn").click(function() {
        registerPlayer();
    });
    
    $("#move-btn").click(function() {
        moveMycar();
    });
});

function connectWebSocket() {
    var socket = new SockJS('/clickrace-websocket');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        
        // Subscribe to participant updates
        stompClient.subscribe('/topic/races.' + raceNumber + '.participants', function (message) {
            var participant = JSON.parse(message.body);
            addParticipant(participant);
        });
        
        // Subscribe to race ready (when 5 participants register)
        stompClient.subscribe('/topic/races.' + raceNumber + '.ready', function (message) {
            var allParticipants = JSON.parse(message.body);
            console.log("Race is ready with 5 participants!");
            loadAllParticipants(allParticipants);
            enableGame();
        });
        
        // Subscribe to position updates
        stompClient.subscribe('/topic/races.' + raceNumber + '.positions', function (message) {
            var participant = JSON.parse(message.body);
            updateCarPosition(participant);
        });
        
        // Subscribe to winner announcements
        stompClient.subscribe('/topic/races.' + raceNumber + '.winner', function (message) {
            var winner = JSON.parse(message.body);
            announceWinner(winner);
        });
    });
}

function registerPlayer() {
    var playerNumber = parseInt($("#player-number").val());
    
    if (!playerNumber || playerNumber < 1) {
        showStatus("Please enter a valid car number", "error");
        return;
    }
    
    $.ajax({
        url: '/races/' + raceNumber + '/participants',
        type: 'PUT',
        contentType: 'application/json',
        data: JSON.stringify({
            number: playerNumber,
            xPosition: 0
        }),
        success: function(response) {
            myCarNumber = playerNumber;
            $("#my-car-number").text(myCarNumber);
            $("#registration-section").hide();
            $("#game-section").show();
            showStatus("Registration successful! Waiting for 5 participants...", "success");
            
            // Load current participants
            loadCurrentParticipants();
        },
        error: function(xhr) {
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

function enableGame() {
    if (!isRaceReady) {
        isRaceReady = true;
        $("#move-btn").prop("disabled", false);
        showStatus("Race is ready! Click MOVE MY CAR! to race!", "success");
    }
}

function moveMycar() {
    if (!myCarNumber || winnerDeclared) {
        return;
    }
    
    var currentPosition = carPositions[myCarNumber] || 0;
    var newPosition = currentPosition + 10;
    
    // Update local position immediately for responsiveness
    carPositions[myCarNumber] = newPosition;
    $("#car-" + myCarNumber).css("left", newPosition + "px");
    
    // Send position update to server
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
    if (newPosition >= 640 && !winnerDeclared) {
        registerAsWinner();
    }
}

function updateCarPosition(participant) {
    carPositions[participant.number] = participant.xPosition;
    $("#car-" + participant.number).css("left", participant.xPosition + "px");
}

function registerAsWinner() {
    winnerDeclared = true;
    
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
            console.log("Failed to register as winner: " + xhr.responseText);
            winnerDeclared = false; // Reset if registration failed
        }
    });
}

function announceWinner(winner) {
    winnerDeclared = true;
    $("#winner-message").text("🏆 Winner: Car #" + winner.number + " 🏆");
    $("#move-btn").prop("disabled", true);
    
    // Highlight the winner's car
    $("#car-" + winner.number).css({
        "background-color": "#FFD700",
        "border-color": "#FFA500",
        "box-shadow": "0 0 20px #FFD700"
    });
}

function showStatus(message, type) {
    var statusDiv = $("#status-message");
    statusDiv.text(message);
    statusDiv.removeClass("success error");
    statusDiv.addClass(type);
}
