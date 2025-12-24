package game.engine.gateway.codec;

public class Packet {
    private int msgId;
    private byte[] body;

    public Packet(int msgId, byte[] body) {
        this.msgId = msgId;
        this.body = body;
    }

    public int getMsgId() {
        return msgId;
    }

    public void setMsgId(int msgId) {
        this.msgId = msgId;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
