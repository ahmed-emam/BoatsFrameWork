package ahmedemam.boatsframework;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;

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

    public WorkingThread(BlockingQueue<Packet> incomingPackets, BlockingQueue<Packet> outgoingPackets,
                         int deviceID, MainActivity mainActivity){
        this.incomingPackets = incomingPackets;
        this.outgoingPackets = outgoingPackets;
        this.deviceID = deviceID;
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {
        mainWorkingThread = this;
        threadIsAlive = true;
        peerIsAlive = false;
        timer = new Timer(true);
        timer.scheduleAtFixedRate(sendHeartBeat, 500 , 1000);



        while(threadIsAlive){
            try {
                Packet packet = incomingPackets.take();
                peerIsAlive = true;

                if(MainActivity.DEVICE_ID == packet.getDestId()){
                    byte tagByte = packet.getType();
                    if(packet.payload != null)
                        debug("Received " + packet.payload.length + " from peer", DEBUG);


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

    public void addToOutgoingStack(Packet packet) {
        debug("Packet from "+deviceID+" "+packet, DEBUG);
        try {
            if(outgoingPackets != null)
                outgoingPackets.put(packet);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void sendHeartBeat() {
        mainActivity.debug("Sending Heart-Beat");
        Packet packet = new Packet(MainActivity.DEVICE_ID, deviceID, HEARTBEAT, 0, new byte[1]);
        addToOutgoingStack(packet);
    }

    TimerTask sendHeartBeat = new TimerTask() {
        @Override
        public void run() {
            //Check if peer is alive after 4 turns (4 seconds as each turn runs after a second)
            if(counter >= 4) {
                if(peerIsAlive){
                    mainActivity.debug("Peer is alive");
                }
                else {
                    mainActivity.debug("Peer is dead");
                    mainWorkingThread.cancel();
                    return;
                }
                counter = 0;
                peerIsAlive = false;
            }
            sendHeartBeat(); //Send a heart beat
            counter++;
        }
    };


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
        debug("Closing Working Thread", INFO);


        if(threadIsAlive) {
            threadIsAlive = false;
            timer.cancel();
            Packet packet = new Packet(deviceID, MainActivity.DEVICE_ID, TERMINATE, 0, new byte[1]);
            addToOutgoingStack(packet);

            incomingPackets.clear();
            mainActivity.removeNode(deviceID);
        }
    }
}
