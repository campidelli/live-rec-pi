package campidelli.liverecpi.mixer.adapters.outbound.xr18;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.illposed.osc.OSCMessage;

import campidelli.liverecpi.mixer.adapters.outbound.network.OscMessageSerializer;
import campidelli.liverecpi.mixer.domain.model.ChannelMeterLevel;
import campidelli.liverecpi.mixer.ports.outbound.FetchMixerMeterLevelsPort;
import campidelli.liverecpi.mixer.ports.outbound.GetMixerDescriptorPort;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Xr18FetchMixerMeterLevelsAdapter implements FetchMixerMeterLevelsPort {

    private static final Logger log = LoggerFactory.getLogger(Xr18FetchMixerMeterLevelsAdapter.class);
    private static final int SOCKET_TIMEOUT_MS = 600;
    private static final long XREMOTE_REFRESH_MS = 5000L;

    private final OscMessageSerializer serializer;
    private final GetMixerDescriptorPort descriptorPort;
    private final Map<String, MeterSession> meterSessions = new ConcurrentHashMap<>();

    public Xr18FetchMixerMeterLevelsAdapter(OscMessageSerializer serializer, GetMixerDescriptorPort descriptorPort) {
        this.serializer = serializer;
        this.descriptorPort = descriptorPort;
    }

    @Override
    public String mixerType() {
        return descriptorPort.getDescriptor().type();
    }

    @Override
    public List<ChannelMeterLevel> fetchMeterLevels(String ipAddress, int port) {
        int totalChannels = descriptorPort.getDescriptor().numberOfChannels();
        String sessionKey = ipAddress + ":" + port;

        MeterSession meterSession = meterSessions.computeIfAbsent(sessionKey,
                ignored -> openMeterSession(ipAddress, port));

        synchronized (meterSession) {
            DatagramSocket socket = meterSession.socket();
            InetAddress address = meterSession.address();

            long now = System.currentTimeMillis();
            if (now - meterSession.lastXremoteAt() >= XREMOTE_REFRESH_MS) {
                sendOscMessage(socket, address, port, new OSCMessage("/xremote"), false);
                sendOscMessage(socket, address, port, new OSCMessage("/meters", List.of("/meters/1", 8)), false);
                sendOscMessage(socket, address, port, new OSCMessage("/meters", List.of("/meters/0", 8)), false);
                meterSession.touchXremote(now);
            }

            OSCMessage meterResponse = receiveMeterPacket(socket);
            if (meterResponse == null || meterResponse.getArguments().isEmpty()) {
                return emptyLevels(totalChannels);
            }

            byte[] meterBlob = extractMeterBlob(meterResponse.getArguments());
            if (meterBlob != null) {
                List<ChannelMeterLevel> levels = decodeInt16MeterBlob(meterBlob, totalChannels);
                return levels != null ? levels : emptyLevels(totalChannels);
            }

            Object firstArgument = meterResponse.getArguments().get(0);
            if (firstArgument instanceof Number) {
                return decodeNumericArgs(meterResponse.getArguments(), totalChannels);
            }

            return emptyLevels(totalChannels);
        }
    }

    private MeterSession openMeterSession(String ipAddress, int port) {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(ipAddress);
            long now = System.currentTimeMillis();

            sendOscMessage(socket, address, port, new OSCMessage("/xremote"), false);
            sendOscMessage(socket, address, port, new OSCMessage("/meters", List.of("/meters/1", 8)), false);
            sendOscMessage(socket, address, port, new OSCMessage("/meters", List.of("/meters/0", 8)), false);

            return new MeterSession(socket, address, now);
        } catch (IOException exception) {
            throw new RuntimeException("Failed opening meter session for XR18 at " + ipAddress + ":" + port, exception);
        }
    }

    private OSCMessage sendOscMessage(
            DatagramSocket socket,
            InetAddress address,
            int port,
            OSCMessage message,
            boolean expectResponse) {
        try {
            byte[] payload = serializer.serialize(message);
            DatagramPacket outputPacket = new DatagramPacket(payload, payload.length, address, port);
            socket.send(outputPacket);

            if (!expectResponse) {
                return null;
            }

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

    private OSCMessage receiveMeterPacket(DatagramSocket socket) {
        try {
            byte[] inputBuffer = new byte[2048];
            DatagramPacket inputPacket = new DatagramPacket(inputBuffer, inputBuffer.length);
            OSCMessage fallbackMeterResponse = null;

            for (int attempt = 0; attempt < 4; attempt++) {
                socket.receive(inputPacket);
                OSCMessage response = serializer.parse(inputPacket.getData(), inputPacket.getLength());
                if (response != null && response.getAddress() != null && response.getAddress().startsWith("/meters")) {
                    if ("/meters/1".equals(response.getAddress())) {
                        return response;
                    }
                    if (fallbackMeterResponse == null) {
                        fallbackMeterResponse = response;
                    }
                }
            }

            return fallbackMeterResponse;
        } catch (SocketTimeoutException exception) {
            return null;
        } catch (Exception exception) {
            log.debug("Failed receiving/parsing meter packet", exception);
            return null;
        }
    }

    private List<ChannelMeterLevel> decodeInt16MeterBlob(byte[] meterBlob, int totalChannels) {
        final int bytesPerSample = Short.BYTES;

        int offset = (meterBlob.length >= 4 && ((meterBlob.length - 4) % bytesPerSample == 0)) ? 4 : 0;
        int availableSamples = (meterBlob.length - offset) / bytesPerSample;
        if (availableSamples <= 0) {
            return null;
        }

        int stride = availableSamples >= totalChannels * 8 ? 8 : 1;
        int decodableChannels = Math.min(totalChannels, availableSamples / stride);

        ByteBuffer buffer = ByteBuffer.wrap(meterBlob).order(ByteOrder.LITTLE_ENDIAN);
        List<ChannelMeterLevel> levels = new ArrayList<>(totalChannels);
        for (int channel = 0; channel < decodableChannels; channel++) {
            int sampleIndex = channel * stride;
            int byteIndex = offset + (sampleIndex * bytesPerSample);
            short raw = buffer.getShort(byteIndex);
            float db = raw / 256.0f;
            levels.add(new ChannelMeterLevel(channel + 1, db));
        }
        for (int channel = decodableChannels + 1; channel <= totalChannels; channel++) {
            levels.add(new ChannelMeterLevel(channel, 0.0f));
        }

        return levels;
    }

    private byte[] extractMeterBlob(List<Object> arguments) {
        for (Object argument : arguments) {
            if (argument instanceof byte[] meterBlob) {
                return meterBlob;
            }
            if (argument instanceof ByteBuffer buffer) {
                ByteBuffer duplicate = buffer.duplicate();
                byte[] meterBlob = new byte[duplicate.remaining()];
                duplicate.get(meterBlob);
                return meterBlob;
            }
        }
        return null;
    }

    private List<ChannelMeterLevel> decodeNumericArgs(List<Object> arguments, int totalChannels) {
        int availableValues = Math.min(totalChannels, arguments.size());
        List<ChannelMeterLevel> levels = new ArrayList<>(totalChannels);

        for (int index = 1; index <= availableValues; index++) {
            Object value = arguments.get(index - 1);
            float level = value instanceof Number number ? number.floatValue() : 0.0f;
            levels.add(new ChannelMeterLevel(index, level));
        }
        for (int index = availableValues + 1; index <= totalChannels; index++) {
            levels.add(new ChannelMeterLevel(index, 0.0f));
        }
        return levels;
    }

    private List<ChannelMeterLevel> emptyLevels(int totalChannels) {
        return java.util.stream.IntStream.rangeClosed(1, totalChannels)
                .mapToObj(index -> new ChannelMeterLevel(index, 0.0f))
                .toList();
    }

    private static final class MeterSession {
        private final DatagramSocket socket;
        private final InetAddress address;
        private long lastXremoteAt;

        private MeterSession(DatagramSocket socket, InetAddress address, long lastXremoteAt) {
            this.socket = socket;
            this.address = address;
            this.lastXremoteAt = lastXremoteAt;
        }

        private DatagramSocket socket() {
            return socket;
        }

        private InetAddress address() {
            return address;
        }

        private long lastXremoteAt() {
            return lastXremoteAt;
        }

        private void touchXremote(long timestamp) {
            this.lastXremoteAt = timestamp;
        }
    }
}
