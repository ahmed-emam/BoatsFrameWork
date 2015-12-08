package ahmedemam.boatsframework;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;

import ahmedemam.boatsframework.model.Packet;

/**
 * Created by aemam on 12/7/15.
 */
public class ReadingThread extends Thread{
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
    private static final String TAG = "Connection_Manager";
    private InputStream inputStream;
    private boolean threadIsAlive;
    MainActivity mainActivity;

    private int deviceID;
    public ReadingThread(BlockingQueue<Packet> incomingPackets, InputStream inputStream,
                         int deviceID, MainActivity mainActivity){
        this.incomingPackets = incomingPackets;
        this.inputStream = inputStream;
        this.deviceID = deviceID;
        this.mainActivity = mainActivity;
    }

    @Override
    public void run() {
        threadIsAlive = true;

        while(threadIsAlive){
            try {
                ObjectInput deSerializeObject = new ObjectInputStream(inputStream);
                Packet readPacket = (Packet) deSerializeObject.readObject();

                incomingPackets.put(readPacket);

            }  catch(ClassNotFoundException exp){
               debug(exp.toString(), WARN);
            } catch(IOException exp){
                debug(exp.toString(), WARN);
                cancel();
            }catch (InterruptedException e) {
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
        debug("Closing Reading Thread", INFO);
        threadIsAlive = false;
        Packet packet = new Packet(deviceID, MainActivity.DEVICE_ID, TERMINATE, 0, new byte[1]);
        try {
            incomingPackets.put(packet);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
