import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

/**
 * This file implements a file transfer protocol for efficient communication between the Buoy's and the shore.
 * @author Pranit Meher (pxm3417@rit.edu)
 *
 */

public class BuoyOcean {
	static final int MTU = 63500;
	static final int headerLength = 37;
	static final int sourceIPPacketPointer = 4;
	static final int sourcePortPacketPointer = 8;
	static final int destinationIPPacketPointer = 12;
	static final int destinationPortPacketPointer = 16;
	static final int sequenceNumberPacketPointer = 20;
	static final int checkSumPacketPointer = 36;
	static final int paddingPacketPointer = 37;
	static final int timeOut = 5000;

	public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
		
		// Declarations and inputs
		Scanner input = new Scanner(System.in);
		
		//converting image to byte array
		File imageFileName = new File(args[0]);
		byte[] imageInByte = null;
		try {
			imageInByte = Files.readAllBytes(imageFileName.toPath());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		// Making Sockets
		DatagramSocket sendSocket = null;
		DatagramSocket receivingSocket = null;
		try {
			sendSocket = new DatagramSocket();
			receivingSocket = new DatagramSocket();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		// Source IP
		String sourceIpString = InetAddress.getLocalHost().getHostAddress();
		InetAddress sourceIp = InetAddress.getByName(sourceIpString);
		byte[] sourceIpInBytes = sourceIp.getAddress();
		
		// Source Port number
		int sourcePort = receivingSocket.getLocalPort();
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(sourcePort);
		byte[] sourcePortInBytes = b.array();
		
		
		// Destination IP and port
		System.out.println("Please enter the Server's IP Address and Port number in this format");
		System.out.println("Eg: 127.0.0.1:12345");
		String serverOnShoreIpPort = input.nextLine();
		String[] serverOnShoreIpPortArray = serverOnShoreIpPort.split(":");
		String serverOnShoreIP = serverOnShoreIpPortArray[0];
		int serverOnShorePort = Integer.parseInt(serverOnShoreIpPortArray[1]);
		
		// Destination IP
		InetAddress destinationIp = InetAddress.getByName(serverOnShoreIP);
		
		// communication with the serverOnShore		
		while(true){
			// message
			String messageInString = "Hey I am a Buoy and I am Listening on port "+ sourcePort;
			byte[] message = messageInString.getBytes();
			
			// communicating with the serverOnShore
			DatagramPacket packet = new DatagramPacket(message, message.length, destinationIp, serverOnShorePort);
			try {
				sendSocket.send(packet);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			// waiting for the serverOnShore's packet to initiate communication
			byte[] buffer = new byte[1024];
			DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
			
			try {
				receivingSocket.setSoTimeout(timeOut); 
				receivingSocket.receive(receivedPacket);
			}
			catch (SocketTimeoutException e) {
				System.out.println("Couldn't reach or receive from server. \n Trying again...");
				continue;
			} catch (SocketException e) {
				System.out.println("Socket Timed out. Try sending the file again");
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 
			
			String receivedMessage = new String(receivedPacket.getData());
			String[] receivedMessageArray = receivedMessage.split(" ");
			String tempPort = receivedMessageArray[receivedMessageArray.length -1].trim();
			int shorePort = Integer.parseInt(tempPort);
			InetAddress shoreIP = receivedPacket.getAddress();
			
			// Destination IP in bytes
			byte[] destinationIpInBytes = shoreIP.getAddress();	
			
			// Destination Port number in bytes
			ByteBuffer c = ByteBuffer.allocate(4);
			c.putInt(shorePort);
			byte[] destinationPortInBytes = c.array();

			// sending size of image
			int sizeOfImage = imageInByte.length;
			ByteBuffer d = ByteBuffer.allocate(4);
			d.putInt(sizeOfImage);
			byte[] sizeOfImageInBytes = d.array();
			DatagramPacket sizePacket = new DatagramPacket(sizeOfImageInBytes, sizeOfImageInBytes.length, shoreIP, shorePort);
			sendSocket.send(sizePacket);
			
			// waiting to receive message to start sending image
			byte[] sizeAck = new byte[1024];
			DatagramPacket sizeAckPacket = new DatagramPacket(sizeAck, sizeAck.length);
			receivingSocket.receive(sizeAckPacket);
			
			//padding byte
			byte[] paddingByte = new byte[1];
			
			// send image from here
			int start = 0;
			int end = MTU - headerLength;
			int sequenceNumber = 1;
			int flag = 0;
			int sendReceiveFlag = 0;
			while(true){
				byte[] tempImageInBytes;
				
				if (sendReceiveFlag == 0){
					
					if(end >= imageInByte.length){
						end = imageInByte.length;
						flag = 1;
						tempImageInBytes = new byte[end - start];
					}
					else{
						tempImageInBytes = new byte[MTU - headerLength];
					}
					
					//sequence number in bytes
					ByteBuffer e = ByteBuffer.allocate(4);
					e.putInt(sequenceNumber);
					byte[] sequenceNumberInBytes = e.array();
					
					int m = 0;
					
					for(int i = start; i < end; i++){
						tempImageInBytes[m] = imageInByte[i];
						m++;
					}
					
					// Calculating CheckSum
					MessageDigest checksumCalc = MessageDigest.getInstance("MD5");
					byte[] checkSumValueInBytes = checksumCalc.digest(tempImageInBytes);
					
					byte[] UDPPacketInBytes = new byte[MTU];
					UDPPacketInBytes = copyPacket(UDPPacketInBytes,sourceIpInBytes,sourceIPPacketPointer);
					UDPPacketInBytes = copyPacket(UDPPacketInBytes, sourcePortInBytes, sourcePortPacketPointer);
					UDPPacketInBytes = copyPacket(UDPPacketInBytes, destinationIpInBytes, destinationIPPacketPointer);
					UDPPacketInBytes = copyPacket(UDPPacketInBytes, destinationPortInBytes, destinationPortPacketPointer);
					UDPPacketInBytes = copyPacket(UDPPacketInBytes, sequenceNumberInBytes,sequenceNumberPacketPointer);
					UDPPacketInBytes = copyPacket(UDPPacketInBytes, checkSumValueInBytes,checkSumPacketPointer);
					UDPPacketInBytes = copyPacket(UDPPacketInBytes, paddingByte,paddingPacketPointer);
					UDPPacketInBytes = copyPacket(UDPPacketInBytes, tempImageInBytes,MTU);	

					start = end;
					end = start + MTU - headerLength;
										
					DatagramPacket UDPPacket = new DatagramPacket(UDPPacketInBytes, UDPPacketInBytes.length, shoreIP, shorePort);
					sendSocket.send(UDPPacket);
					
					//send UDPPacket
					System.out.println("sequence number is: "+ sequenceNumber);
					sequenceNumber++;
	
					sendReceiveFlag = 1;
				}
				else if(sendReceiveFlag == 1){
					
					byte[] ACK = new byte[1024];
					DatagramPacket ACKPacket = new DatagramPacket(ACK, ACK.length);
					receivingSocket.setSoTimeout(timeOut); 
					receivingSocket.receive(ACKPacket);
					ACK = ACKPacket.getData();
					int acknowledgement = ByteBuffer.wrap(ACK).getInt();
					
					if(acknowledgement == 0){
						System.out.println("Acknowledgement failed for sequence: " + (sequenceNumber -1));
						System.out.println("Resending sequence "+ (sequenceNumber -1));
						end = start;
						start = start - MTU + headerLength;
						sequenceNumber--;
						flag = 0;
					}
					
					if(flag == 1){
						System.out.println("Sending image completed");
						break;
					}
					sendReceiveFlag = 0;
				}
			}
			
			break;
		}
		
	}

	private static byte[] copyPacket(byte[] uDPPacket, byte[] toCopy, int pointer) {
		int j = 0;
		
		for(int i = pointer - toCopy.length; i < pointer; i++){
			uDPPacket[i] = toCopy[j];
			j++;
		}
		return uDPPacket;
	}

}
