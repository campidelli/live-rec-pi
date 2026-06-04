package campidelli.liverecpi.infrastructure.mixer;

import campidelli.liverecpi.application.mixer.MixerPort;
import campidelli.liverecpi.domain.mixer.Channel;
import campidelli.liverecpi.domain.mixer.ChannelColor;
import campidelli.liverecpi.domain.mixer.ChannelColor.Color;
import campidelli.liverecpi.domain.mixer.Mixer;
import campidelli.liverecpi.domain.mixer.ProbeSpecification;
import com.illposed.osc.ByteArrayListBytesReceiver;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCSerializerAndParserBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class BehringerXr18MixerAdapter implements MixerPort {

    private static final Logger LOG = LoggerFactory.getLogger(BehringerXr18MixerAdapter.class);

    private static final String MODEL_KEY = "BEHRINGER_XR18";
    private static final String DISPLAY_NAME = "Behringer X-Air XR18";
    private static final int TOTAL_CHANNELS = 18;
    private static final int TIMEOUT_MS = 200;

    @Override
    public String getModelKey() {
        return MODEL_KEY;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean supports(String sig) {
        return sig != null && sig.contains("XR18");
    }

    @Override
    public ProbeSpecification getProbeSpecification() {
        // We can use JavaOSC to serialize our discovery string nicely too
        byte[] payload = serializeOscMessage(new OSCMessage("/xinfo"));
        return new ProbeSpecification(10023, payload);
    }

    @Override
    public Mixer connect(String ipAddress, int port) {
        List<Channel> channels = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(ipAddress);

            for (int i = 1; i <= TOTAL_CHANNELS; i++) {
                String chIdx = String.format("%02d", i);

                // 1. Fetch channel name using library wrapper
                String nameAddress = "/ch/" + chIdx + "/config/name";
                OSCMessage nameResponse = sendOscMessage(socket, address, port, new OSCMessage(nameAddress));
                String channelName = (nameResponse != null && !nameResponse.getArguments().isEmpty())
                        ? (String) nameResponse.getArguments().get(0)
                        : "CH " + chIdx;

                // 2. Fetch channel color
                String colorAddress = "/ch/" + chIdx + "/config/color";
                OSCMessage colorResponse = sendOscMessage(socket, address, port, new OSCMessage(colorAddress));
                int colorRawValue = (colorResponse != null && !colorResponse.getArguments().isEmpty())
                        ? (Integer) colorResponse.getArguments().get(0)
                        : 0;

                // 3. Translate domain values
                Color color = Color.values()[colorRawValue % 8];
                boolean inverted = colorRawValue >= 8;

                channels.add(new Channel(i, channelName, "INPUT", new ChannelColor(color, inverted)));
            }

        } catch (IOException e) {
            LOG.error("Failed communicating with XR18 mixer. address={}, port={}", ipAddress, port, e);
            throw new RuntimeException("Failed communicating with XR18 mixer at " + ipAddress, e);
        }

        return new Mixer(MODEL_KEY, DISPLAY_NAME, ipAddress, port, channels);
    }

    private OSCMessage sendOscMessage(DatagramSocket socket, InetAddress address, int port, OSCMessage message) {
        try {
            // Serialize using JavaOSC engine
            byte[] outputBuffer = serializeOscMessage(message);
            DatagramPacket outPacket = new DatagramPacket(outputBuffer, outputBuffer.length, address, port);
            socket.send(outPacket);

            // Listen for response packet
            byte[] inputBuffer = new byte[1024];
            DatagramPacket inPacket = new DatagramPacket(inputBuffer, inputBuffer.length);
            socket.receive(inPacket);

            // Parse response packet using JavaOSC engine
            java.nio.ByteBuffer responseBuffer = java.nio.ByteBuffer.wrap(inPacket.getData(), 0, inPacket.getLength());
            OSCSerializerAndParserBuilder builder = new OSCSerializerAndParserBuilder();
            OSCPacket packet = builder.buildParser().convert(responseBuffer);
            if (packet instanceof OSCMessage oscMessage) {
                return oscMessage;
            }
        } catch (Exception e) {
            LOG.warn("OSC request failed. address={}, port={}, path={}",
                    address.getHostAddress(),
                    port,
                    message.getAddress(),
                    e);
        }
        return null;
    }

    private byte[] serializeOscMessage(OSCMessage message) {
        try {
            OSCSerializerAndParserBuilder builder = new OSCSerializerAndParserBuilder();
            ByteArrayListBytesReceiver bytesReceiver = new ByteArrayListBytesReceiver();
            builder.buildSerializer(bytesReceiver).write(message);
            return bytesReceiver.toByteArray();
        } catch (Exception e) {
            LOG.error("OSC serialization failed. path={}", message.getAddress(), e);
            throw new RuntimeException("OSC Serialization failure", e);
        }
    }
}