import java.net.DatagramPacket;
import java.io.IOException;


// Applications use GUDP through the GUDP Sockets API.
public interface GUDPSocketAPI {

    public void send(DatagramPacket packet) throws IOException;
    public void receive(DatagramPacket packet) throws IOException;
    public void finish() throws IOException;
    public void close() throws IOException;
}

