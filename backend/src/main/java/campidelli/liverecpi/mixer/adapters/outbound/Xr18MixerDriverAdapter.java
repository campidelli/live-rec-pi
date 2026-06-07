package campidelli.liverecpi.mixer.adapters.outbound;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

import com.illposed.osc.OSCMessage;

import campidelli.liverecpi.mixer.adapters.outbound.network.OscMessageSerializer;
import campidelli.liverecpi.mixer.adapters.outbound.network.UdpOscMixerScanner;
import campidelli.liverecpi.mixer.domain.model.Channel;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer.AvailableMixer;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer.UnavailableMixer;
import campidelli.liverecpi.mixer.domain.model.MixerConnection;
import campidelli.liverecpi.mixer.domain.model.MixerDescriptor;
import campidelli.liverecpi.mixer.ports.outbound.MixerDiscoveryPort;
import campidelli.liverecpi.mixer.ports.outbound.MixerGatewayPort;
import campidelli.liverecpi.mixer.ports.outbound.MixerRegistryPort;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Xr18MixerDriverAdapter implements MixerRegistryPort, MixerDiscoveryPort, MixerGatewayPort {

  private static final Logger log = LoggerFactory.getLogger(Xr18MixerDriverAdapter.class);
  private static final int SOCKET_TIMEOUT_MS = 200;
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

  private final UdpOscMixerScanner scanner;
  private final OscMessageSerializer serializer;

  public Xr18MixerDriverAdapter(UdpOscMixerScanner scanner, OscMessageSerializer serializer) {
    this.scanner = scanner;
    this.serializer = serializer;
  }

  @Override
  public MixerDescriptor getDescriptor() {
      return new MixerDescriptor("XR18", "Behringer", "X Air XR18", 18);
  }

  @Override
  public List<Channel> fetchChannels(String ipAddress, int port) {
    MixerDescriptor descriptor = getDescriptor();

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

  @Override
  public List<DiscoveredMixer> discover() {
    MixerDescriptor descriptor = getDescriptor();
    byte[] payload = serializer.serialize(new OSCMessage("/xinfo"));

    List<AvailableMixer> availableMixers = scanner.discover(descriptor, payload, 10024);
    if (availableMixers.isEmpty()) {
        return List.of(new UnavailableMixer(descriptor));
    }

    return availableMixers.stream()
      .map(availableMixer -> (DiscoveredMixer) new AvailableMixer(
            new MixerDescriptor(
                descriptor.type(),
                descriptor.vendor(),
                extractNameFromSignature(availableMixer.signatureParts()), // update the name based on the signature
                descriptor.numberOfChannels()),
            availableMixer.signatureParts(),
            availableMixer.connection()))
        .toList();
  }

  private String extractNameFromSignature(List<String> signatureParts) {
    // For XR18, we expect the signature to contain the name as the second part of the signature
    return signatureParts.get(1);
  }

  private Channel fetchChannel(DatagramSocket socket, InetAddress address, int port, int index) {
    String channelIndex = String.format("%02d", index);
    Channel channel = new Channel(index);

    OSCMessage nameResponse = sendOscMessage(socket, address, port, new OSCMessage("/ch/" + channelIndex + "/config/name"));
    if (nameResponse != null && !nameResponse.getArguments().isEmpty()) {
      channel.rename((String) nameResponse.getArguments().get(0));
    }

    OSCMessage colorResponse = sendOscMessage(socket, address, port, new OSCMessage("/ch/" + channelIndex + "/config/color"));
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
    } catch (Exception exception) {
      log.warn("OSC request failed. address={}, port={}, path={}", address.getHostAddress(), port, message.getAddress(), exception);
      return null;
    }
  }

  private Channel.Color toChannelColor(int colorValue) {
    String hexCode = COLOR_HEX_CODES[Math.floorMod(colorValue, COLOR_HEX_CODES.length)];
    boolean inverted = colorValue >= COLOR_HEX_CODES.length;
    return new Channel.Color(hexCode, inverted);
  }
}
