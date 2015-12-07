package ahmedemam.boatsframework.model;

import java.io.Serializable;

/**
 * Created by ahmedemam on 12/2/15.
 */
public class Packet implements Serializable {
    int sourceId;
    int destId;
    byte type;
    int length;
    public byte[] payload;
    public Packet(){
        sourceId = 0;
        destId = 0;
        type = 0;
    }
    public Packet(int sourceId, int destId, byte type, int length, byte[] payload){
        this.sourceId = sourceId;
        this.destId = destId;
        this.type = type;
        this.length = length;
        this.payload = payload;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getDestId() {
        return destId;
    }

    public byte getType() {
        return type;
    }

    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }

    public void setDestId(int destId) {
        this.destId = destId;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "| "+this.sourceId+" | "+this.destId+" | "+this.type+
                " | "+this.length+" bytes | "+this.payload;
    }
}
