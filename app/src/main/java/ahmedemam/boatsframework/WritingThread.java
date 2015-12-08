package ahmedemam.boatsframework;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;

import ahmedemam.boatsframework.model.Packet;

/**
 * Created by aemam on 12/7/15.
 */
public class WritingThread extends Thread {
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



    private BlockingQueue<Packet> outgoingPackets;
    private static final String TAG = "Connection_Manager";
    private boolean threadIsAlive;
    private OutputStream outputStream;
    int deviceID = 0;

    public WritingThread(BlockingQueue<Packet> outgoingPackets, OutputStream outputStream, int deviceID){
        this.outgoingPackets = outgoingPackets;
        this.outputStream = outputStream;
        this.deviceID = deviceID;
    }

    @Override
    public void run() {
        threadIsAlive = true;

        while(threadIsAlive){
            try {
                Packet outgoingPacket = outgoingPackets.take();
                if(outgoingPacket.getType() == TERMINATE){

                    cancel();
                }
                else{
                    ObjectOutput output = new ObjectOutputStream(outputStream);
                    output.writeObject(outgoingPacket);
                }


            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                cancel();
                e.printStackTrace();
            }
        }
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

        debug("Closing Writing Thread", INFO);
        threadIsAlive = false;
        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        outgoingPackets.clear();
    }
}
