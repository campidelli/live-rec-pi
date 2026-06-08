package campidelli.liverecpi.mixer.adapters.outbound.xr18;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;

import com.illposed.osc.OSCMessage;

import campidelli.liverecpi.mixer.adapters.outbound.network.OscMessageSerializer;
import campidelli.liverecpi.mixer.domain.model.Channel;
import campidelli.liverecpi.mixer.domain.model.MixerDescriptor;
import campidelli.liverecpi.mixer.ports.outbound.FetchMixerChannelsPort;
import campidelli.liverecpi.mixer.ports.outbound.GetMixerDescriptorPort;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Xr18FetchMixerChannelsAdapter implements FetchMixerChannelsPort {

    private static final Logger log = LoggerFactory.getLogger(Xr18FetchMixerChannelsAdapter.class);
    private static final int SOCKET_TIMEOUT_MS = 600;
    private static final String[] COLOR_HEX_CODES = {
            "#000000",
            "#ff0000",
            "#00ff00",
            "#ffff00",
            "#0000ff",
            "#ff00ff",
            "#00ffff",
            "#ffffff"
    };

    private final OscMessageSerializer serializer;
    private final GetMixerDescriptorPort descriptorPort;

    public Xr18FetchMixerChannelsAdapter(OscMessageSerializer serializer, GetMixerDescriptorPort descriptorPort) {
        this.serializer = serializer;
        this.descriptorPort = descriptorPort;
    }

    @Override
    public String mixerType() {
        return descriptorPort.getDescriptor().type();
    }

    @Override
    public List<Channel> fetchChannels(String ipAddress, int port) {
        MixerDescriptor descriptor = descriptorPort.getDescriptor();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(ipAddress);

            return java.util.stream.IntStream.rangeClosed(1, descriptor.numberOfChannels())
                    .mapToObj(index -> fetchChannel(socket, address, port, index))
                    .toList();
        } catch (IOException exception) {
            throw new RuntimeException("Failed communicating with XR18 mixer at " + ipAddress + ":" + port, exception);
        }
    }

    private Channel fetchChannel(DatagramSocket socket, InetAddress address, int port, int index) {
        String channelIndex = String.format("%02d", index);
        Channel channel = new Channel(index);

        String messagePath = index == 17 ? "/rtn/aux/config/" : "/ch/" + channelIndex + "/config/";

        OSCMessage nameResponse = sendOscMessage(socket, address, port, new OSCMessage(messagePath + "name"));
        if (nameResponse != null && !nameResponse.getArguments().isEmpty()) {
            channel.rename((String) nameResponse.getArguments().get(0));
        }

        OSCMessage colorResponse = sendOscMessage(socket, address, port, new OSCMessage(messagePath + "color"));
        if (colorResponse != null && !colorResponse.getArguments().isEmpty()) {
            int colorValue = (Integer) colorResponse.getArguments().get(0);
            channel.recolor(toChannelColor(colorValue));
        }

        return channel;
    }

    private OSCMessage sendOscMessage(DatagramSocket socket, InetAddress address, int port, OSCMessage message) {
        try {
            byte[] payload = serializer.serialize(message);
            DatagramPacket outputPacket = new DatagramPacket(payload, payload.length, address, port);
            socket.send(outputPacket);

            byte[] inputBuffer = new byte[1024];
            DatagramPacket inputPacket = new DatagramPacket(inputBuffer, inputBuffer.length);
            socket.receive(inputPacket);

            return serializer.parse(inputPacket.getData(), inputPacket.getLength());
        } catch (SocketTimeoutException exception) {
            return null;
        } catch (Exception exception) {
            log.warn("OSC request failed. address={}, port={}, path={}",
                    address.getHostAddress(),
                    port,
                    message.getAddress(),
                    exception);
            return null;
        }
    }

    private Channel.Color toChannelColor(int colorValue) {
        String hexCode = COLOR_HEX_CODES[Math.floorMod(colorValue, COLOR_HEX_CODES.length)];
        boolean inverted = colorValue >= COLOR_HEX_CODES.length;
        return new Channel.Color(hexCode, inverted);
    }
}
