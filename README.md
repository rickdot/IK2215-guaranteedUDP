# IK2215-guaranteedUDP

The programming assignment for the course IK2215 Advanced Internetworking in KTH.

Implement a protocol called Guaranteed UDP which provides guaranteed delivery based on UDP.

## Directory Structure

- `GUDPPacket.java` is the class for GUDP protocol declarations with associated methods to access the GUDP packet header and payload.

- `GUDPSocketAPI.java` is the interface, providing the well-defined API that you must use for your implementation.

- `GUDPSocket.java` is the class for GUDP library that I implemented.

- `run.txt` contains the command to run the program and test application.

Inside test application folder:
- `receiver_no_loss.jar` (VSRecv with my GUDP library)

- `receiver_with_loss.jar` (VSRecv with my GUDP library with random drops of received packets)

- `receiver_nothing.jar` (VSRecv that stays idle and does not process incoming packets)

- `sender_no_loss.jar` (VSSend with my GUDP library)

- `sender_with_loss.jar` (VSSend with my GUDP library with random drops of sent packets)