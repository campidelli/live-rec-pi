package campidelli.liverecpi.infrastructure.network;

import campidelli.liverecpi.application.mixer.DeviceDiscoveryPort;
import campidelli.liverecpi.domain.mixer.OnlineDevice;
import campidelli.liverecpi.domain.mixer.ProbeSpecification;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class UdpDeviceDiscoveryAdapter implements DeviceDiscoveryPort {

  private static final Logger log = LoggerFactory.getLogger(UdpDeviceDiscoveryAdapter.class);
  private static final int TIMEOUT_MS = 1000;

  @Override
  public List<OnlineDevice> discoverOnlineDevices(Collection<ProbeSpecification> specs) {
    List<OnlineDevice> discoveredDevices = new ArrayList<>();
    List<InetAddress> broadcastAddresses = getBroadcastAddresses();

    log.debug("Starting OSC discovery scan across targets: {}", broadcastAddresses);

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(TIMEOUT_MS);

      // 1. Send all unique probes across all active network adapters
      for (ProbeSpecification spec : specs) {
        for (InetAddress broadcastAddr : broadcastAddresses) {
          try {
            DatagramPacket packet = new DatagramPacket(
                spec.payload(),
                spec.payload().length,
                broadcastAddr,
                spec.port());
            socket.send(packet);
          } catch (IOException e) {
            log.warn("Failed to send probe out to interface path: {}", broadcastAddr, e);
          }
        }
      }

      // 2. Listen loop for incoming network signatures
      byte[] receiveBuffer = new byte[1500];
      while (true) {
        try {
          DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
          socket.receive(receivePacket);

          String ip = receivePacket.getAddress().getHostAddress();
          int port = receivePacket.getPort();

          try {
            // Use strict OSC pointer decoding to parse the null-delimited token fields cleanly
            OscMessage oscMessage = decodeOscMessage(receivePacket.getData(), receivePacket.getLength());
            List<String> responseArgs = oscMessage.args().stream().map(Object::toString).toList();
            OnlineDevice device = new OnlineDevice(responseArgs, ip, port);
            log.info("Discovered active OSC device at {}:{} -> {}", ip, port, device.signature());
            discoveredDevices.add(device);

          } catch (Exception parseException) {
            log.debug("Skipped non-OSC or unparseable packet variant from {}: {}", ip, parseException.getMessage());
          }

        } catch (SocketTimeoutException e) {
          log.debug("OSC discovery scan window timeout reached, ending scan cycle.");
          break;
        }
      }

    } catch (IOException e) {
      log.error("Fatal network stack exception during device discovery tracking", e);
    }

    return discoveredDevices;
  }

  /**
   * Loops through every physical adapter interface on the machine, filtering out
   * internal loopbacks,
   * and isolates active IPv4 network subnets.
   */
  private List<InetAddress> getBroadcastAddresses() {
    Set<InetAddress> broadcastSet = new LinkedHashSet<>();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

      while (interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();

        // Skip loopbacks or turned-off links. Virtual flags are intentionally allowed
        // here
        // to retain compatibility with specific macOS USB dongle mapping drivers.
        if (networkInterface.isLoopback() || !networkInterface.isUp()) {
          continue;
        }

        for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
          InetAddress broadcast = interfaceAddress.getBroadcast();
          if (broadcast != null && interfaceAddress.getAddress() instanceof Inet4Address) {
            broadcastSet.add(broadcast);
          }
        }
      }
    } catch (SocketException e) {
      log.error("Failed to map system adapters for network interface routing setup", e);
    }

    // Always ensure global subnet visibility fallback is active for direct
    // point-to-point wires
    try {
      broadcastSet.add(InetAddress.getByName("255.255.255.255"));
    } catch (UnknownHostException ignored) {
    }

    return new ArrayList<>(broadcastSet);
  }

  /*
   * =========================================================================
   * Strict Internal OSC Parsing Mechanism
   * =========================================================================
   */

  private static record OscMessage(String address, List<Object> args) {
  }

  private static OscMessage decodeOscMessage(byte[] packet, int length) throws Exception {
    int offset = 0;

    // 1. Parse address string block
    StringAndOffset addrResult = decodeOscString(packet, offset, length);
    String address = addrResult.value();
    offset = addrResult.nextOffset();

    // 2. Parse type tag block configuration (Must begin with a comma sign)
    StringAndOffset tagsResult = decodeOscString(packet, offset, length);
    String typeTags = tagsResult.value();
    offset = tagsResult.nextOffset();

    if (!typeTags.startsWith(",")) {
      throw new IllegalArgumentException("Packet payload format does not present a valid OSC signature block.");
    }

    List<Object> args = new ArrayList<>();
    for (int i = 1; i < typeTags.length(); i++) {
      char tag = typeTags.charAt(i);
      if (tag == 's') {
        StringAndOffset strResult = decodeOscString(packet, offset, length);
        args.add(strResult.value());
        offset = strResult.nextOffset();
      } else if (tag == 'i') {
        args.add(ByteBuffer.wrap(packet, offset, 4).getInt());
        offset += 4;
      } else if (tag == 'f') {
        args.add(ByteBuffer.wrap(packet, offset, 4).getFloat());
        offset += 4;
      }
    }

    return new OscMessage(address, args);
  }

  private static StringAndOffset decodeOscString(byte[] packet, int offset, int maxLength) {
    int end = -1;
    for (int i = offset; i < maxLength; i++) {
      if (packet[i] == 0) {
        end = i;
        break;
      }
    }
    if (end < 0) {
      throw new IllegalStateException("OSC string mapping constraint missing final null terminator block.");
    }

    String value = new String(packet, offset, end - offset, StandardCharsets.UTF_8);
    int nextOffset = end + 1;
    while (nextOffset % 4 != 0) {
      nextOffset++;
    }
    return new StringAndOffset(value, nextOffset);
  }

  private static record StringAndOffset(String value, int nextOffset) {
  }
}