import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Scanner;

import javax.imageio.ImageIO;

/**
 * This file acts as a server for the Buoys.
 * @author Pranit Meher (pxm3417@rit.edu)
 *
 */
public class ServerOnShore {
	
	
	public static void main(String[] args) throws IOException, InterruptedException {		
		// accepting inputs from user
		Scanner input = new Scanner(System.in);
		System.out.println("Please enter the port number I should listen on");
		int ListeningPort = input.nextInt();
		
		// Making Sockets
		DatagramSocket sendSocket = new DatagramSocket();
		DatagramSocket receivingSocket = new DatagramSocket(ListeningPort);
		
		while(true){
			// lets multiple buoys connect and assigns a new port for every buoy
			
			byte[] buffer = new byte[1024];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			receivingSocket.receive(packet);
			String message = new String( packet.getData());
			String[] messageArray = message.split(" ");
			
			// getting buoy IP and Port
			String tempPort = messageArray[messageArray.length -1].trim();
			int buoyPort = Integer.parseInt(tempPort);
			InetAddress buoyIp = packet.getAddress();
			
			Runnable CommunicatorClassObject = new CommunicatorClass(buoyPort, buoyIp);
			Thread CommunicatorThread = new Thread(CommunicatorClassObject);
			
			// starting the thread
			CommunicatorThread.start();
			Thread.sleep(100);
		}
	}
}

class CommunicatorClass implements Runnable{
	int buoyPort;
	InetAddress buoyIp;
	DatagramSocket sendSocket;
	DatagramSocket receiveSocket;
	static final int MTU = 63500;
	static final int headerLength = 37;
	static final int sourceIPPacketPointer = 4;
	static final int sourcePortPacketPointer = 8;
	static final int destinationIPPacketPointer = 12;
	static final int destinationPortPacketPointer = 16;
	static final int sequenceNumberPacketPointer = 20;
	static final int checkSumPacketPointer = 36;
	static final int paddingPacketPointer = 37;
	
	public CommunicatorClass(int buoyPort, InetAddress buoyIp) {
		this.buoyPort = buoyPort;
		this.buoyIp = buoyIp;
	}

	@Override
	public void run() {
		makeSockets();
		
		// initiate communication
		initiateCommunication();
	
		// start listening
		int flag = 0;
		double size = -1;
		int numberOfPackets = -1;
		byte[] imageInBytes = null;
		int imageInBytesPointer = 0;
		
		int acknowledgementFlag = -1;
		int sequenceNumer = 0;
		while(true){
			
			// will receive images from here
			if(flag == 0){
				
				byte[] buffer = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				try {
					receiveSocket.receive(packet);	// size received
					byte[] sizeInBytes = packet.getData();
					size = ByteBuffer.wrap(sizeInBytes).getInt();
					imageInBytes = new byte[(int) size];
					numberOfPackets = (int) Math.ceil(size/(MTU-headerLength));
					flag = 2;
					
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
			else if(flag == 1){
				byte[] buffer = new byte[MTU];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				
				try {
					receiveSocket.receive(packet);
					buffer = packet.getData();
					byte[] tempBuffer;
					
					// extract information from here
					
					//copying to imageBuffer
					if(sequenceNumer == numberOfPackets -1){
						// for the remnant bytes
						tempBuffer = new byte[imageInBytes.length - sequenceNumer*(MTU-headerLength)];
					}
					else{
						tempBuffer = new byte[MTU - headerLength];
					}
					
					int x = MTU - tempBuffer.length;			
					
					for(int i = x; i < MTU; i++){
						if(imageInBytesPointer >= size){
							System.out.println("imgpoint break: " + imageInBytesPointer);
							System.out.println(tempBuffer[imageInBytesPointer-1]);
							break;
						}
						// copying to a temporary buffer for calculating checksum
						imageInBytes[imageInBytesPointer++] = buffer[i];
						tempBuffer[i - x] =  buffer[i];	
					}				
					// extracting checkSum
					byte[] extractedCheckSum = new byte[checkSumPacketPointer - sequenceNumberPacketPointer];
					int j = 0;
					for(int i = sequenceNumberPacketPointer; i < checkSumPacketPointer; i++){
						extractedCheckSum[j++] = buffer[i]; 
					}
					
					// Calculating CheckSum
					MessageDigest checksumCalc = MessageDigest.getInstance("MD5");
					byte[] checkSumValueInBytes = checksumCalc.digest(tempBuffer);
					
					if(!Arrays.equals(checkSumValueInBytes, extractedCheckSum)){
						acknowledgementFlag = 0;
						imageInBytesPointer = imageInBytesPointer - MTU + headerLength;
					}
					else{
						acknowledgementFlag = 1;
					}
					
					// extracting sequence number
					byte[] extractedSequenceNumber = new byte[sequenceNumberPacketPointer - destinationPortPacketPointer];
					int k = 0;
					for(int i = destinationPortPacketPointer; i < sequenceNumberPacketPointer; i++){
						extractedSequenceNumber[k++] = buffer[i];
					}
					sequenceNumer = ByteBuffer.wrap(extractedSequenceNumber).getInt();
					System.out.println("Received Sequence Number: " + sequenceNumer );
					
				} catch (IOException e) {
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
				flag = 2;
			}
			// block for sending acknowledgments
			else if(flag == 2){
				
				ByteBuffer f = ByteBuffer.allocate(4);
				f.putInt(acknowledgementFlag);
				byte[] acknowledgementInBytes = f.array();
				
				// ACK packet
				DatagramPacket ACK = new DatagramPacket(acknowledgementInBytes, acknowledgementInBytes.length, buoyIp, buoyPort);
				try {
					sendSocket.send(ACK);	// ACK Sent
				} catch (IOException e) {
					e.printStackTrace();
				}
				if((sequenceNumer == numberOfPackets) && (acknowledgementFlag == 1)){
					System.out.println("Image Received");
					System.out.println("Writing Image to file");
					try {
						InputStream in = new ByteArrayInputStream(imageInBytes);
						BufferedImage receivedImage = ImageIO.read(in);
						ImageIO.write(receivedImage, "jpg", new File("2.jpg"));
						System.out.println("Writing Completed");
						sendSocket.close();
						receiveSocket.close();
						return;
					} catch (IOException e) {
						e.printStackTrace();
					}

					break;
				}
				flag = 1;
			}
		}
	}

	private void initiateCommunication() {
		// telling the buoy port to communicate on
		String messageInString = "Hey I am the ServerOnShore. I am listening to you on " + receiveSocket.getLocalPort();
		byte[] message = messageInString.getBytes();

		// communicating with the Buoy
		DatagramPacket packet = new DatagramPacket(message, message.length, buoyIp, buoyPort);
		try {
			sendSocket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private void makeSockets() {
		// just making sending and receiving sockets
		try{
			sendSocket = new DatagramSocket();
			receiveSocket = new DatagramSocket();
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}
	
}
