package ahmedemam.boatsframework;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import ahmedemam.boatsframework.model.DataPacket;
import ahmedemam.boatsframework.model.Packet;

/**
 * Created by aemam on 12/7/15.
 */
public class WorkerThread extends Thread {
    /*****************************
     *    DIFFERENT log types
     * ***************************
     */
    private static final int DEBUG = 0;
    private static final int WARN = 1;
    private static final int INFO = 2;
    private static final int ERROR = 3;
    /*****************************
     *    DIFFERENT log types
     * ***************************
     */


    /**************************************
     *             Packet Types
     * *************************************
     */
    private static final byte HEARTBEAT = 1;
    private static final byte STREAM = 2;
    private static final byte DATA = 3;
    private static final byte TERMINATE = 4;    //Terminate connection
    private static final byte DELETE = 5;

    /**************************************
     *             Packet Types
     * *************************************
     */


    /**************************************
     *             Thread Variables
     * *************************************
     */
    private BlockingQueue<Packet> incomingPackets;       //Queue for packets received by radio
    private BlockingQueue<Packet> outgoingPackets;       //Queue for packets sent on the radio
    private static final String TAG = "Connection_Manager";
    private boolean threadIsAlive;
    int deviceID = 0;
    MainActivity mainActivity;
    private boolean peerIsAlive;                        //If the peer associated with this thread
    //is alive
    private int counter = 0;
    WorkerThread mainWorkerThread;
    private WritingThread writingThread;
    private boolean StreamData;
    private boolean heartBeating;                       //Keep heart beating
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    ScheduledFuture<?> heartBeatHandler;
    /**************************************
     *             Thread Variables
     * *************************************
     */


    public WorkerThread(BlockingQueue<Packet> incomingPackets, BlockingQueue<Packet> outgoingPackets,
                        int deviceID, MainActivity mainActivity, WritingThread writingThread){

        this.incomingPackets = incomingPackets;
        this.outgoingPackets = outgoingPackets;
        this.deviceID = deviceID;
        this.writingThread = writingThread;
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {

        mainWorkerThread = this;
        threadIsAlive = true;
        peerIsAlive = false;
        startHeartBeat();


        while(threadIsAlive){
            try {
                Packet packet = incomingPackets.take();
                peerIsAlive = true;

                if(MainActivity.DEVICE_ID == packet.getDestId()){
                    byte tagByte = packet.getType();

                    switch(tagByte){
                        case DATA:
                            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(packet.payload));
                            DataPacket dataPacket = (DataPacket) objectInputStream.readObject();
                            debug(dataPacket.toString(), INFO);

                            File f = new File((MainActivity.rootDir + "File/" + dataPacket.getFilename()));

                            if(!f.exists()){
                                debug("Receiving file:" + dataPacket.getFilename(), DEBUG);
                                mainActivity.sendToCommandCenter("Receiving file:" + dataPacket.getFilename());
                                mainActivity.log("Receiving file:" + dataPacket.getFilename()+" of "+f.length()+" bytes");
                            }

                            FileOutputStream fileOutputStream = new FileOutputStream(f, true);
                            fileOutputStream.write(dataPacket.getData(), 0, packet.getLength());

                            if(f.length() >= dataPacket.getFileLength()){
                                mainActivity.log("Received "+dataPacket.getFilename()+" Fully");

                                mainActivity.sendToCommandCenter("Received "+dataPacket.getFilename()+" of "+f.length()+" bytes Fully");
                                debug("Received " + dataPacket.getFilename()+" Fully", DEBUG);

                                f.delete();
                                fileOutputStream.close();
                            }
                            objectInputStream.close();
                            break;

                        case STREAM:
                            debug("Stream of bytes of length "+packet.payload, DEBUG);
                            break;



                        case HEARTBEAT:
                            debug("Got a heartbeat", DEBUG);
                            break;
                        case TERMINATE:
                            debug("Terminate connection", DEBUG);
                            cancel();
                            break;

                        case DELETE:
                            f = new File((MainActivity.rootDir + "File/" +"5MB"));
                            mainActivity.sendToCommandCenter("Got a DELETE Packet");
                            debug("Got a DELETE Packet", INFO);
                            if(f.exists()) {
                                mainActivity.log("File size before deleting: " + f.length());
                                debug("File size before deleting: " + f.length(), INFO);
                                f.delete();

                                mainActivity.sendToCommandCenter(MainActivity.rootDir + "File/5MB" + " has successfully been deleted");
                            }
                            else{
                                debug("5MB doesn't exist", INFO);
                                mainActivity.sendToCommandCenter("5MB doesn't exist");

                            }
                            break;
                    }

                }
                else{
//                    outgoingPackets.put(packet);
                    //Route
                    int nextHop = mainActivity.findRoute(packet.getDestId());
                    if(nextHop == -1){
                        //There is no route for this packet, discard the packet for now
                        debug("No route for "+packet.getDestId(), WARN);
                    }
                    else{
                        WorkerThread nextHopThread = mainActivity.getWorkingThread(nextHop);

                        if(nextHopThread != null){
                            nextHopThread.addToOutgoingStack(packet);
                        }
                        else{
                            debug("No worker thread for "+nextHop, ERROR);
                        }
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }catch (IOException e){
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    //
//    public void routePacket(Packet packet){
//
//    }
    public void addToOutgoingStack(Packet packet) {
        debug(packet.toString(), INFO);
        try {
            if(outgoingPackets != null) {
                outgoingPackets.put(packet);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    public void sendHeartBeat() {
//        mainActivity.debug("Sending Heart-Beat " + deviceID);
        Packet packet = new Packet(MainActivity.DEVICE_ID, deviceID, HEARTBEAT, 0, new byte[1]);
        if(heartBeating)
            addToOutgoingStack(packet);
    }


    Runnable sendHeartBeat = new Runnable() {
        @Override
        public void run() {
            //Check if peer is alive after 4 turns (4 seconds as each turn runs after a second)
            if(counter >= 4) {
                if(peerIsAlive){
                    debug(deviceID+" is alive", INFO);
                }
                else {
                    debug(deviceID+" is dead", WARN);
                    mainWorkerThread.cancel();

                    return;
                }
                counter = 0;
                peerIsAlive = false;
            }
            sendHeartBeat(); //Send a heart beat
//            if(heartBeating) {
//                debug("Planned next heart-beat for " + deviceID, INFO);
////                handler.postDelayed(this, 1000);
//            }
            counter++;
        }
    };


    public void stopHeartBeat(){
        debug("Stopping heart-beat to "+deviceID, INFO);
//        synchronized (timer) {
//            timer.cancel();
//            timer.purge();
//        }
        heartBeatHandler.cancel(true);
        heartBeating = false;

    }

    public void startHeartBeat(){
        debug("Starting heart-beat to "+deviceID, INFO);
        heartBeating = true;

        heartBeatHandler = scheduler.scheduleAtFixedRate(sendHeartBeat, 10, 1000, TimeUnit.MILLISECONDS);
//        if(timer == null){
//            timer = new Timer(true);
//        }
//
//        synchronized (timer) {
//
//            timer = new Timer(true);
//            try {
//                timer.scheduleAtFixedRate(sendHeartBeat, 500, 1000);
//            } catch (IllegalStateException e) {
//                e.printStackTrace();
//            }
//        }
    }

    public void sendDeletePacket(int nodeID){

        Packet packet = new Packet(MainActivity.DEVICE_ID, nodeID, DELETE, 0, new byte[1]);
        int nextHop = mainActivity.findRoute(packet.getDestId());
        if(nextHop == -1){
            //There is no route for this packet, discard the packet for now
            debug("No route to "+packet.getDestId(), WARN);
        }
        else{
            WorkerThread nextHopThread = mainActivity.getWorkingThread(nextHop);
            if(nextHopThread != null){
                nextHopThread.addToOutgoingStack(packet);
            }

        }

    }

    private class SendFileRunnable implements Runnable{
        int nodeID;
        String fileName;
        public SendFileRunnable(int nodeID, String filename){
            this.nodeID = nodeID;
            this.fileName = filename;
        }

        @Override
        public void run() {
            try {
                debug("Sending "+fileName+" to "+nodeID, DEBUG);
                FileInputStream inputStream = new FileInputStream((MainActivity.rootDir +"/File/" +fileName));
                int FileSize = inputStream.available();
                int chunkSize = 10 * 1024, len; //10 KB
                byte[] buf = new byte[chunkSize];
                mainActivity.log("Sending file " + fileName + " to " + nodeID);
                while ((len = inputStream.read(buf)) > 0) {


                    DataPacket dataPacket = new DataPacket(fileName, FileSize, buf);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutput output = new ObjectOutputStream(bos);
                    output.writeObject(dataPacket);
                    byte[] objectToArray = bos.toByteArray();

                    Packet packet = new Packet(MainActivity.DEVICE_ID, nodeID, DATA, len, objectToArray);

                    int nextHop = mainActivity.findRoute(packet.getDestId());
                    if(nextHop == -1){
                        //There is no route for this packet, discard the packet for now
                        debug("No route to "+packet.getDestId(), WARN);
                    }
                    else{
                        WorkerThread nextHopThread = mainActivity.getWorkingThread(nextHop);
                        if(nextHopThread != null){
                            nextHopThread.addToOutgoingStack(packet);
                        }

                    }
                }

                debug("DONE Sending "+fileName+" to "+nodeID, DEBUG);
                mainActivity.log("DONE Sending " + fileName + " to " + nodeID);
                mainActivity.sendToCommandCenter("DONE Sending "+fileName+" to "+nodeID);


            }catch (IOException e) {
                debug("Failed to send "+fileName+" to "+nodeID, ERROR);
                e.printStackTrace();
            }
        }
    }
    public void sendFile(int nodeID, String fileName){

        SendFileRunnable sendFileRunnable = new SendFileRunnable(nodeID, fileName);
        Thread sendFileOnSeperateThread = new Thread(sendFileRunnable
        );
        sendFileOnSeperateThread.start();
    }
    /**
     //     * Function will check if we time sync packet is enabled and will work accordingly
     //     * @param nodeID
     //     * @param fileName
     //     */
//    public void sendFile(int nodeID, String fileName){
//
//        //Check if time sync packet is enabled, send the time sync packet
//        if(MainActivity.time_sync){
//            int routeID = MainActivity.routePacket(nodeID);
//            ByteArrayOutputStream packet = new ByteArrayOutputStream();
//            DataOutputStream packetStream = new DataOutputStream(packet);
//
//            try {
//                byte[] fileName_bytes = fileName.getBytes();
//
//                packetStream.writeInt(1+4+4+fileName_bytes.length+4);
//                packetStream.writeInt(MainActivity.DEVICE_ID);
//                packetStream.writeInt(routeID);
//                packetStream.write(RTT);
//                packetStream.writeInt(MainActivity.DEVICE_ID);
//                packetStream.writeInt(fileName_bytes.length);
//                packetStream.write(fileName_bytes);
//                packetStream.writeInt(nodeID);
//
//                t_initial_start = System.currentTimeMillis();
//                writeToNode(routeID, packet.toByteArray());
//
//                connectionEstablishment = 0;
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        //if time sync packet is not enabled send the file directly
//        else{
//            writeFile(nodeID, fileName);
//        }
//    }


    private class SendStreamRunnable implements Runnable{
        int nodeID;
        public SendStreamRunnable(int nodeID){
            this.nodeID = nodeID;
        }
        @Override
        public void run() {
            stopHeartBeat();
            mainActivity.debug("Sending Stream to " + nodeID);
            StreamData = true;

            int chunkSize = 10240, len; //10 KB
            byte[] buf = new byte[chunkSize];
            new Random().nextBytes(buf);

            while (threadIsAlive && StreamData) {
                Packet packet = new Packet(MainActivity.DEVICE_ID, nodeID, STREAM, buf.length, buf);
                int nextHop = mainActivity.findRoute(packet.getDestId());
                if(nextHop == -1){
                    //There is no route for this packet, discard the packet for now
                }
                else{
                    WorkerThread nextHopThread = mainActivity.getWorkingThread(nextHop);
                    if(nextHopThread != null){
                        nextHopThread.addToOutgoingStack(packet);
                    }
                }

            }

            startHeartBeat();
        }
    }


    /**
     * Send file routine, the file is cut to 10 kb chunks.
     * Each 10 KB is encapsulated with additional info and sent to the destination node
     * @param nodeID

     */
    public void sendStream(int nodeID) {
        SendStreamRunnable sendStreamRunnable = new SendStreamRunnable(nodeID);
        Thread sendStreamOnSeperateThread = new Thread(sendStreamRunnable);
        sendStreamOnSeperateThread.start();

    }





    public void stopStream(){
        StreamData = false;
    }

    public void debug(String msg, int type){

        switch (type){
            case DEBUG:
                Log.d(TAG, msg);
                break;
            case WARN:
                Log.w(TAG, msg);
                break;
            case INFO:
                Log.i(TAG, msg);
                break;
            case ERROR:
                Log.e(TAG, msg);
                break;
        }

    }

    public void cancel(){
        debug("Closing Working Thread of node "+deviceID, INFO);


        if(threadIsAlive) {
            threadIsAlive = false;
            stopHeartBeat();
//            Packet packet = new Packet(deviceID, MainActivity.DEVICE_ID, TERMINATE, 0, new byte[1]);
//            addToOutgoingStack(packet);

            incomingPackets.clear();
            mainActivity.removeNode(deviceID);
        }
    }
}
