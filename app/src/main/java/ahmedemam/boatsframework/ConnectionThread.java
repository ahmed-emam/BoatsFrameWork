package ahmedemam.boatsframework;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import ahmedemam.boatsframework.model.DataPacket;
import ahmedemam.boatsframework.model.Packet;

/**
 * Created by ahmedemam on 12/2/15.
 *
 *
 * NOT USING THIS THREAD RIGHT NOW
 */
public class ConnectionThread extends Thread{

    /**************************************
     *             Packet Types
     * *************************************
     */
    private static final byte DATA = 3;
    private static final byte STREAM = 2;
    private static final byte HEARTBEAT = 1;
    /**************************************
     *             Packet Types
     * *************************************
     */

    ConnectionThread mainConnectionThread;
    private static final String TAG = "Connection_Manager";
    InputStream inputStream;
    OutputStream outputStream;
    boolean isClient;
    int deviceID = 0;
    boolean engineRunning;
    MainActivity mainActivity;
    private int numHeartBeats = 0;
    private int counter = 0;
    private Object incrementSynchronizationLock = new Object();
    private Object writeSynchronizationLock = new Object();

    private Logger logger = Logger.getLogger(MainActivity.class.getPackage().getName());
//    private Handler handler;
    private Timer timer;


    private boolean StreamData;
    private boolean peerIsAlive = false;
    public void debug(String msg){
        Log.d(TAG, msg);
    }

    public ConnectionThread( InputStream inStream, OutputStream outStream, boolean client_tmp, int deviceID){
        mainConnectionThread = this;
        this.inputStream = inStream;
        this.outputStream = outStream;
        this.isClient = client_tmp;
        this.deviceID = deviceID;
        engineRunning = true;

    }


    public ConnectionThread(MainActivity mainActivity,  InputStream inStream, OutputStream outStream, boolean client_tmp, int deviceID){
        mainConnectionThread = this;
        this.mainActivity = mainActivity;

        this.inputStream = inStream;
        this.outputStream = outStream;
        this.isClient = client_tmp;
        this.deviceID = deviceID;
        engineRunning = true;

    }

    @Override
    public void run() {
        timer = new Timer(true);
        timer.scheduleAtFixedRate(sendHeartBeat, 500 , 1000);

//        Looper.prepare();
//        Looper.loop();
//        handler = new Handler();

//        handler.postDelayed(sendHeartBeat, 1000);

//        if(!isClient){
//            handler.removeCallbacks(sendHeartBeat);
//            sendStream(deviceID);
//            handler.postDelayed(sendHeartBeat, 1000);
//        }

        while(engineRunning){

            try {
                ObjectInput deSerializeObject = new ObjectInputStream(inputStream);
                Packet readPacket = (Packet) deSerializeObject.readObject();

                peerIsAlive = true;

                if(MainActivity.DEVICE_ID == readPacket.getDestId()){
                    byte tagByte = readPacket.getType();
                    if(readPacket.payload != null)
                        debug("Received " + readPacket.payload.length + " from peer");


                    switch(tagByte){
                        case DATA:
                            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(readPacket.payload));
                            DataPacket dataPacket = (DataPacket) objectInputStream.readObject();

                            File f = new File((MainActivity.rootDir + dataPacket.getFilename()));
                            FileOutputStream fileOutputStream = new FileOutputStream(f, true);
                            fileOutputStream.write(dataPacket.getData());

                            if(f.length() == dataPacket.getFileLength()){
                                debug("Received "+dataPacket.getFilename()+" Fully");
                                fileOutputStream.close();
                            }

                            objectInputStream.close();
                            break;

                        case STREAM:
                            debug("Stream of bytes of length "+readPacket.payload);
                            break;

                        case HEARTBEAT:
                            debug("Got a heartbeat");
                            break;
                    }

                }


            }
            catch(ClassNotFoundException exp){
                logger.log(Level.WARNING, "Cannot perform input. Class not found", exp);
            }
            catch(IOException exp){

                logger.log(Level.WARNING, "Cannot perform input.", exp);
                cancel();
            }
        }



    }




    /**
     * Send file routine, the file is cut to 10 kb chunks.
     * Each 10 KB is encapsulated with additional info and sent to the destination node
     * @param nodeID
     * @param fileName
     */
    public void sendFile(int nodeID, String fileName) {
        mainActivity.debug("Sending file to " + nodeID);
        try {
            FileInputStream inputStream = new FileInputStream((MainActivity.rootDir + fileName));
            int FileSize = inputStream.available();
            int chunkSize = 10240, len; //10 KB
            byte[] buf = new byte[chunkSize];

            while ((len = inputStream.read(buf)) > 0) {

                //Check if len is buf.length, so you dont need to copyOfRange
                byte[] data = Arrays.copyOfRange(buf, 0, len);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bos);
                DataPacket dataPacket = new DataPacket(fileName, FileSize, data);
                out.writeObject(dataPacket);
                byte[] dataPacketToBytes = bos.toByteArray();
                out.close();

                Packet packet = new Packet(MainActivity.DEVICE_ID, nodeID, DATA, dataPacketToBytes.length, dataPacketToBytes);

                sendPacket(packet);
//                    ObjectOutput output = new ObjectOutputStream(outputStream);
//                    output.writeObject(packet);

            }
            mainActivity.debug("Done writing file " + fileName);


        }catch(IOException e){
            cancel();
            e.printStackTrace();
        }

    }


    /**
     * Send file routine, the file is cut to 10 kb chunks.
     * Each 10 KB is encapsulated with additional info and sent to the destination node
     * @param nodeID

     */
    public void sendStream(int nodeID) {
        mainActivity.debug("Sending Stream to " + nodeID);
        StreamData = true;
//        try {
        int chunkSize = 10240, len; //10 KB
        byte[] buf = new byte[chunkSize];
        new Random().nextBytes(buf);

        while (engineRunning &&  StreamData) {
            Packet packet = new Packet(MainActivity.DEVICE_ID, nodeID, STREAM, buf.length, buf);
            sendPacket(packet);
            debug("Sending stream");
        }
    }

    public void stopStream(){
        StreamData = false;
    }

    public synchronized void sendPacket(Packet packet){
        try {
            ObjectOutput output = new ObjectOutputStream(outputStream);
            output.writeObject(packet);
        }catch(IOException e){

            cancel();
            e.printStackTrace();
        }
    }

    /**
     * Sending heart beat packets to the peer
     * If sending a heart beat fails then we lost the connection
     */
    public void sendHeartBeat() {
        mainActivity.debug("Sending Heart-Beat");
        Packet packet = new Packet(MainActivity.DEVICE_ID, deviceID, HEARTBEAT, 0, new byte[1]);
        sendPacket(packet);
    }

//    Runnable sendHeartBeat = new Runnable() {
//        @Override
//        public void run() {
//            //Check how many heartbeats I got after 4 turns (4 seconds as each turn runs after a second)
//            if(counter >= 4) {
//                if(peerIsAlive){
//                    mainActivity.debug("Peer is alive");
//                }
//                else{
//                    mainActivity.debug("Peer is dead");
//                    mainConnectionThread.cancel();
//                    return;
//                }
//                debug("Have seen him enough, r");
//                counter = 0;
//                peerIsAlive = false;
//            }
//            sendHeartBeat(); //Send a heart beat
//            counter++;
//            handler.postDelayed(this, 1000);
//        }
//    };
//
    TimerTask sendHeartBeat = new TimerTask() {
        @Override
        public void run() {
            //Check how many heartbeats I got after 4 turns (4 seconds as each turn runs after a second)
            if(counter >= 4) {
                if(peerIsAlive){
                    mainActivity.debug("Peer is alive");
                }
                else{
                    mainActivity.debug("Peer is dead");
                    mainConnectionThread.cancel();
                    return;
                }
                debug("Have seen him enough, r");
                counter = 0;
                peerIsAlive = false;
            }
            sendHeartBeat(); //Send a heart beat
            counter++;
        }
    };


    public void cancel(){

        if(engineRunning) {
            timer.cancel();

            stopStream();
            engineRunning = false;


            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException exp) {
                logger.log(Level.SEVERE, "Couldnt close the stream. ", exp);
            }
            Log.e(MainActivity.TAG, "Disconnecting from " + deviceID);
            if (mainActivity != null) {
                mainActivity.debug("Disconnecting from " + deviceID);
                mainActivity.removeNode(deviceID);
            }
            mainActivity = null;
        }

    }

}
