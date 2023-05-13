import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;
    
    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)
    
    // variables used for Entity A
    private Packet[] senderWindow;
    private int[] ifAck;
    private int tempSeqNum = 0;
    private int base = 0;
    private LinkedList<Packet> senderBuffer = new LinkedList<Packet>();
    // variables used for Entity B
    private Packet[] receiverWindow;
    private int latestReceivedSeqNum = -1;
    private Packet latestReceivedPacket;
    // variables for statistics output
    private int originalTransmissions = 0;
    private int corrupted = 0;
    private int retransmissions = 0;
    private int delivered = 0;
    private int ack = 0;
    private int RTTCounter = 0;
    private double totalRTT = 0.0;
    private int communicationCounter = 0;
    private double totalCommunication = 0.0;
    private Map<Integer,Double> RTTRecord = new HashMap<Integer,Double>();
    private Map<Integer,Double> communicationRecord = new HashMap<Integer,Double>();
    
    
    

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages, double loss, double corrupt, double avgDelay, int trace, int seed, int winsize, double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    
    
    
    
    
    
    // method for calculate checksum for a packet:
    // checksum = sequence#  +  ACK#  +  each char in payload
    private int checkSum(Packet packet) {
        int checksum = packet.getSeqnum() + packet.getAcknum();
        String payload = packet.getPayload();
        for(int i=0; i<payload.length(); i++)
        	checksum = checksum + payload.charAt(i);
        return checksum;
    }
    
    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
    	// Start 
    	System.out.println("A output START");
        // create packet
    	Packet sendPacket = new Packet(tempSeqNum, -1, 0, message.getData());
    	System.out.println("Upper layer MSG at A:" + message.getData());
        //set checksum
    	sendPacket.setChecksum(checkSum(sendPacket));
        // buffer packet
        senderBuffer.add(sendPacket);
        //System.out.println("Current sender buffer size:" + senderBuffer.size());
        // check next seq number, when seq number reaches LimitSeqNo value, wraps around to 0
        if (tempSeqNum+1 >= LimitSeqNo) {
            tempSeqNum=0;
        } else {
            tempSeqNum++;
        }
        // add packets into sender window from buffer if there are empty spaces
        for (int i=base; (i<senderBuffer.size())&&(i<WindowSize+base); i++) {
        	if (senderWindow[i%WindowSize] == null) {
        		senderWindow[i%WindowSize] = senderBuffer.get(i);
        	}
        }
        // for every packet in window, send out if unsent
        for (int i=0; i<ifAck.length; i++) {
        	if (senderWindow[i]!=null&&ifAck[i]==-1) {
        		toLayer3(A, senderWindow[i]);
        		System.out.println("toLayer3: seqnum:" + senderWindow[i].getSeqnum() +  
        						   " acknum:" + senderWindow[i].getAcknum() +
        						   " checksum:" + senderWindow[i].getChecksum() +
        						   " payload:" + senderWindow[i].getPayload());
        		ifAck[i] = 0;
        		RTTRecord.put(senderWindow[i].getSeqnum(), getTime());
        		communicationRecord.put(senderWindow[i].getSeqnum(), getTime());
        		originalTransmissions++;
        		stopTimer(A);
        		System.out.println("stopTimer: stopping timer at " + getTime());
        		startTimer(A, RxmtInterval);
        		System.out.println("startTimer: starting timer at " + getTime());
        	}
        }
    	System.out.println("A output END");
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
    	System.out.println("A input START");
		System.out.println("A got ACK from B, packet is: seqnum:" + packet.getSeqnum() +  
				" acknum:" + packet.getAcknum() + 
				" checksum:" + packet.getChecksum() +
				" payload:" + packet.getPayload());
    	// the received packet is corrupted
    	if (packet.getChecksum()!=checkSum(packet)) {
            // update count of corrupted packet
    		corrupted++;
    		System.out.println("A got Corrupted ACK/NAK from B");
    	} else {
    		//if being acked for the first time
    		if (ifAck[packet.getAcknum()%WindowSize]==0) {
        		stopTimer(A);
        		System.out.println("stopTimer: stopping timer at " + getTime());
        		// set ack status to 1, means is acked
        		ifAck[packet.getAcknum()%WindowSize] = 1;
    			// if ACK >= head, update head
        		if (packet.getAcknum()>=base) {
        			// if ACK = head
        			if (packet.getAcknum()==base) {
        				System.out.println("Received ACK:" + packet.getAcknum());
        			} else {
        			// if ACK > head
        				System.out.println("Received Cumulative ACK. Expected:" + base + 
								   		   " Received:"+ packet.getAcknum());
        			}
        			// store current base to previousHead
        			int previousHead = base;
        			// update new base
        			base = packet.getAcknum()+1;
        			// Slide sender window, send new packet from buffer
                    for (int i=previousHead; i<base; i++) {
                    	int key = i%WindowSize;
                    	if (RTTRecord.get(senderWindow[key].getSeqnum())!=-1.0) {
                    		// update total RTT time
                    		totalRTT = totalRTT + (getTime()-RTTRecord.get(senderWindow[key].getSeqnum()));
                    		RTTRecord.put(senderWindow[key].getSeqnum(), -1.0);
                    		RTTCounter++;
                    	}
                    	communicationCounter++;
                    	// update total Communication time
                    	totalCommunication = totalCommunication + (getTime()-communicationRecord.get(senderWindow[key].getSeqnum()));
                    	// set ack status to -1
                    	ifAck[key] = -1;
                    	// reset certain location in sender Window
                    	senderWindow[key] = null;
                    }
                    // add packets into window from buffer if there are empty spaces
                    for (int i=base; (i<senderBuffer.size()) && (i<WindowSize+base); i++) {
                    	// find empty space in sender window 
                    	if (senderWindow[i%WindowSize]==null) {
                    		// add packets from buffer into corresponding sender window
                    		senderWindow[i%WindowSize] = senderBuffer.get(i);
                    	}
                    }
                    // for every packet in window, send out if unsent
                    for (int i=0; i<ifAck.length; i++) {
                    	// ack status is -1, means hasn't been sent before
                    	if ((ifAck[i]==-1)&&(senderWindow[i]!=null)) {
                    		// send packets to B from sender window
                    		toLayer3(A, senderWindow[i]);
                    		System.out.println("toLayer3: seqnum:" + senderWindow[i].getSeqnum() +  
         						   " acknum:" + senderWindow[i].getAcknum() +
         						   " checksum:" + senderWindow[i].getChecksum() +
         						   " payload:" + senderWindow[i].getPayload());
                    		// packet sent to B, set ack status to 0, means has been sent but not acked
                    		ifAck[i] = 0;
                    		RTTRecord.put(senderWindow[i].getSeqnum(), getTime());
                            // update count of original retransmit packet
                    		originalTransmissions++;
                    		stopTimer(A);
                    		System.out.println("stopTimer: stopping timer at " + getTime());
                    		startTimer(A, RxmtInterval);
                    		System.out.println("startTimer: starting timer at " + getTime());
                    	}
                    }
        		}
    		} else if (ifAck[packet.getAcknum()%WindowSize]==1) {
    		// has been ack before (i.e. duplicate ACK, resends next packet) 
    			System.out.println("\tDuplicate ACK:" + packet.getAcknum() + ", resend:" + base);
    			//get next packet
    			int temp =(packet.getAcknum()+1)%WindowSize;
    			// resend next packet if the packet has been acked before
    			toLayer3(A, senderWindow[temp]);
        		System.out.println("toLayer3: seqnum:" + senderWindow[temp].getSeqnum() +  
						   " acknum:" + senderWindow[temp].getAcknum() +
						   " checksum:" + senderWindow[temp].getChecksum() +
						   " payload:" + senderWindow[temp].getPayload());
    			RTTRecord.put(senderWindow[temp].getSeqnum(), getTime());
    			// update count of retransmissions
    			retransmissions++;
        		stopTimer(A);
        		System.out.println("stopTimer: stopping timer at " + getTime());
        		startTimer(A, RxmtInterval);
        		System.out.println("startTimer: starting timer at " + getTime());
    		} else {
    		// the received packet is corrupted
    			System.out.println("A got Corrupted ACK/NAK from B");
                // update count of corrupted packet
    	        corrupted++;
    		}
    	}
    	System.out.println("A input END");
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt() {
    	System.out.println("Timer interrupt A at local time:" + getTime());
        System.out.println("\tTimer: A retrans " + base);
        // retransmit packet due to time interrupt
        toLayer3(A, senderWindow[base%WindowSize]);
        System.out.println("toLayer3: seqnum:" + senderWindow[base%WindowSize].getSeqnum() +  
				   " acknum:" + senderWindow[base%WindowSize].getAcknum() +
				   " checksum:" + senderWindow[base%WindowSize].getChecksum() +
				   " payload:" + senderWindow[base%WindowSize].getPayload());
        RTTRecord.put(senderWindow[base % WindowSize].getSeqnum(), getTime());
        // update count of retransmit packet
        retransmissions++;
		stopTimer(A);
		System.out.println("stopTimer: stopping timer at " + getTime());
		startTimer(A, RxmtInterval);
		System.out.println("startTimer: starting timer at " + getTime());
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
    	// init senderWindow with size
        senderWindow = new Packet[WindowSize];
        // init ifAck array with size
        ifAck = new int[WindowSize];
        // fill ifAck with -1
        for (int i=0; i<ifAck.length; i++) {
        	ifAck[i] = -1;
        }
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {
    	System.out.println("B input START");
    	System.out.println("B getting:" + packet.getPayload());
    	// packet corrupted
    	if (packet.getChecksum()!=checkSum(packet)) {
    		// update count of corrupted packets
    		corrupted++;
    		System.out.println("Checksum error in B, corrupted");
    	} else {
    	// packet is not corrupted
    		// update ACK to seq and recompute checksum
            packet.setAcknum(packet.getSeqnum());
            packet.setChecksum(checkSum(packet));
            // check last received seq number, when next seq number reaches LimitSeqNo value, wraps around to 0
            if (latestReceivedSeqNum == LimitSeqNo-1) {
            	latestReceivedSeqNum = -1;
            }
            if (packet.getSeqnum()==(latestReceivedSeqNum+1)) {
            // if packet with expected sequence number, send data to layer5
                ack++;
                delivered++;
                latestReceivedSeqNum++;
                // update latest received packet
                latestReceivedPacket = packet;
            	System.out.println("Correct seq num. Expecting pkt:"+(latestReceivedSeqNum)+", got pkt:"+packet.getSeqnum());
            	// send data to layer5
                toLayer5(packet.getPayload());
                // send more packets in receiver window if it's available
                while (receiverWindow[(latestReceivedSeqNum+1)%WindowSize]!=null && 
                	   receiverWindow[(latestReceivedSeqNum+1)%WindowSize].getSeqnum()==(latestReceivedSeqNum+1)) {
                	ack++;
                	delivered++;
                	latestReceivedSeqNum++;
                	// update the latest received packet
                	latestReceivedPacket = receiverWindow[(latestReceivedSeqNum+1)%WindowSize];
                	// send data to layer5
                	toLayer5(receiverWindow[(latestReceivedSeqNum+1)%WindowSize].getPayload());
                }
                System.out.println("toLayer3: seqnum:" + latestReceivedPacket.getSeqnum() +  
     				   " acknum:" + latestReceivedPacket.getAcknum() +
     				   " checksum:" + latestReceivedPacket.getChecksum() +
     				   " payload:" + latestReceivedPacket.getPayload());
            } else if (packet.getSeqnum()>(latestReceivedSeqNum+1) && 
            		   packet.getSeqnum()<(latestReceivedSeqNum+WindowSize)) {
            // buffer packet in receiver window if not the expected packet but within window size
                ack++;
            	System.out.println("Expecting pkt:"+(latestReceivedSeqNum+1)+", got pkt:"+packet.getSeqnum());
            	// buffer packet in receiver window
                receiverWindow[packet.getSeqnum()%WindowSize] = packet;
                System.out.println("Putting packet into rcv_buffer["+(packet.getSeqnum()%WindowSize)+"]");
            	System.out.println("Sending ACK: " + latestReceivedPacket.getSeqnum());
            } else if (packet.getSeqnum()==latestReceivedSeqNum) {
            // send duplicate ACK if the received packet is duplicate
                ack++;
            	System.out.println("Expecting pkt:"+(latestReceivedSeqNum+1)+", got pkt:"+packet.getSeqnum());
            	System.out.println("Receiving duplicate packets:" + packet.getSeqnum() + ". Sending ACK:"+latestReceivedPacket.getSeqnum());
            }
            // send ACK to A
            toLayer3(B, latestReceivedPacket);
    	}
    	System.out.println("B input END");
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
    	// init receiverWindow with size
    	receiverWindow = new Packet[WindowSize];
    }

    // Use to print final statistics
    protected void Simulation_done() {
    	double lostRatio = (retransmissions-corrupted) / (double)((originalTransmissions+retransmissions)+ack);
        double corruptionRatio = corrupted / (double)((originalTransmissions+retransmissions)+ack-(retransmissions-corrupted));
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A:" + originalTransmissions);
    	System.out.println("Number of retransmissions by A:" + retransmissions);
    	System.out.println("Number of data packets delivered to layer 5 at B:" + delivered);
    	System.out.println("Number of ACK packets sent by B:" + ack);
    	System.out.println("Number of corrupted packets:" + corrupted);
    	System.out.println("Ratio of lost packets:" + lostRatio);
    	System.out.println("Ratio of corrupted packets:" + corruptionRatio);
    	System.out.println("Average RTT:" + totalRTT / (double)RTTCounter);
    	System.out.println("Average communication time:" + totalCommunication / (double)communicationCounter);
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	System.out.println("All RTT: " + totalRTT);
    	System.out.println("Counter RTT: " + RTTCounter);
    	System.out.println("Total time to communicate: " + totalCommunication);
    	System.out.println("Counter for time to communicate: " + communicationCounter);
    }	
}
