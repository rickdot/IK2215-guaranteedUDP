import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.util.Random;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GUDPSocket implements GUDPSocketAPI {
    DatagramSocket datagramSocket;
    InetSocketAddress sender_addr;

    LinkedList<GUDPPacket> sendQueue = new LinkedList<GUDPPacket>();
    LinkedList<GUDPPacket> receiveQueue = new LinkedList<GUDPPacket>();

	int next_seq = -1;
    boolean start = false;

	int left = 0;
	int right = 0;
	final int MAX_FAILURE = 10;
	int failure = 0;

	int receiver_expect=-1;
    
    // takes an existing DatagramSocket (UDP socket) into a GUDP socket
    public GUDPSocket(DatagramSocket socket) {
        datagramSocket = socket;
    }

    public void send(DatagramPacket packet) throws IOException {
//		convert packet into GUDP packet
		if (!start) {
			// generate random BSN number
			Random rand = new Random(); //instance of random class
			int upperbound = 127; //generate random values from 0-24
			int int_random = rand.nextInt(upperbound);
//			add a BSN packet to the queue
			InetSocketAddress recvaddr = new InetSocketAddress(packet.getAddress(), packet.getPort());
			ByteBuffer BSN_data = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
			BSN_data.order(ByteOrder.BIG_ENDIAN);
			GUDPPacket bsn_packet = new GUDPPacket(BSN_data);
			bsn_packet.setType(GUDPPacket.TYPE_BSN);
			bsn_packet.setVersion(GUDPPacket.GUDP_VERSION);
			bsn_packet.setPayloadLength(0);
			bsn_packet.setSeqno(int_random);
			bsn_packet.setSocketAddress(recvaddr);
			sendQueue.add(bsn_packet);

			next_seq = int_random + 1;
			start = true;
		}

		if (start){
			// add DATA to sending queue
			GUDPPacket data_packet = GUDPPacket.encapsulate(packet);
			data_packet.setType(GUDPPacket.TYPE_DATA);
			data_packet.setVersion(GUDPPacket.GUDP_VERSION);
			data_packet.setSeqno(next_seq);
			sendQueue.add(data_packet);
			next_seq += 1;
		}
	}



        

    public void receive(DatagramPacket packet) throws IOException {
//		datagramSocket.setSoTimeout(500);

//		receive a UDP packet from network
        byte[] buf = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
        DatagramPacket udppacket = new DatagramPacket(buf, buf.length);

		while(true) {
			try {
				datagramSocket.receive(udppacket);
				GUDPPacket gudppacket = GUDPPacket.unpack(udppacket);
				short recv_type = gudppacket.getType();
				int recv_seq = gudppacket.getSeqno();
//				System.out.println(String.format("GUDPSocket: received %s %s", recv_type, recv_seq));
//

				if(recv_type == GUDPPacket.TYPE_BSN) {
					receiver_expect = recv_seq + 1;
					sendACK(gudppacket, receiver_expect);
					receiveQueue.add(gudppacket);
				} else if (recv_type == GUDPPacket.TYPE_DATA) {
//					received the correct seq
					if(recv_seq == receiver_expect){
						receiver_expect += 1;
						sendACK(gudppacket, receiver_expect);
						receiveQueue.add(gudppacket);
						gudppacket.decapsulate(packet);
						break;
					} else {
//					received the wrong seq
						sendACK(gudppacket, receiver_expect);
					}
				}


			} catch (SocketTimeoutException e) {
//				System.out.println("GUDPSocket: receive timeout, try again");
			}
		}




    }

	public void sendACK(GUDPPacket gudpp, int ack_seq) throws IOException {

		ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
		buffer.order(ByteOrder.BIG_ENDIAN);
		GUDPPacket ack_packet = new GUDPPacket(buffer);
		ack_packet.setType(GUDPPacket.TYPE_ACK);
		ack_packet.setVersion(GUDPPacket.GUDP_VERSION);
		ack_packet.setSeqno(ack_seq);
		ack_packet.setPayloadLength(0);
		ack_packet.setSocketAddress(gudpp.getSocketAddress());
		datagramSocket.send(ack_packet.pack());



	}

    public void finish() throws IOException {
//		System.out.println("finish() is called, all packets in the queue, start the actual sending");
		print_sending_queue();
		start_actual_sending();
//		System.out.println("end of finished()");
    }

	// send one packet in the queue
	public void sendOne(int index) throws IOException {
		GUDPPacket tmp_gudp_packet = sendQueue.get(index);
		DatagramPacket udp_packet = tmp_gudp_packet.pack();
		datagramSocket.send(udp_packet);
//		System.out.println(String.format("GUDPSocket: sender sent %s %s", tmp_gudp_packet.getType(), tmp_gudp_packet.getSeqno()));

	}

	public void  start_actual_sending() throws IOException {
		left = 0;
		right = 0;
		int state = 0; // 0->send; 1->wait for ACK;

		while (true) {


			if(right==sendQueue.size() && left==sendQueue.size()){
//				System.out.println("GUDPSocket: actual sending completed");
				sendQueue.clear();
				left = 0;
				right = 0;

				start = false;
				break;
			}

			if(state==0) {
				// send queue[right]
				if(right <= sendQueue.size()-1) {
					sendOne(right);
					right += 1;
				}

				// check window is full after every sending
				if(get_window_size() == GUDPPacket.MAX_WINDOW_SIZE || right == sendQueue.size()) {
					System.out.println("window size reach, wait for ACK");
					state = 1;
				}
			} else if(state==1) {

//				receive ACK
				byte[] buf = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
				DatagramPacket udp_packet = new DatagramPacket(buf, buf.length);

				datagramSocket.setSoTimeout(500);

				try {
					datagramSocket.receive(udp_packet);
					failure = 0;
					GUDPPacket received_ack = GUDPPacket.unpack(udp_packet);
					short recv_type = received_ack.getType();
					int recv_seq = received_ack.getSeqno();
//					System.out.println(String.format("GUDPSocket: received %s %s", recv_type, recv_seq));

					if(recv_seq-1 == get_seq_by_index(left)){
						left += 1;
					}
					if(get_window_size()==0){
						state = 0;
					}

				} catch (SocketTimeoutException e) {
//					System.out.println(String.format("GUDPSocket: cannot received expected ACK %s", get_seq_by_index(left)+1));
					failure += 1;
					sendOne(left);
				}

				if(failure == MAX_FAILURE){
//					System.err.println("transmission failed");
					throw new IOException("Retransmissions times reach limit");
//					break;
				}




			}


		}
	}



    public void close() throws IOException {
        datagramSocket.close();
    	System.out.println("socket closed");
    }
    
    
    public void print_sending_queue() throws IOException {
    	System.out.print("sending queue: ");
    	sendQueue.forEach((e) -> {
			System.out.print(String.format("%s-%s  ", e.getType(), e.getSeqno()));
	    });
	    System.out.println();
    }

	public void print_receive_queue() throws IOException {
		System.out.print("receiving queue: ");
		receiveQueue.forEach((e) -> {
			System.out.print(String.format("%s-%s  ", e.getType(), e.getSeqno()));
		});
		System.out.println();
	}

	public int get_window_size() throws IOException {
		return right - left ;
	}

	public int get_seq_by_index(int index) throws IOException {
		GUDPPacket get_gudp_packet = sendQueue.get(index);
		return get_gudp_packet.getSeqno();
	}

    
}

