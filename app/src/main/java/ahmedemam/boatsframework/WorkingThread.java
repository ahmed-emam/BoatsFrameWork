package ahmedemam.boatsframework;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
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
public class WorkingThread extends Thread {
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


    /**************************************
     *             Packet Types
     * *************************************
     */


    private BlockingQueue<Packet> incomingPackets;
    private BlockingQueue<Packet> outgoingPackets;
    private static final String TAG = "Connection_Manager";
    private boolean threadIsAlive;
    int deviceID = 0;
    MainActivity mainActivity;
    private boolean peerIsAlive;
    private int counter = 0;
    WorkingThread mainWorkingThread;
    private Timer timer;
    private WritingThread writingThread;
    private boolean StreamData;
    private boolean heartBeating;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    ScheduledFuture<?> heartBeatHandler;

    public WorkingThread(BlockingQueue<Packet> incomingPackets, BlockingQueue<Packet> outgoingPackets,
                         int deviceID, MainActivity mainActivity, WritingThread writingThread){
        this.incomingPackets = incomingPackets;
        this.outgoingPackets = outgoingPackets;
        this.deviceID = deviceID;
        this.writingThread = writingThread;
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {

        mainWorkingThread = this;
        threadIsAlive = true;
        peerIsAlive = false;
        startHeartBeat();


        while(threadIsAlive){
            try {
                Packet packet = incomingPackets.take();
                peerIsAlive = true;

                if(MainActivity.DEVICE_ID == packet.getDestId()){
                    byte tagByte = packet.getType();
//                    if(packet.payload != null)
//                        debug("Received " + packet.payload.length + " from peer", DEBUG);


                    switch(tagByte){
                        case DATA:
                            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(packet.payload));
                            DataPacket dataPacket = (DataPacket) objectInputStream.readObject();

                            File f = new File((MainActivity.rootDir + dataPacket.getFilename()));
                            FileOutputStream fileOutputStream = new FileOutputStream(f, true);
                            fileOutputStream.write(dataPacket.getData());

                            if(f.length() == dataPacket.getFileLength()){
                                debug("Received "+dataPacket.getFilename()+" Fully", DEBUG);
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
                    }

                }
                else{
//                    outgoingPackets.put(packet);
                    //Route
                    int nextHop = mainActivity.findRoute(packet.getDestId());
                    if(nextHop == -1){
                        //There is no route for this packet, discard the packet for now
                    }
                    else{
                        WorkingThread nextHopThread = mainActivity.getWorkingThread(nextHop);
                        if(nextHopThread != null){
                            nextHopThread.addToOutgoingStack(packet);
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
            if(outgoingPackets != null)
                outgoingPackets.put(packet);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    public void sendHeartBeat() {
        mainActivity.debug("Sending Heart-Beat " + deviceID);
        Packet packet = new Packet(MainActivity.DEVICE_ID, deviceID, HEARTBEAT, 0, new byte[1]);
        if(heartBeating)
            addToOutgoingStack(packet);
    }

//    TimerTask sendHeartBeat = new TimerTask() {
//        @Override
//        public void run() {
//            //Check if peer is alive after 4 turns (4 seconds as each turn runs after a second)
//            if(counter >= 4) {
//                if(peerIsAlive){
//                    debug(deviceID+" is alive", INFO);
//                }
//                else {
//                    debug(deviceID+" is dead", WARN);
//                    mainWorkingThread.cancel();
//                    this.cancel();
//                    return;
//                }
//                counter = 0;
//                peerIsAlive = false;
//            }
//            sendHeartBeat(); //Send a heart beat
//            counter++;
//        }
//    };

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
                    mainWorkingThread.cancel();

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
                    WorkingThread nextHopThread = mainActivity.getWorkingThread(nextHop);
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

//        stopHeartBeat();
//        mainActivity.debug("Sending Stream to " + nodeID);
//        StreamData = true;
//
//        int chunkSize = 10240, len; //10 KB
//        byte[] buf = new byte[chunkSize];
//        new Random().nextBytes(buf);
//
//        while (threadIsAlive && StreamData) {
//            Packet packet = new Packet(MainActivity.DEVICE_ID, nodeID, STREAM, buf.length, buf);
//            int nextHop = mainActivity.findRoute(packet.getDestId());
//            if(nextHop == -1){
//                //There is no route for this packet, discard the packet for now
//            }
//            else{
//                WorkingThread nextHopThread = mainActivity.getWorkingThread(nextHop);
//                if(nextHopThread != null){
//                    nextHopThread.addToOutgoingStack(packet);
//                }
//            }
//
//        }
//
//        startHeartBeat();
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
            Packet packet = new Packet(deviceID, MainActivity.DEVICE_ID, TERMINATE, 0, new byte[1]);
            addToOutgoingStack(packet);

            incomingPackets.clear();
            mainActivity.removeNode(deviceID);
        }
    }
}
