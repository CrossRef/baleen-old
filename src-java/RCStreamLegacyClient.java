package baleen;

import io.socket.*;
import java.net.URISyntaxException;
import java.net.MalformedURLException;
import java.util.*;
import org.json.*;
import clojure.lang.IFn;

/* RCStream client using legacy 0.9 websocket.io */
public class RCStreamLegacyClient {
	private IFn callback;
    private String subscribe;
    private SocketIO socket;

	public RCStreamLegacyClient(IFn callback, String subscribe) {
		this.callback = callback;
        this.subscribe = subscribe;
	}

    public void reconnect() {
        this.socket.reconnect();
    }

    public void stop() {
        this.socket.disconnect();
    }
	
	public void run() throws URISyntaxException, MalformedURLException {
		this.socket = new SocketIO("http://stream.wikimedia.org/rc");
        this.socket.connect(
        	new IOCallback() {
            @Override
            public void onMessage(JSONObject json, IOAcknowledge ack) {
            }

            @Override
            public void onMessage(String data, IOAcknowledge ack) {
            }

            @Override
            public void onError(SocketIOException socketIOException) {
                System.out.println("an Error occured");
                socketIOException.printStackTrace();
            }

            @Override
            public void onDisconnect() {
                System.out.println("Connection terminated.");
            }

            @Override
            public void onConnect() {
                System.out.println("Connection established");
                socket.emit("subscribe", RCStreamLegacyClient.this.subscribe);
                System.out.println("Subscribed");
            }

            @Override
            public void on(String event, IOAcknowledge ack, Object... args) {
                RCStreamLegacyClient.this.callback.invoke(event, args);
            }
        });
	}
}